package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DraarlDialogLargeFontTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogActionsRemainUsableAtOnePointFiveFontScale() {
        verifyDialogActions(fontScale = 1.5f)
    }

    @Test
    fun dialogActionsRemainUsableAtTwoPointZeroFontScale() {
        verifyDialogActions(fontScale = 2f)
    }

    private fun verifyDialogActions(fontScale: Float) {
        var dismissClicks = 0
        var confirmClicks = 0

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale)
            ) {
                DraarlTheme(darkTheme = false) {
                    DraarlDialog(
                        title = "确认通信设置",
                        onDismissRequest = {},
                        dismissAction = DraarlAction("取消", { dismissClicks += 1 }),
                        confirmAction = DraarlAction(
                            label = "保存并应用",
                            onClick = { confirmClicks += 1 },
                            style = CommandStyle.PRIMARY
                        )
                    ) {
                        Text(
                            text = "检查当前设置后保存，新的连接参数将在下次通信时生效。",
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("取消")
            .assertFullyDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("保存并应用")
            .assertFullyDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
            assertEquals(1, confirmClicks)
        }
    }

    private fun SemanticsNodeInteraction.assertFullyDisplayed(): SemanticsNodeInteraction {
        assertIsDisplayed()
        assertEquals(
            "Node must not be clipped by its root or ancestors",
            getUnclippedBoundsInRoot(),
            getBoundsInRoot()
        )
        return this
    }
}
