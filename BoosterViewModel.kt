package com.example.ui.viewmodel

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.BoostEngine
import com.example.data.PermissionManagerHelper
import com.example.data.PrivilegeDetector
import com.example.data.SettingsDataStore
import com.example.data.SystemTelemetryRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.BoostLevel
import com.example.domain.model.BoostResult
import com.example.domain.model.GameModeStatus
import com.example.domain.model.GameProfile
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.PermissionStatusSnapshot
import com.example.domain.model.PrivilegeStatus
import com.example.domain.model.TelemetrySnapshot
import com.example.service.ActiveSessionState
import com.example.service.ActiveSessionTracker
import com.example.service.GameMonitorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BoosterUiState(
    val isLoadingApps: Boolean = true,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val savedGames: List<GameProfile> = emptyList(),
    val selectedGamePackage: String? = null,
    val selectedGameModeStatus: GameModeStatus = GameModeStatus.UNSUPPORTED,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val isPrefsLoaded: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val shizukuModeEnabled: Boolean = false,
    val privilegeStatus: PrivilegeStatus = PrivilegeStatus(),
    val permissions: PermissionStatusSnapshot = PermissionStatusSnapshot(),
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
    val activeSession: ActiveSessionState = ActiveSessionState(),
    val isBoosting: Boolean = false,
    val boostChargeProgress: Float = 0f,
    val lastBoostResult: BoostResult? = null
) {
    val selectedGame: GameProfile?
        get() = savedGames.firstOrNull { it.packageName == selectedGamePackage }
            ?: savedGames.firstOrNull()

    fun iconFor(packageName: String) =
        installedApps.firstOrNull { it.packageName == packageName }?.icon
}

class BoosterViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val permissionHelper = PermissionManagerHelper(appContext)
    private val dataStore = SettingsDataStore(appContext)
    private val appRepository = AppRepository(appContext)
    private val telemetryRepository = SystemTelemetryRepository(appContext)
    private val privilegeDetector = PrivilegeDetector(appContext)
    private val boostEngine = BoostEngine(
        context = appContext,
        dataStore = dataStore,
        permissionHelper = permissionHelper,
        appRepository = appRepository,
        privilegeDetector = privilegeDetector
    )

    private val _uiState = MutableStateFlow(BoosterUiState())
    val uiState: StateFlow<BoosterUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionsAndPrivileges()
        loadInstalledApps()

        viewModelScope.launch {
            dataStore.stateFlow.collectLatest { prefs ->
                val selectedPkg = prefs.selectedGamePackage?.takeIf { pkg ->
                    prefs.savedGames.any { it.packageName == pkg }
                } ?: prefs.savedGames.firstOrNull()?.packageName

                val gameMode = appRepository.queryGameModeStatus(selectedPkg)
                _uiState.update {
                    it.copy(
                        savedGames = prefs.savedGames,
                        selectedGamePackage = selectedPkg,
                        selectedGameModeStatus = gameMode,
                        language = prefs.language,
                        isPrefsLoaded = true,
                        onboardingCompleted = prefs.onboardingCompleted,
                        shizukuModeEnabled = prefs.shizukuModeEnabled
                    )
                }
            }
        }

        viewModelScope.launch {
            telemetryRepository.observeTelemetry(1200L).collect { snap ->
                _uiState.update { it.copy(telemetry = snap) }
            }
        }

        viewModelScope.launch {
            ActiveSessionTracker.state.collect { session ->
                _uiState.update { it.copy(activeSession = session) }
            }
        }
    }

    fun refreshPermissionsAndPrivileges() {
        val perms = permissionHelper.checkAll()
        val privs = privilegeDetector.detect()
        _uiState.update { it.copy(permissions = perms, privilegeStatus = privs) }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = appRepository.getInstalledLaunchableApps()
            _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
        }
    }

    fun addGameToLibrary(app: InstalledAppInfo) {
        viewModelScope.launch {
            val profile = GameProfile(
                packageName = app.packageName,
                displayName = app.appName,
                boostLevel = BoostLevel.HIGH,
                keepScreenOn = true,
                lockBrightness = false,
                brightnessPercent = 85,
                autoDnd = permissionHelper.hasDndAccess(),
                enableOverlay = Settings.canDrawOverlays(appContext)
            )
            dataStore.addOrUpdateGame(profile)
            dataStore.setSelectedGame(app.packageName)
        }
    }

    fun activateAndLaunchGame(app: InstalledAppInfo) {
        viewModelScope.launch {
            val existing = _uiState.value.savedGames.firstOrNull { it.packageName == app.packageName }
            val profile = existing ?: GameProfile(
                packageName = app.packageName,
                displayName = app.appName,
                boostLevel = BoostLevel.HIGH,
                keepScreenOn = true,
                lockBrightness = false,
                brightnessPercent = 85,
                autoDnd = permissionHelper.hasDndAccess(),
                enableOverlay = Settings.canDrawOverlays(appContext)
            )
            if (existing == null) {
                dataStore.addOrUpdateGame(profile)
            }
            dataStore.setSelectedGame(profile.packageName)
            launchBoostForProfile(profile)
        }
    }

    fun activateSavedGame(packageName: String) {
        val profile = _uiState.value.savedGames.firstOrNull { it.packageName == packageName } ?: return
        viewModelScope.launch {
            dataStore.setSelectedGame(packageName)
            launchBoostForProfile(profile)
        }
    }

    fun removeGameFromLibrary(packageName: String) {
        viewModelScope.launch {
            dataStore.removeGame(packageName)
        }
    }

    fun selectGame(packageName: String) {
        viewModelScope.launch {
            dataStore.setSelectedGame(packageName)
        }
    }

    fun updateGameProfile(profile: GameProfile) {
        viewModelScope.launch {
            dataStore.addOrUpdateGame(profile)
        }
    }

    fun resetHeadshotAnchor(packageName: String) {
        viewModelScope.launch {
            dataStore.updateHeadshotAnchor(packageName, null, null, false)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            dataStore.setLanguage(language)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            dataStore.setOnboardingCompleted(completed)
        }
    }

    fun setShizukuModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setShizukuEnabled(enabled)
        }
    }

    fun startBoostAndLaunch() {
        val target = _uiState.value.selectedGame ?: return
        viewModelScope.launch {
            launchBoostForProfile(target)
        }
    }

    private suspend fun launchBoostForProfile(target: GameProfile) {
        val state = _uiState.value
        if (state.isBoosting) return

        _uiState.update { it.copy(isBoosting = true, boostChargeProgress = 0.05f) }
        val result = boostEngine.executeBoost(
            profile = target,
            shizukuBridgeEnabled = state.shizukuModeEnabled,
            onProgress = { progress ->
                _uiState.update { it.copy(boostChargeProgress = progress) }
            }
        )
        delay(280L)
        _uiState.update {
            it.copy(
                isBoosting = false,
                boostChargeProgress = 1f,
                lastBoostResult = result
            )
        }
    }

    fun relaunchSelectedGame() {
        val target = _uiState.value.selectedGame ?: return
        boostEngine.launchGame(target.packageName)
    }

    fun toggleFloatingMonitorOverlay() {
        val state = _uiState.value
        if (state.activeSession.isRunning) {
            GameMonitorService.stopSession(appContext)
        } else {
            if (!Settings.canDrawOverlays(appContext)) return
            val target = state.selectedGame
            GameMonitorService.startSession(
                context = appContext,
                packageName = target?.packageName,
                gameName = target?.displayName ?: "Live Telemetry",
                showOverlay = true,
                keepScreenOn = target?.keepScreenOn ?: false,
                dndActive = false
            )
        }
    }

    fun endActiveBoostSession() {
        GameMonitorService.stopSession(appContext)
    }
}
