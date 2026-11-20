package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.GameProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.apexDataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_boost_prefs")

data class StoredPreferencesState(
    val savedGames: List<GameProfile> = emptyList(),
    val selectedGamePackage: String? = null,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val shizukuModeEnabled: Boolean = false
)

data class RestorableSystemState(
    val dndModified: Boolean = false,
    val previousDndFilter: Int = -1,
    val brightnessModified: Boolean = false,
    val previousBrightnessMode: Int = -1,
    val previousBrightnessValue: Int = -1
)

class SettingsDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val SAVED_GAMES_JSON = stringPreferencesKey("saved_games_json")
        val SELECTED_GAME_PKG = stringPreferencesKey("selected_game_pkg")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SHIZUKU_ENABLED = booleanPreferencesKey("shizuku_enabled")

        val DND_MODIFIED = booleanPreferencesKey("dnd_modified")
        val PREV_DND_FILTER = intPreferencesKey("prev_dnd_filter")
        val BRIGHTNESS_MODIFIED = booleanPreferencesKey("brightness_modified")
        val PREV_BRIGHTNESS_MODE = intPreferencesKey("prev_brightness_mode")
        val PREV_BRIGHTNESS_VALUE = intPreferencesKey("prev_brightness_value")
    }

    val stateFlow: Flow<StoredPreferencesState> = context.apexDataStore.data.map { prefs ->
        val gamesJson = prefs[Keys.SAVED_GAMES_JSON]
        val games = if (gamesJson.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<GameProfile>>(gamesJson) }
                .getOrDefault(emptyList())
        }

        val langName = prefs[Keys.APP_LANGUAGE]
        val language = AppLanguage.entries.firstOrNull { it.name == langName } ?: AppLanguage.SYSTEM

        StoredPreferencesState(
            savedGames = games,
            selectedGamePackage = prefs[Keys.SELECTED_GAME_PKG],
            language = language,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            shizukuModeEnabled = prefs[Keys.SHIZUKU_ENABLED] ?: false
        )
    }

    suspend fun addOrUpdateGame(profile: GameProfile) {
        context.apexDataStore.edit { prefs ->
            val current = decodeGames(prefs[Keys.SAVED_GAMES_JSON]).toMutableList()
            val idx = current.indexOfFirst { it.packageName == profile.packageName }
            if (idx >= 0) {
                current[idx] = profile
            } else {
                current.add(profile)
            }
            prefs[Keys.SAVED_GAMES_JSON] = json.encodeToString(current)
            if (prefs[Keys.SELECTED_GAME_PKG].isNullOrBlank()) {
                prefs[Keys.SELECTED_GAME_PKG] = profile.packageName
            }
        }
    }

    suspend fun removeGame(packageName: String) {
        context.apexDataStore.edit { prefs ->
            val updated = decodeGames(prefs[Keys.SAVED_GAMES_JSON])
                .filterNot { it.packageName == packageName }
            prefs[Keys.SAVED_GAMES_JSON] = json.encodeToString(updated)
            if (prefs[Keys.SELECTED_GAME_PKG] == packageName) {
                prefs[Keys.SELECTED_GAME_PKG] = updated.firstOrNull()?.packageName ?: ""
            }
        }
    }

    suspend fun setSelectedGame(packageName: String) {
        context.apexDataStore.edit { prefs ->
            prefs[Keys.SELECTED_GAME_PKG] = packageName
        }
    }

    suspend fun updateHeadshotAnchor(
        packageName: String,
        anchorX: Float?,
        anchorY: Float?,
        locked: Boolean
    ) {
        context.apexDataStore.edit { prefs ->
            val current = decodeGames(prefs[Keys.SAVED_GAMES_JSON]).toMutableList()
            val idx = current.indexOfFirst { it.packageName == packageName }
            if (idx >= 0) {
                current[idx] = current[idx].copy(
                    headshotAnchorX = anchorX,
                    headshotAnchorY = anchorY,
                    headshotAnchorLocked = locked
                )
                prefs[Keys.SAVED_GAMES_JSON] = json.encodeToString(current)
            }
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.apexDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.name
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.apexDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setShizukuEnabled(enabled: Boolean) {
        context.apexDataStore.edit { prefs ->
            prefs[Keys.SHIZUKU_ENABLED] = enabled
        }
    }

    suspend fun saveRestorableDnd(previousFilter: Int) {
        context.apexDataStore.edit { prefs ->
            if (prefs[Keys.DND_MODIFIED] != true) {
                prefs[Keys.DND_MODIFIED] = true
                prefs[Keys.PREV_DND_FILTER] = previousFilter
            }
        }
    }

    suspend fun saveRestorableBrightness(mode: Int, value: Int) {
        context.apexDataStore.edit { prefs ->
            if (prefs[Keys.BRIGHTNESS_MODIFIED] != true) {
                prefs[Keys.BRIGHTNESS_MODIFIED] = true
                prefs[Keys.PREV_BRIGHTNESS_MODE] = mode
                prefs[Keys.PREV_BRIGHTNESS_VALUE] = value
            }
        }
    }

    suspend fun consumeRestorableSystemState(): RestorableSystemState {
        val snapshot = context.apexDataStore.data.first()
        val state = RestorableSystemState(
            dndModified = snapshot[Keys.DND_MODIFIED] ?: false,
            previousDndFilter = snapshot[Keys.PREV_DND_FILTER] ?: -1,
            brightnessModified = snapshot[Keys.BRIGHTNESS_MODIFIED] ?: false,
            previousBrightnessMode = snapshot[Keys.PREV_BRIGHTNESS_MODE] ?: -1,
            previousBrightnessValue = snapshot[Keys.PREV_BRIGHTNESS_VALUE] ?: -1
        )
        context.apexDataStore.edit { prefs ->
            prefs[Keys.DND_MODIFIED] = false
            prefs[Keys.PREV_DND_FILTER] = -1
            prefs[Keys.BRIGHTNESS_MODIFIED] = false
            prefs[Keys.PREV_BRIGHTNESS_MODE] = -1
            prefs[Keys.PREV_BRIGHTNESS_VALUE] = -1
        }
        return state
    }

    private fun decodeGames(raw: String?): List<GameProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<GameProfile>>(raw) }.getOrDefault(emptyList())
    }
}
