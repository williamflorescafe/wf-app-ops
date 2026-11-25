package com.example.wfappops

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.home.view.orderHistoryItems
import com.example.wfappops.home.viewmodel.OrderHistoryScope
import com.example.wfappops.home.viewmodel.OrderHistoryUiState
import com.example.wfappops.home.viewmodel.OrderOverview
import com.example.wfappops.orders.model.*
import com.example.wfappops.ui.theme.WfTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeOrderActivityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeListReordersOnEachStatusChangeAndOnlyMarksTheWholeOrderReady() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TableWorker("worker", "Colaborador")
        val time = Instant.parse("2026-11-24T21:00:00Z")
        var initial = TableService().open(1, worker, time).open(2, worker, time)
        val first = initial.openVisit(1)!!.id
        val second = initial.openVisit(2)!!.id
        val line = OrderLine("Café", 1, 800)
        initial = initial.add(first, line, time, worker).add(first, line.copy(product = "Agua"), time, worker)
            .sendLocally(first, worker, time)
            .add(second, line, time.plusSeconds(1), worker).sendLocally(second, worker, time.plusSeconds(1))
        val service = mutableStateOf(initial)
        compose.setContent { WfTheme {
            val state = OrderHistoryUiState(OrderHistoryScope("business", worker.id),
                Result.success(OrderOverview(service.value.registeredOrders(), service.value.serviceTables())))
            LazyColumn(Modifier.fillMaxSize()) {
                orderHistoryItems(state.homeOrders(HomeOrderFilter.ALL), {}, true, {}, R.string.home_open_orders_empty)
            }
        } }
        fun assertAbove(top: Int, bottom: Int) {
            val topBounds = compose.onNodeWithText(app.getString(R.string.order_number, top), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val bottomBounds = compose.onNodeWithText(app.getString(R.string.order_number, bottom), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(topBounds.top < bottomBounds.top)
        }
        assertAbove(2, 1)
        compose.runOnIdle {
            service.value = service.value.advanceKitchenLine(DispatchLineKey(first, 0, 0), KitchenItemStatus.SENT, time.plusSeconds(2))
                .advanceKitchenLine(DispatchLineKey(first, 0, 0), KitchenItemStatus.IN_PREPARATION, time.plusSeconds(3))
        }
        assertAbove(1, 2)
        compose.runOnIdle { assertFalse(service.value.registeredOrders().first().isFullyReady) }
        compose.onNodeWithText(app.getString(R.string.order_number, 1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, app.getString(R.string.order_preparing)))
        capture("ops-home-recent-partial.png")
        compose.runOnIdle {
            service.value = service.value.advanceKitchenLine(DispatchLineKey(second, 0, 0), KitchenItemStatus.SENT, time.plusSeconds(4))
        }
        assertAbove(2, 1)
        compose.runOnIdle {
            service.value = service.value.advanceKitchenLine(DispatchLineKey(first, 0, 1), KitchenItemStatus.SENT, time.plusSeconds(5))
                .advanceKitchenLine(DispatchLineKey(first, 0, 1), KitchenItemStatus.IN_PREPARATION, time.plusSeconds(6))
        }
        assertAbove(1, 2)
        compose.onNodeWithText(app.getString(R.string.order_number, 1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, app.getString(R.string.order_ready)))
        capture("ops-home-recent-ready.png")
        compose.runOnIdle { service.value = service.value.close(first) }
        compose.onNodeWithText(app.getString(R.string.order_number, 1)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.order_number, 2)).assertIsDisplayed()
    }

    private fun capture(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        File(app.cacheDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
