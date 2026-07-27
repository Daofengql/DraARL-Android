package cn.silverdragon.draarl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.silverdragon.draarl.AppPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pttIsTheSelectedCenterDestination() {
        composeRule.setContent {
            MaterialTheme {
                MainBottomBar(selectedPage = AppPage.RADIO, onNavigate = {})
            }
        }

        composeRule.onNodeWithText("PTT").assertIsSelected()
        listOf("设备", "群组", "PTT", "工具", "我的").forEach { label ->
            composeRule.onNodeWithContentDescription(label).assertExists()
        }
    }

    @Test
    fun toolsDestinationDispatchesFromBottomBar() {
        var selected: AppPage? = null
        composeRule.setContent {
            MaterialTheme {
                MainBottomBar(selectedPage = AppPage.RADIO, onNavigate = { selected = it })
            }
        }

        composeRule.onNodeWithText("工具").performClick()

        assertEquals(AppPage.TOOLS, selected)
    }
}
