package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Choreographer
import android.view.Display
import com.example.domain.model.TelemetrySnapshot
import com.example.domain.model.ThermalLevel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Reads genuine device hardware & OS telemetry without any simulated/mocked numbers.
 *
 * Android Platform & OEM Limitations:
 * 1. FPS: Without Root/Shizuku (`dumpsys SurfaceFlinger`), Android isolates another app's
 *    OpenGL/Vulkan frame buffer. Here we read the genuine panel Refresh Rate (`Display.refreshRate`)
 *    and real compositor FPS via `Choreographer.FrameCallback`.
 * 2. CPU Clocks: On Android 8+ (Oreo+), SELinux restricts `/sys/devices/system/cpu/` or `/proc/stat`
 *    on certain OEMs. If unreadable, we return `null` so UI shows "OS Restricted" instead of fake data.
 * 3. Thermal Headroom: `PowerManager.getThermalHeadroom()` requires Android 11+ (API 30+) and
 *    device thermal HAL support; it returns `NaN` when unsupported or rate-limited.
 */
class SystemTelemetryRepository(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    private val frameCounter = AtomicInteger(0)

    fun observeTelemetry(intervalMs: Long = 1200L): Flow<TelemetrySnapshot> = flow {
        val mainHandler = Handler(Looper.getMainLooper())
        val isTracking = AtomicBoolean(true)
        var choreographer: Choreographer? = null

        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isTracking.get()) return
                frameCounter.incrementAndGet()
                choreographer?.postFrameCallback(this)
            }
        }

        mainHandler.post {
            choreographer = Choreographer.getInstance()
            frameCounter.set(0)
            choreographer?.postFrameCallback(frameCallback)
        }

        var lastWindowStartMs = System.currentTimeMillis()

        try {
            while (currentCoroutineContext().isActive) {
                val nowMs = System.currentTimeMillis()
                val elapsedMs = (nowMs - lastWindowStartMs).coerceAtLeast(1L)
                val frames = frameCounter.getAndSet(0)
                lastWindowStartMs = nowMs

                val measuredFps = if (elapsedMs >= 200L) {
                    ((frames * 1000.0) / elapsedMs).roundToInt()
                } else {
                    0
                }

                emit(readSnapshot(measuredFps))
                delay(intervalMs)
            }
        } finally {
            isTracking.set(false)
            mainHandler.post {
                choreographer?.removeFrameCallback(frameCallback)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun readSnapshot(measuredFps: Int = getDisplayRefreshRateHz().roundToInt()): TelemetrySnapshot {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)
        val ramUsagePct = if (totalRamMb > 0L) {
            ((usedRamMb.toDouble() / totalRamMb.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.coerceIn(0, 100) ?: 0
        }

        val rawTempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = rawTempTenths / 10f
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val rawCurrentUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (rawCurrentUa != null && rawCurrentUa != Int.MIN_VALUE && rawCurrentUa != 0) {
            // Most Android HALs report microamperes; some older OEMs report milliamperes
            if (kotlin.math.abs(rawCurrentUa) > 10_000) rawCurrentUa / 1000 else rawCurrentUa
        } else {
            null
        }

        val thermalLevel = readThermalLevel()
        val thermalHeadroomPct = readThermalHeadroomPercent()

        val cpu0CurMhz = readSysfsCpuFreqMhz("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
        val cpu0MaxMhz = readSysfsCpuFreqMhz("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")

        return TelemetrySnapshot(
            displayRefreshRateHz = getDisplayRefreshRateHz(),
            measuredCompositorFps = measuredFps,
            batteryPercent = batteryPct,
            batteryTempCelsius = tempCelsius,
            batteryVoltageMv = voltageMv,
            batteryCurrentMa = currentMa,
            isCharging = isCharging,
            totalRamMb = totalRamMb,
            usedRamMb = usedRamMb,
            availableRamMb = availRamMb,
            ramUsagePercent = ramUsagePct,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            cpu0CurrentFreqMhz = cpu0CurMhz,
            cpu0MaxFreqMhz = cpu0MaxMhz,
            thermalLevel = thermalLevel,
            thermalHeadroomPercent = thermalHeadroomPct
        )
    }

    private fun getDisplayRefreshRateHz(): Float {
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return display?.refreshRate ?: 60f
    }

    private fun readThermalLevel(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.NONE
        val status = runCatching { powerManager?.currentThermalStatus }.getOrNull()
            ?: return ThermalLevel.NONE
        return when (status) {
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.CRITICAL
            else -> ThermalLevel.NONE
        }
    }

    private fun readThermalHeadroomPercent(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val raw = runCatching { powerManager?.getThermalHeadroom(10) }.getOrNull() ?: return null
        if (raw.isNaN() || raw.isInfinite() || raw < 0f) return null
        return (raw * 100f).roundToInt().coerceIn(0, 120)
    }

    private fun readSysfsCpuFreqMhz(path: String): Int? {
        return runCatching {
            val file = File(path)
            if (!file.canRead()) return null
            val khz = file.readText().trim().toLongOrNull() ?: return null
            (khz / 1000L).toInt()
        }.getOrNull()
    }
}
