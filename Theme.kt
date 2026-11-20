package com.example.ui.theme

import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CyberExtendedColors(
    val obsidian: Color = ObsidianBlack,
    val surfaceElevated: Color = CyberSurfaceElevated,
    val stroke: Color = CyberSurfaceStroke,
    val crimson: Color = NeonCrimson,
    val crimsonGlow: Color = CrimsonGlow,
    val cyan: Color = ElectricCyan,
    val violet: Color = CyberViolet,
    val emerald: Color = EmeraldTelemetry,
    val amber: Color = AmberThermal,
    val textMuted: Color = TextMuted
)

val LocalCyberColors = staticCompositionLocalOf { CyberExtendedColors() }
val LocalAppResources = staticCompositionLocalOf<Resources> {
    error("LocalAppResources not provided")
}

private val ApexDarkColorScheme = darkColorScheme(
    primary = NeonCrimson,
    onPrimary = Color.White,
    primaryContainer = CrimsonDeep,
    onPrimaryContainer = Color.White,
    secondary = ElectricCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0xFF003B47),
    onSecondaryContainer = ElectricCyan,
    tertiary = CyberViolet,
    onTertiary = Color.White,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CyberSurfaceStroke,
    outlineVariant = Color(0xFF20202E),
    error = NeonCrimson,
    onError = Color.White
)

@Composable
fun ApexBoostTheme(
    isArabic: Boolean = false,
    content: @Composable () -> Unit
) {
    val cyberColors = CyberExtendedColors()
    val typography = buildCyberTypography(isArabic = isArabic)

    CompositionLocalProvider(LocalCyberColors provides cyberColors) {
        MaterialTheme(
            colorScheme = ApexDarkColorScheme,
            typography = typography,
            shapes = CyberShapes,
            content = content
        )
    }
}
