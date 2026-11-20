package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object LibraryRoute

@Serializable
object MonitorRoute

@Serializable
object SettingsRoute

@Serializable
data class GameConfigRoute(val packageName: String)

@Serializable
object PermissionWizardRoute
