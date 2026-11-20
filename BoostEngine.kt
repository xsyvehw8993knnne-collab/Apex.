package com.example.data

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process
import android.provider.Settings
import com.example.domain.model.BoostLevel
import com.example.domain.model.BoostResult
import com.example.domain.model.GameProfile
import com.example.service.GameMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Executes real, non-root official Android optimizations when boosting a game.
 *
 * Platform Behavior & Limits:
 * - Background cleanup uses `ActivityManager.killBackgroundProcesses()` on killable cached
 *   packages (never kills foreground services or critical system apps).
 * - ADPF `PerformanceHintManager` (Android 12+) creates a hint session for booster/telemetry
 *   threads; on Android 15+ (`API 35+`) we disable power-efficiency preference for low latency.
 * - DND (`NotificationManager.setInterruptionFilter`) and Brightness Lock (`Settings.System`)
 *   save the user's previous system values to DataStore and restore them automatically when ended.
 */
class BoostEngine(
    private val context: Context,
    private val dataStore: SettingsDataStore,
    private val permissionHelper: PermissionManagerHelper,
    private val appRepository: AppRepository,
    private val privilegeDetector: PrivilegeDetector
) {

    private var activeHintSession: PerformanceHintManager.Session? = null

    suspend fun executeBoost(
        profile: GameProfile,
        shizukuBridgeEnabled: Boolean,
        onProgress: (Float) -> Unit
    ): BoostResult = withContext(Dispatchers.IO) {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 1. Measure genuine available RAM before cleanup
        onProgress(0.15f)
        val memBefore = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val ramBeforeMb = memBefore.availMem / (1024L * 1024L)

        // 2. Trim killable background processes (filtered safely by boost level)
        onProgress(0.35f)
        val trimmedCount = trimBackgroundProcesses(
            activityManager = activityManager,
            targetGamePkg = profile.packageName,
            boostLevel = profile.boostLevel
        )
        delay(220L)

        // 3. Measure genuine available RAM after cleanup
        onProgress(0.55f)
        val memAfter = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val ramAfterMb = memAfter.availMem / (1024L * 1024L)
        val reclaimedMb = (ramAfterMb - ramBeforeMb).coerceAtLeast(0L)

        // 4. Activate ADPF PerformanceHintManager session (API 31+)
        onProgress(0.70f)
        val adpfActive = startOrUpdateAdpfSession(profile.boostLevel)

        // 5. Apply Do Not Disturb if enabled & granted
        onProgress(0.80f)
        val dndApplied = if (profile.autoDnd && permissionHelper.hasDndAccess()) {
            applyDndMode()
        } else {
            false
        }

        // 6. Apply Brightness Lock if enabled & WRITE_SETTINGS granted
        val brightnessApplied = if (profile.lockBrightness && Settings.System.canWrite(context)) {
            applyBrightnessLock(profile.brightnessPercent)
        } else {
            false
        }

        // 7. Optional Shizuku/Root Game Mode command ONLY if enabled & authorized
        onProgress(0.90f)
        val shizukuApplied = if (shizukuBridgeEnabled && profile.boostLevel == BoostLevel.EXTREME) {
            privilegeDetector.trySetGameModePerformanceViaRoot(profile.packageName)
        } else {
            false
        }

        val gameModeStatus = appRepository.queryGameModeStatus(profile.packageName)

        // 8. Start Foreground Telemetry Service & optional floating HUD
        onProgress(0.96f)
        GameMonitorService.startSession(
            context = context,
            packageName = profile.packageName,
            gameName = profile.displayName,
            showOverlay = profile.enableOverlay && Settings.canDrawOverlays(context),
            keepScreenOn = profile.keepScreenOn,
            dndActive = dndApplied
        )

        // 9. Launch target game
        onProgress(1.0f)
        val launched = launchGame(profile.packageName)

        BoostResult(
            targetPackage = profile.packageName,
            targetName = profile.displayName,
            ramBeforeMb = ramBeforeMb,
            ramAfterMb = ramAfterMb,
            ramReclaimedMb = reclaimedMb,
            processesTrimmedCount = trimmedCount,
            adpfSessionActive = adpfActive,
            dndApplied = dndApplied,
            brightnessApplied = brightnessApplied,
            gameModeStatus = gameModeStatus,
            shizukuCommandApplied = shizukuApplied,
            gameLaunched = launched
        )
    }

    suspend fun restoreSystemSettings() = withContext(Dispatchers.IO) {
        val restorable = dataStore.consumeRestorableSystemState()

        if (restorable.dndModified && restorable.previousDndFilter >= 0 && permissionHelper.hasDndAccess()) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            runCatching { nm?.setInterruptionFilter(restorable.previousDndFilter) }
        }

        if (restorable.brightnessModified && Settings.System.canWrite(context)) {
            runCatching {
                val resolver = context.contentResolver
                if (restorable.previousBrightnessValue in 1..255) {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        restorable.previousBrightnessValue
                    )
                }
                if (restorable.previousBrightnessMode >= 0) {
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        restorable.previousBrightnessMode
                    )
                }
            }
        }

        closeAdpfSession()
    }

    fun launchGame(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?: return false
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun trimBackgroundProcesses(
        activityManager: ActivityManager,
        targetGamePkg: String,
        boostLevel: BoostLevel
    ): Int {
        val pm = context.packageManager
        val defaultLauncherPkg = resolveDefaultLauncherPackage(pm)
        val candidates = LinkedHashSet<String>()

        // Prefer recently active apps when Usage Access is granted
        if (permissionHelper.hasUsageStatsPermission()) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 6L * 60L * 60L * 1000L,
                now
            )
            stats?.sortedByDescending { it.lastTimeUsed }?.forEach { usage ->
                if (usage.totalTimeInForeground > 0L) {
                    candidates.add(usage.packageName)
                }
            }
        }

        // Also include launchable non-system user apps on HIGH / EXTREME levels
        if (boostLevel != BoostLevel.BALANCED) {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
            for (info in resolved) {
                val appInfo = info.activityInfo?.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!isSystem || boostLevel == BoostLevel.EXTREME) {
                    candidates.add(appInfo.packageName)
                }
            }
        }

        val protectedPackages = setOfNotNull(
            context.packageName,
            targetGamePkg,
            defaultLauncherPkg,
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.inputmethod.latin",
            "moe.shizuku.privileged.api"
        )

        var trimmed = 0
        val maxTargets = when (boostLevel) {
            BoostLevel.BALANCED -> 12
            BoostLevel.HIGH -> 35
            BoostLevel.EXTREME -> 80
        }

        for (pkg in candidates) {
            if (trimmed >= maxTargets) break
            if (pkg in protectedPackages) continue
            runCatching {
                activityManager.killBackgroundProcesses(pkg)
                trimmed++
            }
        }
        return trimmed
    }

    private fun resolveDefaultLauncherPackage(pm: PackageManager): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(homeIntent, 0)
        }
        return res?.activityInfo?.packageName
    }

    private suspend fun startOrUpdateAdpfSession(boostLevel: BoostLevel): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@withContext false
            val phm = context.getSystemService(PerformanceHintManager::class.java)
                ?: return@withContext false

            val targetDurationNanos = when (boostLevel) {
                BoostLevel.BALANCED -> 16_666_666L // ~60 FPS target
                BoostLevel.HIGH -> 11_111_111L     // ~90 FPS target
                BoostLevel.EXTREME -> 8_333_333L   // ~120 FPS target
            }

            runCatching {
                activeHintSession?.close()
                // Bind hint session to the persistent Main thread rather than a transient IO worker thread
                val session = phm.createHintSession(intArrayOf(Process.myTid()), targetDurationNanos)
                if (session != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        session.setPreferPowerEfficiency(boostLevel == BoostLevel.BALANCED)
                    }
                    session.reportActualWorkDuration(targetDurationNanos)
                    activeHintSession = session
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        }

    private fun closeAdpfSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { activeHintSession?.close() }
            activeHintSession = null
        }
    }

    private suspend fun applyDndMode(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return runCatching {
            val previousFilter = nm.currentInterruptionFilter
            dataStore.saveRestorableDnd(previousFilter)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        }.getOrDefault(false)
    }

    private suspend fun applyBrightnessLock(percent: Int): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val prevMode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            )
            val prevValue = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            dataStore.saveRestorableBrightness(prevMode, prevValue)

            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val mappedBrightness = ((percent.coerceIn(10, 100) / 100f) * 255f).roundToInt().coerceIn(15, 255)
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                mappedBrightness
            )
            true
        }.getOrDefault(false)
    }
}
