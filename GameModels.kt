package com.example.domain.model

import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable

@Serializable
enum class BoostLevel {
    BALANCED,
    HIGH,
    EXTREME
}

@Serializable
enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    ARABIC
}

@Serializable
data class GameProfile(
    val packageName: String,
    val displayName: String,
    val boostLevel: BoostLevel = BoostLevel.HIGH,
    val keepScreenOn: Boolean = true,
    val lockBrightness: Boolean = false,
    val brightnessPercent: Int = 85,
    val autoDnd: Boolean = true,
    val enableOverlay: Boolean = true,
    val headshotAnchorX: Float? = null,
    val headshotAnchorY: Float? = null,
    val headshotAnchorLocked: Boolean = false
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isDeclaredGame: Boolean
)

enum class ThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL
}

enum class GameModeStatus {
    STANDARD,
    PERFORMANCE,
    BATTERY,
    UNSUPPORTED
}

data class TelemetrySnapshot(
    val displayRefreshRateHz: Float = 60f,
    val measuredCompositorFps: Int = 0,
    val batteryPercent: Int = 0,
    val batteryTempCelsius: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val batteryCurrentMa: Int? = null,
    val isCharging: Boolean = false,
    val totalRamMb: Long = 0L,
    val usedRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val ramUsagePercent: Int = 0,
    val cpuCoreCount: Int = 1,
    val cpu0CurrentFreqMhz: Int? = null,
    val cpu0MaxFreqMhz: Int? = null,
    val thermalLevel: ThermalLevel = ThermalLevel.NONE,
    val thermalHeadroomPercent: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class BoostResult(
    val targetPackage: String,
    val targetName: String,
    val ramBeforeMb: Long,
    val ramAfterMb: Long,
    val ramReclaimedMb: Long,
    val processesTrimmedCount: Int,
    val adpfSessionActive: Boolean,
    val dndApplied: Boolean,
    val brightnessApplied: Boolean,
    val gameModeStatus: GameModeStatus,
    val shizukuCommandApplied: Boolean,
    val gameLaunched: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class PermissionStatusSnapshot(
    val postNotificationsGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val dndAccessGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val writeSettingsGranted: Boolean = false
) {
    val coreGrantedCount: Int
        get() = listOf(
            postNotificationsGranted,
            usageAccessGranted,
            overlayGranted,
            dndAccessGranted,
            batteryOptimizationIgnored
        ).count { it }
}

data class PrivilegeStatus(
    val shizukuInstalled: Boolean = false,
    val rootSuAvailable: Boolean = false
)
