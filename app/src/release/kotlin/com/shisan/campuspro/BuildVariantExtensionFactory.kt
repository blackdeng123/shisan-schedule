package com.shisan.campuspro

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

internal fun createBuildVariantExtension(): BuildVariantExtension = ReleaseBuildVariantExtension

private object ReleaseBuildVariantExtension : BuildVariantExtension {
    @Composable
    override fun SettingsEntry(onOpen: () -> Unit) = Unit

    override fun registerRoutes(builder: NavGraphBuilder, navController: NavHostController, container: AndroidAppContainer) = Unit

    override val route: String? = null
}
