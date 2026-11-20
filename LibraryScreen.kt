package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.InstalledAppInfo
import com.example.ui.components.AppIconImage
import com.example.ui.components.CyberChamferedCard
import com.example.ui.components.str
import com.example.ui.theme.LocalCyberColors
import com.example.ui.viewmodel.BoosterUiState

@Composable
fun LibraryScreen(
    uiState: BoosterUiState,
    onAddGame: (InstalledAppInfo) -> Unit,
    onActivateInstalled: (InstalledAppInfo) -> Unit,
    onActivateSaved: (String) -> Unit,
    onRemoveGame: (String) -> Unit,
    onSelectGame: (String) -> Unit,
    onConfigureGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val haptic = LocalHapticFeedback.current
    var selectedTab by remember { mutableIntStateOf(if (uiState.savedGames.isEmpty()) 1 else 0) }
    var searchQuery by remember { mutableStateOf("") }
    var gamesOnly by remember { mutableStateOf(false) }

    val savedPackages = remember(uiState.savedGames) {
        uiState.savedGames.map { it.packageName }.toSet()
    }

    val filteredInstalled = remember(uiState.installedApps, searchQuery, gamesOnly) {
        uiState.installedApps.filter { app ->
            val matchesFilter = !gamesOnly || app.isDeclaredGame
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = str(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            // Segmented Tabs: My Library vs Installed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val tabShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                CyberChamferedCard(
                    isHighlighted = selectedTab == 0,
                    accentColor = cyber.crimson,
                    cutSize = 10.dp,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library_tab_saved")
                ) {
                    Text(
                        text = str(R.string.library_tab_saved, uiState.savedGames.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }

                CyberChamferedCard(
                    isHighlighted = selectedTab == 1,
                    accentColor = cyber.cyan,
                    cutSize = 10.dp,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier
                        .weight(1f)
                        .clip(tabShape)
                        .testTag("library_tab_installed")
                ) {
                    Text(
                        text = str(R.string.library_tab_installed, uiState.installedApps.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }

            if (selectedTab == 0) {
                if (uiState.savedGames.isEmpty()) {
                    CyberChamferedCard(
                        isHighlighted = false,
                        accentColor = cyber.crimson,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = str(R.string.library_empty_saved_title),
                                tint = cyber.crimsonGlow,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = str(R.string.library_empty_saved_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = str(R.string.library_empty_saved_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.savedGames, key = { it.packageName }) { profile ->
                            val isSelected = profile.packageName == uiState.selectedGame?.packageName
                            CyberChamferedCard(
                                isHighlighted = isSelected,
                                accentColor = cyber.crimson,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectGame(profile.packageName)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("saved_game_${profile.packageName}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconImage(
                                        drawable = uiState.iconFor(profile.packageName),
                                        contentDescription = profile.displayName,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CutCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = boostLevelLabel(profile.boostLevel),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) cyber.crimsonGlow else cyber.cyan
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onActivateSaved(profile.packageName)
                                        },
                                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = cyber.crimson),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("library_activate_${profile.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = str(R.string.library_activate),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = str(R.string.library_activate),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    IconButton(
                                        onClick = { onConfigureGame(profile.packageName) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("library_tune_${profile.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = str(R.string.library_configure),
                                            tint = cyber.cyan
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveGame(profile.packageName) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("library_remove_${profile.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = str(R.string.library_remove),
                                            tint = cyber.crimsonGlow
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Search & Games-only filter
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(str(R.string.library_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = str(R.string.library_search_hint),
                            tint = cyber.cyan
                        )
                    },
                    singleLine = true,
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cyber.cyan,
                        unfocusedBorderColor = cyber.stroke,
                        focusedContainerColor = Color(0xFF13131D),
                        unfocusedContainerColor = Color(0xFF101018)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = gamesOnly,
                        onClick = { gamesOnly = true },
                        label = { Text(str(R.string.library_filter_games_only)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cyber.crimson.copy(alpha = 0.24f),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_games_only")
                    )
                    FilterChip(
                        selected = !gamesOnly,
                        onClick = { gamesOnly = false },
                        label = { Text(str(R.string.library_filter_all_apps)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cyber.cyan.copy(alpha = 0.22f),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_all_apps")
                    )
                }

                if (uiState.isLoadingApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = cyber.crimson)
                    }
                } else if (filteredInstalled.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = str(R.string.library_empty_search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredInstalled, key = { it.packageName }) { app ->
                            val isAdded = app.packageName in savedPackages
                            CyberChamferedCard(
                                isHighlighted = isAdded,
                                accentColor = cyber.cyan,
                                cutSize = 10.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconImage(
                                        drawable = app.icon,
                                        contentDescription = app.appName,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CutCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val badgeColor = if (app.isDeclaredGame) cyber.crimson else cyber.textMuted
                                            Text(
                                                text = str(
                                                    if (app.isDeclaredGame) R.string.library_badge_game
                                                    else R.string.library_badge_app
                                                ),
                                                style = MaterialTheme.typography.labelSmall.copy(color = badgeColor),
                                                modifier = Modifier
                                                    .border(1.dp, badgeColor.copy(alpha = 0.6f), CutCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cyber.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (isAdded) {
                                                onActivateInstalled(app)
                                            } else {
                                                onAddGame(app)
                                            }
                                        },
                                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAdded) cyber.cyan else cyber.crimson
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("install_toggle_${app.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = if (isAdded) Icons.Default.Bolt else Icons.Default.Add,
                                            contentDescription = str(
                                                if (isAdded) R.string.library_activate else R.string.library_add
                                            ),
                                            tint = if (isAdded) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = str(if (isAdded) R.string.library_activate else R.string.library_add),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isAdded) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
