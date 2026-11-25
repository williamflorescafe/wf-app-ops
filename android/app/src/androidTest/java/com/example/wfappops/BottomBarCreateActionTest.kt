package com.example.wfappops

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.ui.components.OpsBottomBar
import com.example.wfappops.ui.components.OpsTab
import com.example.wfappops.ui.theme.WfTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BottomBarCreateActionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun createActionIsCenteredAndDoesNotChangeTheSelectedDestination() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        var selected by mutableStateOf(OpsTab.HOME)
        var creations = 0
        compose.setContent {
            WfTheme { OpsBottomBar(selected, onSelect = { selected = it }, onCreateOrder = { creations++ }) }
        }
        val action = compose.onNodeWithContentDescription(app.getString(R.string.home_create_order))
        action.assertIsDisplayed()
        val button = action.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val orders = compose.onNodeWithText(app.getString(R.string.ops_orders)).fetchSemanticsNode().boundsInRoot
        val tables = compose.onNodeWithText(app.getString(R.string.tables_title)).fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, button.center.x, 1f)
        assertTrue(orders.center.x < button.center.x && button.center.x < tables.center.x)
        File(app.cacheDir, "ops-bottom-bar-center.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        action.performClick()
        compose.onNodeWithText(app.getString(R.string.ops_home)).assertIsSelected()
        compose.onNodeWithText(app.getString(R.string.tables_title)).performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(1, creations) }
    }

    @Test
    fun createActionIsDisabledWhileTheAccountIsBusy() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            WfTheme { OpsBottomBar(OpsTab.HOME, onSelect = {}, onCreateOrder = {}, createOrderEnabled = false) }
        }
        compose.onNodeWithContentDescription(app.getString(R.string.home_create_order)).assertIsNotEnabled()
    }
}
