package com.example

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.AppLanguage
import com.example.ui.components.CyberBackdrop
import com.example.ui.components.CyberBottomNavBar
import com.example.ui.components.CyberNavItem
import com.example.ui.components.str
import com.example.ui.navigation.GameConfigRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.MonitorRoute
import com.example.ui.navigation.PermissionWizardRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.GameConfigScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.PermissionWizardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ApexBoostTheme
import com.example.ui.theme.LocalAppResources
import com.example.ui.theme.NeonCrimson
import com.example.ui.viewmodel.BoosterViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            ApexBoostAppRoot()
        }
    }
}

@Composable
fun ApexBoostAppRoot(
    viewModel: BoosterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permissions whenever user returns to the app from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionsAndPrivileges()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effectiveLocale = remember(uiState.language) {
        when (uiState.language) {
            AppLanguage.ARABIC -> Locale("ar")
            AppLanguage.ENGLISH -> Locale("en")
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
    }
    val isArabic = effectiveLocale.language.startsWith("ar", ignoreCase = true)
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    val localizedResources = remember(context, effectiveLocale) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(effectiveLocale)
        config.setLayoutDirection(effectiveLocale)
        context.createConfigurationContext(config).resources
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppResources provides localizedResources
    ) {
        ApexBoostTheme(isArabic = isArabic) {
            CyberBackdrop {
                if (!uiState.isPrefsLoaded) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonCrimson)
                    }
                } else {
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = backStackEntry?.destination

                    // Auto-open Permission Wizard on genuine first launch
                    LaunchedEffect(uiState.onboardingCompleted) {
                        if (!uiState.onboardingCompleted &&
                            currentDestination?.hasRoute<PermissionWizardRoute>() != true
                        ) {
                            navController.navigate(PermissionWizardRoute) {
                                launchSingleTop = true
                            }
                        }
                    }

                    val showBottomBar = currentDestination?.let { dest ->
                        dest.hasRoute<HomeRoute>() ||
                            dest.hasRoute<LibraryRoute>() ||
                            dest.hasRoute<MonitorRoute>() ||
                            dest.hasRoute<SettingsRoute>()
                    } ?: true

                    fun navigateTopLevel(route: Any) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (showBottomBar) {
                                CyberBottomNavBar(
                                    items = listOf(
                                        CyberNavItem(
                                            label = str(R.string.nav_home),
                                            icon = Icons.Default.Bolt,
                                            selected = currentDestination?.hasRoute<HomeRoute>() == true,
                                            testTag = "nav_home",
                                            onClick = { navigateTopLevel(HomeRoute) }
                                        ),
                                        CyberNavItem(
                                            label = str(R.string.nav_library),
                                            icon = Icons.Default.SportsEsports,
                                            selected = currentDestination?.hasRoute<LibraryRoute>() == true,
                                            testTag = "nav_library",
                                            onClick = { navigateTopLevel(LibraryRoute) }
                                        ),
                                        CyberNavItem(
                                            label = str(R.string.nav_monitor),
                                            icon = Icons.Default.Speed,
                                            selected = currentDestination?.hasRoute<MonitorRoute>() == true,
                                            testTag = "nav_monitor",
                                            onClick = { navigateTopLevel(MonitorRoute) }
                                        ),
                                        CyberNavItem(
                                            label = str(R.string.nav_settings),
                                            icon = Icons.Default.Settings,
                                            selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                                            testTag = "nav_settings",
                                            onClick = { navigateTopLevel(SettingsRoute) }
                                        )
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute,
                            enterTransition = { fadeIn(animationSpec = tween(220)) },
                            exitTransition = { fadeOut(animationSpec = tween(180)) },
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(innerPadding)
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    uiState = uiState,
                                    onSelectGame = viewModel::selectGame,
                                    onStartBoost = viewModel::startBoostAndLaunch,
                                    onRelaunchGame = viewModel::relaunchSelectedGame,
                                    onEndSession = viewModel::endActiveBoostSession,
                                    onOpenLibrary = { navigateTopLevel(LibraryRoute) },
                                    onConfigureGame = { pkg ->
                                        navController.navigate(GameConfigRoute(pkg))
                                    },
                                    onOpenWizard = {
                                        navController.navigate(PermissionWizardRoute)
                                    }
                                )
                            }

                            composable<LibraryRoute> {
                                LibraryScreen(
                                    uiState = uiState,
                                    onAddGame = viewModel::addGameToLibrary,
                                    onActivateInstalled = viewModel::activateAndLaunchGame,
                                    onActivateSaved = viewModel::activateSavedGame,
                                    onRemoveGame = viewModel::removeGameFromLibrary,
                                    onSelectGame = { pkg ->
                                        viewModel.selectGame(pkg)
                                        navigateTopLevel(HomeRoute)
                                    },
                                    onConfigureGame = { pkg ->
                                        navController.navigate(GameConfigRoute(pkg))
                                    }
                                )
                            }

                            composable<MonitorRoute> {
                                MonitorScreen(
                                    uiState = uiState,
                                    onToggleFloatingOverlay = viewModel::toggleFloatingMonitorOverlay,
                                    onRequestOverlayPermission = {
                                        runCatching {
                                            context.startActivity(
                                                viewModel.permissionHelper.buildOverlayPermissionIntent()
                                            )
                                        }
                                    }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    uiState = uiState,
                                    onSelectLanguage = viewModel::setLanguage,
                                    onOpenPermissionWizard = {
                                        navController.navigate(PermissionWizardRoute)
                                    },
                                    onToggleShizukuBridge = viewModel::setShizukuModeEnabled
                                )
                            }

                            composable<GameConfigRoute> { entry ->
                                val route = entry.toRoute<GameConfigRoute>()
                                GameConfigScreen(
                                    packageName = route.packageName,
                                    uiState = uiState,
                                    onUpdateProfile = viewModel::updateGameProfile,
                                    onResetHeadshotAnchor = viewModel::resetHeadshotAnchor,
                                    onRequestWriteSettings = {
                                        runCatching {
                                            context.startActivity(
                                                viewModel.permissionHelper.buildWriteSettingsIntent()
                                            )
                                        }
                                    },
                                    onRequestOverlay = {
                                        runCatching {
                                            context.startActivity(
                                                viewModel.permissionHelper.buildOverlayPermissionIntent()
                                            )
                                        }
                                    },
                                    onRequestDnd = {
                                        runCatching {
                                            context.startActivity(
                                                viewModel.permissionHelper.buildDndSettingsIntent()
                                            )
                                        }
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<PermissionWizardRoute> {
                                PermissionWizardScreen(
                                    permissions = uiState.permissions,
                                    permissionHelper = viewModel.permissionHelper,
                                    onRefreshPermissions = viewModel::refreshPermissionsAndPrivileges,
                                    onFinish = {
                                        viewModel.setOnboardingCompleted(true)
                                        if (!navController.popBackStack()) {
                                            navigateTopLevel(HomeRoute)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
