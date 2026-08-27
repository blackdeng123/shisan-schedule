package com.shisan.campuspro.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test

class AdaptiveTwoPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactWindowOnlyShowsMainPane() {
        composeRule.setContent {
            CampusAdaptiveProvider(adaptiveInfo = classifyCampusAdaptiveInfo(360, 800)) {
                AdaptiveTwoPane(
                    mainPane = { Text("主内容") },
                    supportingPane = { Text("辅助内容") },
                )
            }
        }

        composeRule.onNodeWithText("主内容").assertIsDisplayed()
        composeRule.onAllNodesWithText("辅助内容").assertCountEquals(0)
    }

    @Test
    fun expandedWindowShowsBothPanes() {
        composeRule.setContent {
            CampusAdaptiveProvider(adaptiveInfo = classifyCampusAdaptiveInfo(1280, 800)) {
                AdaptiveTwoPane(
                    mainPane = { Text("主内容") },
                    supportingPane = { Text("辅助内容") },
                )
            }
        }

        composeRule.onNodeWithText("主内容").assertIsDisplayed()
        composeRule.onNodeWithText("辅助内容").assertIsDisplayed()
    }
}
