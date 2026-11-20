package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.PermissionManagerHelper
import com.example.domain.model.PermissionStatusSnapshot
import com.example.ui.components.CyberChamferedCard
import com.example.ui.components.str
import com.example.ui.theme.LocalCyberColors

private data class WizardStepSpec(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val optional: Boolean,
    val isGranted: (PermissionStatusSnapshot) -> Boolean,
    val onGrant: () -> Unit
)

@Composable
fun PermissionWizardScreen(
    permissions: PermissionStatusSnapshot,
    permissionHelper: PermissionManagerHelper,
    onRefreshPermissions: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var stepIndex by remember { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    // Re-check permissions immediately when returning from Android System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val steps = remember(permissions) {
        listOf(
            WizardStepSpec(
                titleRes = R.string.perm_notifications_title,
                descRes = R.string.perm_notifications_desc,
                icon = Icons.Default.NotificationsActive,
                optional = false,
                isGranted = { it.postNotificationsGranted },
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onRefreshPermissions()
                    }
                }
            ),
            WizardStepSpec(
                titleRes = R.string.perm_usage_title,
                descRes = R.string.perm_usage_desc,
                icon = Icons.Default.QueryStats,
                optional = false,
                isGranted = { it.usageAccessGranted },
                onGrant = { runCatching { context.startActivity(permissionHelper.buildUsageAccessIntent()) } }
            ),
            WizardStepSpec(
                titleRes = R.string.perm_overlay_title,
                descRes = R.string.perm_overlay_desc,
                icon = Icons.Default.Layers,
                optional = false,
                isGranted = { it.overlayGranted },
                onGrant = { runCatching { context.startActivity(permissionHelper.buildOverlayPermissionIntent()) } }
            ),
            WizardStepSpec(
                titleRes = R.string.perm_dnd_title,
                descRes = R.string.perm_dnd_desc,
                icon = Icons.Default.DoNotDisturbOn,
                optional = false,
                isGranted = { it.dndAccessGranted },
                onGrant = { runCatching { context.startActivity(permissionHelper.buildDndSettingsIntent()) } }
            ),
            WizardStepSpec(
                titleRes = R.string.perm_battery_title,
                descRes = R.string.perm_battery_desc,
                icon = Icons.Default.BatteryChargingFull,
                optional = false,
                isGranted = { it.batteryOptimizationIgnored },
                onGrant = { runCatching { context.startActivity(permissionHelper.buildIgnoreBatteryOptimizationIntent()) } }
            ),
            WizardStepSpec(
                titleRes = R.string.perm_write_settings_title,
                descRes = R.string.perm_write_settings_desc,
                icon = Icons.Default.BrightnessHigh,
                optional = true,
                isGranted = { it.writeSettingsGranted },
                onGrant = { runCatching { context.startActivity(permissionHelper.buildWriteSettingsIntent()) } }
            )
        )
    }

    val currentStep = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val granted = currentStep.isGranted(permissions)
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = str(R.string.wizard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = str(R.string.wizard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Progress bar segments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                steps.forEachIndexed { index, spec ->
                    val stepGranted = spec.isGranted(permissions)
                    val isCurrent = index == stepIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                color = when {
                                    stepGranted -> cyber.emerald
                                    isCurrent -> cyber.crimson
                                    else -> cyber.stroke
                                },
                                shape = CutCornerShape(2.dp)
                            )
                    )
                }
            }

            Text(
                text = str(R.string.wizard_step_counter, stepIndex + 1, steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = cyber.cyan
            )

            // Main Step Card
            CyberChamferedCard(
                isHighlighted = true,
                accentColor = if (granted) cyber.emerald else cyber.crimson,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = currentStep.icon,
                            contentDescription = str(currentStep.titleRes),
                            tint = if (granted) cyber.emerald else cyber.crimsonGlow,
                            modifier = Modifier.size(38.dp)
                        )
                        val badgeColor = when {
                            granted -> cyber.emerald
                            currentStep.optional -> cyber.cyan
                            else -> cyber.amber
                        }
                        val badgeText = when {
                            granted -> str(R.string.wizard_granted)
                            currentStep.optional -> str(R.string.wizard_optional)
                            else -> str(R.string.wizard_pending)
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelLarge,
                            color = badgeColor
                        )
                    }

                    Text(
                        text = str(currentStep.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = str(currentStep.descRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (granted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = str(R.string.wizard_granted),
                                tint = cyber.emerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = str(R.string.wizard_granted),
                                style = MaterialTheme.typography.labelLarge,
                                color = cyber.emerald
                            )
                        }
                    } else {
                        Button(
                            onClick = currentStep.onGrant,
                            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cyber.crimson),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("wizard_grant_button")
                        ) {
                            Text(str(R.string.wizard_grant_btn))
                        }
                    }
                }
            }

            // Navigation Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onFinish,
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("wizard_skip_button")
                ) {
                    Text(str(R.string.wizard_finish_btn))
                }

                if (stepIndex < steps.lastIndex) {
                    Button(
                        onClick = { stepIndex++ },
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cyber.cyan),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("wizard_next_button")
                    ) {
                        Text(
                            text = str(if (granted) R.string.wizard_next_btn else R.string.wizard_skip_btn),
                            color = Color.Black
                        )
                    }
                } else {
                    Button(
                        onClick = onFinish,
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cyber.emerald),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("wizard_complete_button")
                    ) {
                        Text(str(R.string.wizard_finish_btn), color = Color.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
