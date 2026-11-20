package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import com.example.domain.model.PrivilegeStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects optional elevated execution environments (Shizuku / Root su).
 *
 * Android Platform Constraint:
 * - Standard non-root apps cannot call `GameManager.setGameMode()` for other packages
 *   (requires system signature permission `MANAGE_GAME_MODE`) or run `am force-stop`.
 * - If the user explicitly has Root (`su`) or Shizuku installed, this detector surfaces
 *   their presence transparently. Without them, Apex Boost strictly uses official public APIs.
 */
class PrivilegeDetector(private val context: Context) {

    fun detect(): PrivilegeStatus {
        val shizukuInstalled = isPackageInstalled("moe.shizuku.privileged.api")
        val suExists = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su"
        ).any { path -> runCatching { File(path).exists() }.getOrDefault(false) }

        return PrivilegeStatus(
            shizukuInstalled = shizukuInstalled,
            rootSuAvailable = suExists
        )
    }

    /**
     * Attempts to apply Android 12+ `cmd game mode performance <packageName>` ONLY when
     * the user enabled the optional privilege bridge AND `su` is actually authorized on device.
     * Note: Full Shizuku Binder IPC requires the user to grant Shizuku permission inside the
     * Shizuku app; if `su` is unavailable, this safely returns false without faking success.
     */
    suspend fun trySetGameModePerformanceViaRoot(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!detect().rootSuAvailable) return@withContext false
            // Validate strict package name format before passing to shell
            val safePackage = packageName.takeIf {
                it.matches(Regex("^[a-zA-Z0-9_.]+$"))
            } ?: return@withContext false

            return@withContext runCatching {
                val process = ProcessBuilder("su", "-c", "cmd game mode performance $safePackage")
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                exitCode == 0
            }.getOrDefault(false)
        }

    private fun isPackageInstalled(pkg: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            true
        }.getOrElse {
            @Suppress("DEPRECATION")
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }
}
