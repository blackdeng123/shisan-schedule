package com.shisan.campuspro

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

internal interface BuildVariantExtension {
    @Composable
    fun SettingsEntry(onOpen: () -> Unit)

    fun registerRoutes(
        builder: NavGraphBuilder,
        navController: NavHostController,
        container: AndroidAppContainer,
    )

    val route: String?
}
