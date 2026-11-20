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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BoostLevel
import com.example.domain.model.GameProfile
import com.example.ui.components.AppIconImage
import com.example.ui.components.CyberChamferedCard
import com.example.ui.components.str
import com.example.ui.theme.LocalCyberColors
import com.example.ui.viewmodel.BoosterUiState
import kotlin.math.roundToInt

@Composable
fun GameConfigScreen(
    packageName: String,
    uiState: BoosterUiState,
    onUpdateProfile: (GameProfile) -> Unit,
    onResetHeadshotAnchor: (String) -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestDnd: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val profile = uiState.savedGames.firstOrNull { it.packageName == packageName }
    val scrollState = rememberScrollState()

    if (profile == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onBack) { Text(str(R.string.config_save_back)) }
        }
        return
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("config_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = str(R.string.config_save_back),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = str(R.string.config_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            // Game Header Card
            CyberChamferedCard(
                isHighlighted = true,
                accentColor = cyber.crimson,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconImage(
                        drawable = uiState.iconFor(profile.packageName),
                        contentDescription = profile.displayName,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CutCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = profile.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cyber.textMuted
                        )
                    }
                }
            }

            // Boost Level Section
            Text(
                text = str(R.string.config_boost_level),
                style = MaterialTheme.typography.labelLarge,
                color = cyber.cyan
            )

            BoostLevelOptionCard(
                title = str(R.string.config_level_balanced),
                description = str(R.string.config_level_balanced_desc),
                selected = profile.boostLevel == BoostLevel.BALANCED,
                accentColor = cyber.emerald,
                testTag = "boost_level_balanced",
                onClick = { onUpdateProfile(profile.copy(boostLevel = BoostLevel.BALANCED)) }
            )
            BoostLevelOptionCard(
                title = str(R.string.config_level_high),
                description = str(R.string.config_level_high_desc),
                selected = profile.boostLevel == BoostLevel.HIGH,
                accentColor = cyber.cyan,
                testTag = "boost_level_high",
                onClick = { onUpdateProfile(profile.copy(boostLevel = BoostLevel.HIGH)) }
            )
            BoostLevelOptionCard(
                title = str(R.string.config_level_extreme),
                description = str(R.string.config_level_extreme_desc),
                selected = profile.boostLevel == BoostLevel.EXTREME,
                accentColor = cyber.crimson,
                testTag = "boost_level_extreme",
                onClick = { onUpdateProfile(profile.copy(boostLevel = BoostLevel.EXTREME)) }
            )

            // Toggles Section
            ConfigToggleCard(
                icon = Icons.Default.LightMode,
                title = str(R.string.config_keep_screen_on),
                description = str(R.string.config_keep_screen_on_desc),
                checked = profile.keepScreenOn,
                accentColor = cyber.cyan,
                testTag = "toggle_keep_screen_on",
                onCheckedChange = { onUpdateProfile(profile.copy(keepScreenOn = it)) }
            )

            // Brightness Lock
            CyberChamferedCard(
                isHighlighted = profile.lockBrightness,
                accentColor = cyber.amber,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessHigh,
                            contentDescription = str(R.string.config_lock_brightness),
                            tint = cyber.amber
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = str(R.string.config_lock_brightness),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = str(R.string.config_lock_brightness_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.lockBrightness,
                            onCheckedChange = { enabled ->
                                if (enabled && !uiState.permissions.writeSettingsGranted) {
                                    onRequestWriteSettings()
                                }
                                onUpdateProfile(profile.copy(lockBrightness = enabled))
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = cyber.amber),
                            modifier = Modifier.testTag("toggle_lock_brightness")
                        )
                    }

                    if (profile.lockBrightness) {
                        Text(
                            text = str(R.string.config_brightness_value, profile.brightnessPercent),
                            style = MaterialTheme.typography.labelLarge,
                            color = cyber.amber
                        )
                        Slider(
                            value = profile.brightnessPercent.toFloat(),
                            onValueChange = { value ->
                                onUpdateProfile(profile.copy(brightnessPercent = value.roundToInt()))
                            },
                            valueRange = 15f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = cyber.amber,
                                activeTrackColor = cyber.amber
                            ),
                            modifier = Modifier.testTag("brightness_slider")
                        )
                        if (!uiState.permissions.writeSettingsGranted) {
                            OutlinedButton(
                                onClick = onRequestWriteSettings,
                                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                            ) {
                                Text(str(R.string.wizard_grant_btn))
                            }
                        }
                    }
                }
            }

            ConfigToggleCard(
                icon = Icons.Default.DoNotDisturbOn,
                title = str(R.string.config_auto_dnd),
                description = str(R.string.config_auto_dnd_desc),
                checked = profile.autoDnd,
                accentColor = cyber.violet,
                testTag = "toggle_auto_dnd",
                needsPermission = !uiState.permissions.dndAccessGranted,
                onRequestPermission = onRequestDnd,
                onCheckedChange = { enabled ->
                    if (enabled && !uiState.permissions.dndAccessGranted) {
                        onRequestDnd()
                    }
                    onUpdateProfile(profile.copy(autoDnd = enabled))
                }
            )

            ConfigToggleCard(
                icon = Icons.Default.Layers,
                title = str(R.string.config_floating_overlay),
                description = str(R.string.config_floating_overlay_desc),
                checked = profile.enableOverlay,
                accentColor = cyber.cyan,
                testTag = "toggle_floating_overlay",
                needsPermission = !uiState.permissions.overlayGranted,
                onRequestPermission = onRequestOverlay,
                onCheckedChange = { enabled ->
                    if (enabled && !uiState.permissions.overlayGranted) {
                        onRequestOverlay()
                    }
                    onUpdateProfile(profile.copy(enableOverlay = enabled))
                }
            )

            // Headshot Calibration Anchor Status
            CyberChamferedCard(
                isHighlighted = profile.headshotAnchorLocked,
                accentColor = cyber.cyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = str(R.string.overlay_headshot),
                        tint = if (profile.headshotAnchorLocked) cyber.emerald else cyber.textMuted
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str(R.string.overlay_headshot),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = str(
                                if (profile.headshotAnchorLocked) R.string.config_headshot_calibrated
                                else R.string.config_headshot_uncalibrated
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (profile.headshotAnchorLocked) cyber.emerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (profile.headshotAnchorLocked) {
                        OutlinedButton(
                            onClick = { onResetHeadshotAnchor(profile.packageName) },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                        ) {
                            Text(str(R.string.overlay_headshot_reset))
                        }
                    }
                }
            }

            // Android Game Mode API Info
            CyberChamferedCard(
                isHighlighted = false,
                accentColor = cyber.stroke,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = str(R.string.config_game_mode_api),
                        tint = cyber.cyan
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str(R.string.config_game_mode_api),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = gameModeLabel(uiState.selectedGameModeStatus),
                            style = MaterialTheme.typography.labelLarge,
                            color = cyber.cyan
                        )
                    }
                }
            }

            Button(
                onClick = onBack,
                shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cyber.crimson),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("config_done_button")
            ) {
                Text(str(R.string.config_save_back))
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BoostLevelOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    CyberChamferedCard(
        isHighlighted = selected,
        accentColor = accentColor,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) accentColor else Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigToggleCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    accentColor: Color,
    testTag: String,
    needsPermission: Boolean = false,
    onRequestPermission: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    CyberChamferedCard(
        isHighlighted = checked,
        accentColor = accentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
                    modifier = Modifier.testTag(testTag)
                )
            }
            if (needsPermission && onRequestPermission != null) {
                OutlinedButton(
                    onClick = onRequestPermission,
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                ) {
                    Text(str(R.string.wizard_grant_btn))
                }
            }
        }
    }
}
