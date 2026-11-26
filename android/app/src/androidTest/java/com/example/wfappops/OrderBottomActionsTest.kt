package com.example.wfappops

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.orders.view.OrderBottomActions
import com.example.wfappops.ui.theme.WfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OrderBottomActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothActionsStayVisibleAndSendingRequiresProducts() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val hasDraft = mutableStateOf(false)
        var sends = 0
        compose.setContent {
            WfTheme {
                OrderBottomActions(busy = false, hasDraft = hasDraft.value, hasDispatches = false,
                    selectedCount = null, onAdd = { hasDraft.value = true }, onSend = { sends++ }, onCancelLines = {})
            }
        }
        compose.onNodeWithText(app.getString(R.string.order_products_cta)).assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.order_send)).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription(app.getString(R.string.order_add_products)).performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.order_send)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sends) }
    }
}
