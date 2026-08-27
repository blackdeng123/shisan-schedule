package com.shisan.campuspro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.shisan.campuspro.core.ui.SettingsListItem
import com.shisan.campuspro.debugtools.DeveloperToolsRoute

internal fun createBuildVariantExtension(): BuildVariantExtension = DebugBuildVariantExtension

private object DebugBuildVariantExtension : BuildVariantExtension {
    override val route: String = "debug_tools"

    @Composable
    override fun SettingsEntry(onOpen: () -> Unit) {
        SettingsListItem(
            title = "开发者工具",
            subtitle = "通知、后台任务与同步诊断",
            onClick = onOpen,
            leadingContent = { Icon(Icons.Rounded.Build, contentDescription = null) },
        )
    }

    override fun registerRoutes(builder: NavGraphBuilder, navController: NavHostController, container: AndroidAppContainer) {
        builder.composable(route) {
            DeveloperToolsRoute(container = container, onBack = navController::popBackStack)
        }
    }
}
