package cn.silverdragon.draarl.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun clearAppState() {
        device.executeShellCommand("pm clear $PACKAGE_NAME")
    }

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            device.dismissCompatibilityDialogIfPresent()
            device.waitForText("通信客户端")

            device.findObject(By.text("注册账号")).click()
            device.waitForText("创建新账号")

            device.pressBack()
            device.waitForText("通信客户端")
        }
    }

    private fun UiDevice.waitForText(text: String) {
        check(wait(Until.hasObject(By.text(text)), UI_TIMEOUT_MILLIS)) {
            "Timed out waiting for '$text'"
        }
    }

    private fun UiDevice.dismissCompatibilityDialogIfPresent() {
        wait(Until.findObject(By.res("android:id/button1")), SYSTEM_DIALOG_TIMEOUT_MILLIS)?.click()
    }

    private companion object {
        const val PACKAGE_NAME = "cn.silverdragon.draarl"
        const val SYSTEM_DIALOG_TIMEOUT_MILLIS = 2_000L
        const val UI_TIMEOUT_MILLIS = 10_000L
    }
}
