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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.ui.components.CyberChamferedCard
import com.example.ui.components.str
import com.example.ui.theme.LocalCyberColors
import com.example.ui.viewmodel.BoosterUiState

@Composable
fun SettingsScreen(
    uiState: BoosterUiState,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenPermissionWizard: () -> Unit,
    onToggleShizukuBridge: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = str(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            // Language Switcher (Arabic RTL / English LTR / System)
            CyberChamferedCard(
                isHighlighted = false,
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
                            imageVector = Icons.Default.Language,
                            contentDescription = str(R.string.settings_language),
                            tint = cyber.cyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = str(R.string.settings_language),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageChip(
                            label = str(R.string.settings_lang_system),
                            selected = uiState.language == AppLanguage.SYSTEM,
                            testTag = "lang_system",
                            onClick = { onSelectLanguage(AppLanguage.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageChip(
                            label = str(R.string.settings_lang_en),
                            selected = uiState.language == AppLanguage.ENGLISH,
                            testTag = "lang_en",
                            onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageChip(
                            label = str(R.string.settings_lang_ar),
                            selected = uiState.language == AppLanguage.ARABIC,
                            testTag = "lang_ar",
                            onClick = { onSelectLanguage(AppLanguage.ARABIC) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Permissions Matrix + Wizard Launcher
            CyberChamferedCard(
                isHighlighted = uiState.permissions.coreGrantedCount < 5,
                accentColor = if (uiState.permissions.coreGrantedCount == 5) cyber.emerald else cyber.amber,
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
                            imageVector = Icons.Default.Security,
                            contentDescription = str(R.string.settings_permissions_section),
                            tint = if (uiState.permissions.coreGrantedCount == 5) cyber.emerald else cyber.amber
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = str(R.string.settings_permissions_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${uiState.permissions.coreGrantedCount}/5",
                            style = MaterialTheme.typography.labelLarge,
                            color = cyber.cyan
                        )
                    }

                    Button(
                        onClick = onOpenPermissionWizard,
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cyber.crimson),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_open_wizard_button")
                    ) {
                        Text(str(R.string.settings_open_wizard))
                    }
                }
            }

            // Optional Shizuku / Root Bridge
            val hasPrivilege = uiState.privilegeStatus.shizukuInstalled || uiState.privilegeStatus.rootSuAvailable
            CyberChamferedCard(
                isHighlighted = uiState.shizukuModeEnabled && hasPrivilege,
                accentColor = cyber.violet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = str(R.string.settings_privilege_section),
                            tint = cyber.violet
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = str(R.string.settings_privilege_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Text(
                        text = str(
                            if (hasPrivilege) R.string.settings_status_ready
                            else R.string.settings_status_unavailable
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hasPrivilege) cyber.emerald else cyber.textMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = str(R.string.settings_shizuku_enable),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                            Text(
                                text = str(R.string.settings_shizuku_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = uiState.shizukuModeEnabled,
                            enabled = hasPrivilege,
                            onCheckedChange = onToggleShizukuBridge,
                            colors = SwitchDefaults.colors(checkedTrackColor = cyber.violet),
                            modifier = Modifier.testTag("settings_shizuku_switch")
                        )
                    }
                }
            }

            // Android Capabilities & Limitations
            CyberChamferedCard(
                isHighlighted = false,
                accentColor = cyber.stroke,
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
                        contentDescription = str(R.string.settings_capabilities_title),
                        tint = cyber.cyan
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = str(R.string.settings_capabilities_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = cyber.cyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = str(R.string.settings_capabilities_body),
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
private fun LanguageChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    CyberChamferedCard(
        isHighlighted = selected,
        accentColor = cyber.cyan,
        cutSize = 8.dp,
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) cyber.cyan else Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 6.dp, vertical = 10.dp)
        )
    }
}
