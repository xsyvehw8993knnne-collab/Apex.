package com.example.data

import android.app.GameManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.domain.model.GameModeStatus
import com.example.domain.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers installed launchable apps & games using PackageManager with `<queries>`
 * (`ACTION_MAIN` + `CATEGORY_LAUNCHER`), avoiding broad `QUERY_ALL_PACKAGES` permission.
 */
class AppRepository(private val context: Context) {

    suspend fun getInstalledLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val seenPackages = HashSet<String>()
        val results = ArrayList<InstalledAppInfo>()

        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == context.packageName || !seenPackages.add(pkg)) continue

            val appInfo = activityInfo.applicationInfo
            val isGame = isDeclaredAsGame(appInfo)
            val label = runCatching {
                resolveInfo.loadLabel(pm).toString().ifBlank { pkg }
            }.getOrDefault(pkg)
            val icon = runCatching {
                resolveInfo.loadIcon(pm)
            }.getOrNull()

            results.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon,
                    isDeclaredGame = isGame
                )
            )
        }

        results.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isDeclaredGame }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Queries Android 12+ (API 31+) `GameManager` API for the active Game Mode.
     *
     * Platform Restriction:
     * - `GameManager.getGameMode()` returns the mode for the calling app process.
     * - `GameManager.getGameMode(packageName)` & `setGameMode(packageName, mode)` are `@SystemApi`
     *   methods guarded by `MANAGE_GAME_MODE` (signature|privileged), unavailable to third-party apps.
     */
    fun queryGameModeStatus(@Suppress("UNUSED_PARAMETER") targetPackage: String? = null): GameModeStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return GameModeStatus.UNSUPPORTED
        }
        val gm = context.getSystemService(GameManager::class.java) ?: return GameModeStatus.UNSUPPORTED
        val rawMode = runCatching { gm.gameMode }.getOrDefault(GameManager.GAME_MODE_UNSUPPORTED)

        return when (rawMode) {
            GameManager.GAME_MODE_PERFORMANCE -> GameModeStatus.PERFORMANCE
            GameManager.GAME_MODE_BATTERY -> GameModeStatus.BATTERY
            GameManager.GAME_MODE_STANDARD -> GameModeStatus.STANDARD
            else -> GameModeStatus.UNSUPPORTED
        }
    }

    @Suppress("DEPRECATION")
    private fun isDeclaredAsGame(appInfo: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            appInfo.category == ApplicationInfo.CATEGORY_GAME
        ) {
            return true
        }
        return (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }
}
