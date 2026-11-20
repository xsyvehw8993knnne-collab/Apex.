package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ThermalLevel
import com.example.ui.components.ArcTelemetryGauge
import com.example.ui.components.CyberChamferedCard
import com.example.ui.components.str
import com.example.ui.theme.CyberNumericStyle
import com.example.ui.theme.LocalCyberColors
import com.example.ui.viewmodel.BoosterUiState
import java.util.Locale

@Composable
fun MonitorScreen(
    uiState: BoosterUiState,
    onToggleFloatingOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val haptic = LocalHapticFeedback.current
    val t = uiState.telemetry
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = str(R.string.monitor_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            // Floating HUD Control Card
            CyberChamferedCard(
                isHighlighted = uiState.activeSession.overlayVisible,
                accentColor = cyber.cyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = str(R.string.monitor_overlay_card_title),
                            tint = cyber.cyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = str(R.string.monitor_overlay_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = str(
                                    if (uiState.activeSession.isRunning) R.string.monitor_overlay_active
                                    else R.string.monitor_overlay_inactive
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.activeSession.isRunning) cyber.emerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (uiState.permissions.overlayGranted) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleFloatingOverlay()
                            },
                            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.activeSession.isRunning) cyber.crimson else cyber.cyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("monitor_overlay_toggle_button")
                        ) {
                            Text(
                                text = str(
                                    if (uiState.activeSession.isRunning) R.string.monitor_toggle_stop
                                    else R.string.monitor_toggle_start
                                ),
                                color = if (uiState.activeSession.isRunning) Color.White else Color.Black
                            )
                        }
                    } else {
                        Button(
                            onClick = onRequestOverlayPermission,
                            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cyber.amber),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("monitor_grant_overlay_button")
                        ) {
                            Text(
                                text = str(R.string.monitor_grant_overlay),
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            // 2x2 Large Glowing Arc Gauges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val hz = t.displayRefreshRateHz.toInt().coerceAtLeast(30)
                val fpsShown = if (t.measuredCompositorFps > 0) t.measuredCompositorFps else hz
                ArcTelemetryGauge(
                    label = str(R.string.metric_fps),
                    valueText = if (t.measuredCompositorFps > 0) "${t.measuredCompositorFps}" else "$hz",
                    subText = "${hz}HZ",
                    progress = (fpsShown.toFloat() / hz.toFloat()),
                    accentColor = cyber.cyan,
                    size = 136.dp
                )
                ArcTelemetryGauge(
                    label = str(R.string.metric_temp),
                    valueText = String.format(Locale.US, "%.1f°C", t.batteryTempCelsius),
                    subText = thermalLabel(t.thermalLevel),
                    progress = ((t.batteryTempCelsius - 20f) / 35f).coerceIn(0.05f, 1f),
                    accentColor = if (t.batteryTempCelsius >= 41f) cyber.crimson else cyber.amber,
                    size = 136.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ArcTelemetryGauge(
                    label = str(R.string.metric_ram),
                    valueText = "${t.ramUsagePercent}%",
                    subText = "${t.usedRamMb}/${t.totalRamMb}M",
                    progress = t.ramUsagePercent / 100f,
                    accentColor = cyber.crimson,
                    size = 136.dp
                )
                val headroomVal = t.thermalHeadroomPercent
                ArcTelemetryGauge(
                    label = str(R.string.metric_cpu),
                    valueText = if (headroomVal != null) "$headroomVal%" else "${t.batteryPercent}%",
                    subText = if (headroomVal != null) {
                        str(R.string.metric_thermal_headroom)
                    } else {
                        str(R.string.metric_battery)
                    },
                    progress = if (headroomVal != null) {
                        (headroomVal / 100f).coerceIn(0.05f, 1f)
                    } else {
                        (t.batteryPercent / 100f).coerceIn(0.05f, 1f)
                    },
                    accentColor = cyber.violet,
                    size = 136.dp
                )
            }

            // Detailed Real Hardware Matrix
            CyberChamferedCard(
                isHighlighted = false,
                accentColor = cyber.stroke,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = str(R.string.metric_cores),
                            tint = cyber.crimsonGlow
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = str(R.string.metric_cpu),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    TelemetryRow(
                        label = str(R.string.metric_cores),
                        value = "${t.cpuCoreCount}"
                    )
                    TelemetryRow(
                        label = str(R.string.metric_cpu_freq),
                        value = t.cpu0CurrentFreqMhz?.let { cur ->
                            val max = t.cpu0MaxFreqMhz
                            if (max != null) "$cur / $max MHz" else "$cur MHz"
                        } ?: str(R.string.metric_unavailable)
                    )
                    TelemetryRow(
                        label = str(R.string.metric_refresh_rate),
                        value = String.format(Locale.US, "%.0f Hz", t.displayRefreshRateHz)
                    )
                    TelemetryRow(
                        label = str(R.string.monitor_thermal_status),
                        value = thermalLabel(t.thermalLevel)
                    )
                    TelemetryRow(
                        label = str(R.string.metric_battery),
                        value = "${t.batteryPercent}% (${
                            str(if (t.isCharging) R.string.metric_charging else R.string.metric_discharging)
                        })"
                    )
                    TelemetryRow(
                        label = str(R.string.metric_voltage),
                        value = "${t.batteryVoltageMv} mV"
                    )
                    TelemetryRow(
                        label = str(R.string.metric_current),
                        value = t.batteryCurrentMa?.let { "$it mA" } ?: str(R.string.metric_unavailable)
                    )
                }
            }

            // Hardware Transparency Notice
            CyberChamferedCard(
                isHighlighted = false,
                accentColor = cyber.cyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = str(R.string.monitor_honesty_title),
                        tint = cyber.cyan
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = str(R.string.monitor_honesty_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = cyber.cyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = str(R.string.monitor_honesty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = CyberNumericStyle.copy(fontSize = 13.sp, color = Color.White)
        )
    }
}

@Composable
private fun thermalLabel(level: ThermalLevel): String = when (level) {
    ThermalLevel.NONE -> str(R.string.thermal_none)
    ThermalLevel.LIGHT -> str(R.string.thermal_light)
    ThermalLevel.MODERATE -> str(R.string.thermal_moderate)
    ThermalLevel.SEVERE -> str(R.string.thermal_severe)
    ThermalLevel.CRITICAL -> str(R.string.thermal_critical)
}
