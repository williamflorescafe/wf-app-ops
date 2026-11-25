package com.example.wfappops

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.home.view.OrderHistoryScreen
import com.example.wfappops.home.view.OrderHistoryCard
import com.example.wfappops.home.viewmodel.OrderHistoryScope
import com.example.wfappops.home.viewmodel.OrderHistoryUiState
import com.example.wfappops.home.viewmodel.OrderOverview
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.orders.model.OrderProductSummary
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.DraftProductSummary
import com.example.wfappops.tables.tableActivityTime
import com.example.wfappops.ui.theme.WfTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

class OrderHistoryPeriodTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureClosedOrderAppearance() {
        val order = RegisteredOrder("closed-preview", 3, 5,
            Instant.parse("2026-11-25T00:04:00Z"), RegisteredOrderStatus.CLOSED, 0, setOf("worker"),
            tableName = "3", products = listOf(OrderProductSummary("Café", 1, KitchenItemStatus.DELIVERED)),
            cashierHandoffAt = Instant.parse("2026-11-25T00:04:00Z"))
        compose.setContent { WfTheme { Column(Modifier.padding(16.dp)) {
            OrderHistoryCard(order, true, {})
        } } }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.order_number, 5)).assertIsDisplayed()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.order_closed)).assertIsDisplayed()
        capture("ops-order-closed-preview.png")
    }

    @Test
    fun summaryUsesTableDisplayNameAndUpdatesTimeWhenAllProductsBecomeReady() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val order = mutableStateOf(RegisteredOrder("summary", 99, 15,
            Instant.parse("2026-11-24T21:30:00Z"), RegisteredOrderStatus.READY, 1, setOf("worker"),
            tableName = "2", tableCode = "M02", products = listOf(
                OrderProductSummary("Café", 1, KitchenItemStatus.READY),
                OrderProductSummary("Agua", 1, KitchenItemStatus.IN_PREPARATION))))
        var opened = 0
        compose.setContent { WfTheme { Column(Modifier.padding(16.dp)) {
            OrderHistoryCard(order.value, true, { opened++ })
        } } }
        capture("ops-order-summary-pending.png")
        val number = app.getString(R.string.order_number, 15)
        val initialTime = tableActivityTime.format(Instant.parse("2026-11-24T21:30:00Z"))
        val readyTime = tableActivityTime.format(Instant.parse("2026-11-24T21:35:00Z"))
        compose.onNodeWithText(number).assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("M02").assertDoesNotExist()
        compose.onNodeWithText(initialTime).assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            app.getString(R.string.order_preparing))).assertIsDisplayed()
        val numberBounds = compose.onNodeWithText(number, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tableBounds = compose.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val timeBounds = compose.onNodeWithText(initialTime, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(numberBounds.right < tableBounds.left)
        assertTrue(tableBounds.right < timeBounds.left)
        compose.runOnIdle {
            order.value = order.value.copy(lastActivityAt = Instant.parse("2026-11-24T21:35:00Z"), readyQuantity = 2,
                products = order.value.products.map { it.copy(status = KitchenItemStatus.READY) })
        }
        compose.onNodeWithText(readyTime).assertIsDisplayed()
        compose.onNodeWithText(initialTime).assertDoesNotExist()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            app.getString(R.string.order_ready))).assertIsDisplayed()
        capture("ops-order-summary-ready.png")
        compose.onNodeWithContentDescription(app.getString(R.string.order_products_expand)).performClick()
        compose.onNodeWithText("Café").assertIsDisplayed()
        compose.onNodeWithText("Agua").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.runOnIdle { order.value = order.value.copy(status = RegisteredOrderStatus.DRAFT,
            products = emptyList(), draftProducts = listOf(DraftProductSummary("Producto en borrador", 1)),
            hasDraftProducts = true, hasKitchenDispatches = false, readyQuantity = 0) }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            app.getString(R.string.home_order_draft))).assertIsDisplayed()
        capture("ops-order-summary-draft.png")
        compose.onNodeWithText("0 productos").assertDoesNotExist()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.order_product_count, 1, 1)).assertIsDisplayed()
        compose.onNodeWithText("Producto en borrador").assertIsDisplayed()
        compose.runOnIdle { order.value = order.value.copy(draftProducts = emptyList(), hasDraftProducts = false,
            status = RegisteredOrderStatus.CANCELED) }
        compose.onNodeWithText(number).assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.order_products_expand)).assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.order_products_collapse)).assertDoesNotExist()
    }

    private fun capture(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        File(app.cacheDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun realHistoryScreenShowsTwoDayHeaderAndBothDateGroups() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val day = LocalDate.of(2026, 11, 24)
        fun order(number: Int, date: String, name: String) = RegisteredOrder(
            visitId = "visit-$number", table = number, number = number,
            lastActivityAt = Instant.parse(date), startedAt = Instant.parse(date),
            status = RegisteredOrderStatus.CLOSED, readyQuantity = 0, employeeIds = setOf("worker"), tableName = name,
            products = listOf(OrderProductSummary("Café", 1, KitchenItemStatus.DELIVERED))
        )
        val state = OrderHistoryUiState(
            OrderHistoryScope("business", "worker"),
            Result.success(OrderOverview(listOf(
                order(1, "2026-11-24T17:00:00Z", "Mesa de hoy"),
                order(2, "2026-11-23T17:00:00Z", "Mesa de ayer"),
                order(3, "2026-11-22T17:00:00Z", "Mesa antigua")
            ), emptyList())), day
        )
        compose.setContent {
            val empty = order(4, "2026-11-24T18:00:00Z", "Mesa sin productos").copy(products = emptyList())
            WfTheme { OrderHistoryScreen(state.orders(true)?.map { it + empty }, day, {}, false, {}) }
        }
        compose.onNodeWithText(app.getString(R.string.orders_last_two_days)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.home_orders_today)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.orders_yesterday)).assertIsDisplayed()
        compose.onNodeWithText("Mesa de hoy").assertIsDisplayed()
        compose.onNodeWithText("Mesa de ayer").assertIsDisplayed()
        compose.onNodeWithText("Mesa antigua").assertDoesNotExist()
        compose.onNodeWithText("Mesa sin productos").assertDoesNotExist()
    }
}
