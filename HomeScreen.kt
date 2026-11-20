package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.BoostLevel
import com.example.domain.model.GameModeStatus
import com.example.domain.model.GameProfile
import com.example.ui.components.AppIconImage
import com.example.ui.components.ArcTelemetryGauge
import com.example.ui.components.CyberChamferedCard
import com.example.ui.components.RgbBoostCoreButton
import com.example.ui.components.str
import com.example.ui.theme.CyberNumericStyle
import com.example.ui.theme.LocalCyberColors
import com.example.ui.viewmodel.BoosterUiState
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: BoosterUiState,
    onSelectGame: (String) -> Unit,
    onStartBoost: () -> Unit,
    onRelaunchGame: () -> Unit,
    onEndSession: () -> Unit,
    onOpenLibrary: () -> Unit,
    onConfigureGame: (String) -> Unit,
    onOpenWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val haptic = LocalHapticFeedback.current
    val selectedGame = uiState.selectedGame
    val telemetry = uiState.telemetry
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
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = str(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = str(R.string.app_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = cyber.crimsonGlow
                    )
                }

                val permsOk = uiState.permissions.coreGrantedCount
                CyberChamferedCard(
                    isHighlighted = permsOk < 5,
                    accentColor = if (permsOk == 5) cyber.emerald else cyber.amber,
                    cutSize = 10.dp,
                    onClick = onOpenWizard,
                    modifier = Modifier.testTag("home_permissions_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = str(R.string.settings_permissions_section),
                            tint = if (permsOk == 5) cyber.emerald else cyber.amber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$permsOk/5",
                            style = CyberNumericStyle.copy(
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            // Live Compact Arc Gauges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val hz = telemetry.displayRefreshRateHz.toInt().coerceAtLeast(30)
                val fpsShown = if (telemetry.measuredCompositorFps > 0) telemetry.measuredCompositorFps else hz
                ArcTelemetryGauge(
                    label = str(R.string.metric_fps),
                    valueText = if (telemetry.measuredCompositorFps > 0) "${telemetry.measuredCompositorFps}" else "$hz",
                    subText = "${hz}HZ",
                    progress = (fpsShown.toFloat() / hz.toFloat()),
                    accentColor = cyber.cyan,
                    size = 104.dp
                )
                ArcTelemetryGauge(
                    label = str(R.string.metric_temp),
                    valueText = String.format(Locale.US, "%.1f°", telemetry.batteryTempCelsius),
                    subText = "CELSIUS",
                    progress = ((telemetry.batteryTempCelsius - 20f) / 35f).coerceIn(0.05f, 1f),
                    accentColor = if (telemetry.batteryTempCelsius >= 41f) cyber.crimson else cyber.amber,
                    size = 104.dp
                )
                ArcTelemetryGauge(
                    label = str(R.string.metric_ram),
                    valueText = "${telemetry.ramUsagePercent}%",
                    subText = "${telemetry.availableRamMb}M FREE",
                    progress = telemetry.ramUsagePercent / 100f,
                    accentColor = cyber.crimson,
                    size = 104.dp
                )
            }

            // Game Library Carousel or Empty Callout
            if (uiState.savedGames.isEmpty()) {
                CyberChamferedCard(
                    isHighlighted = true,
                    accentColor = cyber.crimson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = str(R.string.nav_library),
                            tint = cyber.crimsonGlow,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = str(R.string.home_select_game_prompt),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenLibrary()
                            },
                            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cyber.crimson),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_open_library_button")
                        ) {
                            Text(text = str(R.string.home_go_to_library))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = str(R.string.home_switch_game),
                        style = MaterialTheme.typography.labelLarge,
                        color = cyber.cyan
                    )
                    if (selectedGame != null) {
                        OutlinedButton(
                            onClick = { onConfigureGame(selectedGame.packageName) },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("home_tune_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = str(R.string.home_configure_game),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = str(R.string.home_configure_game),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(uiState.savedGames, key = { it.packageName }) { game ->
                        GameSelectorCard(
                            game = game,
                            icon = uiState.iconFor(game.packageName),
                            selected = game.packageName == selectedGame?.packageName,
                            onSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectGame(game.packageName)
                            },
                            onConfigure = { onConfigureGame(game.packageName) }
                        )
                    }
                }
            }

            // Massive Central RGB Boost Core Button
            val coreTitle = when {
                uiState.isBoosting -> str(R.string.home_boosting_now)
                uiState.activeSession.isRunning -> str(R.string.home_session_active)
                else -> str(R.string.home_start_boost)
            }
            val coreSub = when {
                selectedGame != null -> selectedGame.displayName
                else -> ""
            }

            RgbBoostCoreButton(
                title = coreTitle,
                subtitle = coreSub,
                enabled = selectedGame != null,
                isBoosting = uiState.isBoosting,
                isSessionActive = uiState.activeSession.isRunning,
                chargeProgress = uiState.boostChargeProgress,
                onClick = {
                    if (uiState.activeSession.isRunning) {
                        onRelaunchGame()
                    } else {
                        onStartBoost()
                    }
                },
                modifier = Modifier.testTag("home_boost_core_button")
            )

            // Active Session Quick Action Bar
            AnimatedVisibility(
                visible = uiState.activeSession.isRunning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRelaunchGame()
                        },
                        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cyber.cyan),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("home_relaunch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = str(R.string.home_launch_again),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = str(R.string.home_launch_again),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEndSession()
                        },
                        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("home_end_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = str(R.string.home_end_session),
                            tint = cyber.crimsonGlow
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = str(R.string.home_end_session),
                            color = cyber.crimsonGlow
                        )
                    }
                }
            }

            // Selected Game Active Feature Chips
            if (selectedGame != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureStatusPill(
                        icon = Icons.Default.Bolt,
                        label = boostLevelLabel(selectedGame.boostLevel),
                        active = true,
                        activeColor = cyber.crimson,
                        modifier = Modifier.weight(1f)
                    )
                    FeatureStatusPill(
                        icon = Icons.Default.Layers,
                        label = str(R.string.home_quick_overlay),
                        active = selectedGame.enableOverlay && uiState.permissions.overlayGranted,
                        activeColor = cyber.cyan,
                        modifier = Modifier.weight(1f)
                    )
                    FeatureStatusPill(
                        icon = Icons.Default.DoNotDisturbOn,
                        label = str(R.string.home_quick_dnd),
                        active = selectedGame.autoDnd && uiState.permissions.dndAccessGranted,
                        activeColor = cyber.violet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Genuine Last Boost Telemetry Report
            uiState.lastBoostResult?.let { report ->
                CyberChamferedCard(
                    isHighlighted = true,
                    accentColor = cyber.cyan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = str(R.string.home_last_boost_report),
                            style = MaterialTheme.typography.labelLarge,
                            color = cyber.cyan
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ReportStatItem(
                                label = str(R.string.home_ram_reclaimed),
                                value = "+${report.ramReclaimedMb} MB"
                            )
                            ReportStatItem(
                                label = str(R.string.home_processes_trimmed),
                                value = "${report.processesTrimmedCount}"
                            )
                            ReportStatItem(
                                label = str(R.string.home_adpf_hint),
                                value = if (report.adpfSessionActive) "ACTIVE" else "N/A"
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ReportStatItem(
                                label = str(R.string.home_dnd_applied),
                                value = if (report.dndApplied) "ON" else "OFF"
                            )
                            ReportStatItem(
                                label = str(R.string.home_brightness_applied),
                                value = if (report.brightnessApplied) "LOCKED" else "OFF"
                            )
                            ReportStatItem(
                                label = str(R.string.home_game_mode_state),
                                value = gameModeLabel(report.gameModeStatus)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GameSelectorCard(
    game: GameProfile,
    icon: android.graphics.drawable.Drawable?,
    selected: Boolean,
    onSelect: () -> Unit,
    onConfigure: () -> Unit
) {
    val cyber = LocalCyberColors.current
    CyberChamferedCard(
        isHighlighted = selected,
        accentColor = cyber.crimson,
        cutSize = 14.dp,
        onClick = onSelect,
        modifier = Modifier
            .width(196.dp)
            .testTag("game_card_${game.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                drawable = icon,
                contentDescription = game.displayName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CutCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = boostLevelLabel(game.boostLevel),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) cyber.crimsonGlow else cyber.textMuted,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onConfigure,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tune_game_${game.packageName}")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = str(R.string.library_configure),
                    tint = cyber.cyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureStatusPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (active) activeColor.copy(alpha = 0.14f) else Color(0xFF13131D),
                shape
            )
            .border(
                width = 1.dp,
                color = if (active) activeColor.copy(alpha = 0.65f) else cyber.stroke,
                shape = shape
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) activeColor else cyber.textMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (active) Color.White else cyber.textMuted
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReportStatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = CyberNumericStyle.copy(fontSize = 14.sp, color = Color.White)
        )
    }
}

@Composable
fun boostLevelLabel(level: BoostLevel): String = when (level) {
    BoostLevel.BALANCED -> str(R.string.config_level_balanced)
    BoostLevel.HIGH -> str(R.string.config_level_high)
    BoostLevel.EXTREME -> str(R.string.config_level_extreme)
}

@Composable
fun gameModeLabel(mode: GameModeStatus): String = when (mode) {
    GameModeStatus.PERFORMANCE -> str(R.string.gamemode_performance)
    GameModeStatus.BATTERY -> str(R.string.gamemode_battery)
    GameModeStatus.STANDARD -> str(R.string.gamemode_standard)
    GameModeStatus.UNSUPPORTED -> str(R.string.gamemode_unsupported)
}
