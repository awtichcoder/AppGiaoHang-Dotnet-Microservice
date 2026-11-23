package com.deliveryapp.shipper

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShipperFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo()
        rule.waitForIdle()
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }
    private fun exists(tag: String) { rule.waitUntil(8000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("previews")!!
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    // Runs against MockShipperRepository: ./gradlew :shipper:connectedDebugAndroidTest -PuseMock=true
    @Test fun completeTripHistorySettingsAndLogout() {
        exists("fill_demo")
        capture("01-login")
        click("fill_demo")
        click("login")
        exists("availability")
        capture("02-home")
        click("availability")
        exists("accept_offer")
        capture("03-home-offer")
        click("accept_offer")
        exists("advance_trip")
        rule.activityRule.scenario.recreate()
        exists("advance_trip")
        capture("04-active-trip")
        // Figma steps: Đến Nhận hàng → Bắt đầu giao hàng → Xác Nhận Đã Giao Hàng (+ confirmation).
        click("advance_trip")
        rule.waitUntil(8000) { rule.onAllNodesWithText("Bắt đầu giao hàng").fetchSemanticsNodes().isNotEmpty() }
        click("advance_trip")
        rule.waitUntil(8000) { rule.onAllNodesWithText("Xác Nhận Đã Giao Hàng").fetchSemanticsNodes().isNotEmpty() }
        click("advance_trip")
        exists("confirm_delivered")
        capture("05-confirm")
        rule.onNodeWithTag("confirm_delivered").performClick()
        exists("completion_home")
        capture("07-completed")
        click("completion_home")
        rule.onNodeWithTag("tab_history").performClick()
        exists("history_delivery-1")
        capture("08-history")
        click("history_delivery-1")
        rule.onNodeWithTag("advance_trip").assertDoesNotExist()
        rule.onNodeWithTag("back").performClick()
        rule.onNodeWithTag("tab_profile").performClick()
        capture("09-profile")
        val wasLarge = rule.onNodeWithTag("large_text").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]
        click("large_text")
        rule.waitUntil(8000) {
            rule.onNodeWithTag("large_text").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] != wasLarge
        }
        rule.waitForIdle()
        click("logout")
        exists("confirm")
        rule.onNodeWithTag("confirm").performClick()
        exists("login")
        rule.onNodeWithTag("tab_home").assertDoesNotExist()
    }
    @Test fun invalidLoginAndRejectedOffer() {
        exists("login")
        click("login")
        rule.onNodeWithText("Nhập địa chỉ email hợp lệ.").assertExists()
        click("fill_demo"); click("login")
        exists("availability"); click("availability")
        exists("reject_offer")
        click("reject_offer")
        rule.waitUntil(8000) { rule.onAllNodesWithText("Đã từ chối lời mời", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("accept_offer").assertDoesNotExist()
    }
}
