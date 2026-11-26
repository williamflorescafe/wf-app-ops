package com.example.wfappops

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.home.view.OrderSummaryCard
import com.example.wfappops.orders.model.*
import com.example.wfappops.orders.view.OrderDraftCard
import com.example.wfappops.orders.view.RegisteredDispatchCard
import com.example.wfappops.ui.theme.WfTheme
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class DraftHeaderDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryShowsStableDraftNumberAndUnassignedTableInsteadOfInternalCounter() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val service = TableService(listOf(TableVisit("draft", 38, apiBacked = true, openedAt = Instant.now(),
            draft = listOf(OrderLine("Americano", 1, 1400))))).withLocalDraftNumbers()
        compose.setContent { WfTheme {
            OrderSummaryCard(service.registeredOrders().single(), true, {}, HomeOrderFilter.ALL)
        } }
        compose.onNodeWithText("Borrador #1").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.order_table_unassigned)).assertIsDisplayed()
        compose.onNodeWithText("38").assertDoesNotExist()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.order_summary_products_count, 1, 1)).assertIsDisplayed()
        capture("ops-numbered-draft-card.png")
    }

    @Test fun noteLivesInHeaderAndCollapsedDraftShowsTheSumOfUnits() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val note = "Quiero que todos los platos salgan al mismo tiempo."
        compose.setContent { WfTheme { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RegisteredDispatchCard(OrderDispatch(Instant.now(), "Colaborador", listOf(
                OrderLine("Latte", 1, 1200, options = listOf("null"), note = "Temperatura media.", kitchenStatus = KitchenItemStatus.IN_PREPARATION),
                OrderLine("Cheesecake", 1, 1800, note = "Con salsa de frutos rojos.", kitchenStatus = KitchenItemStatus.READY)
            ), note = note), 1, false, false, emptySet(), {}, null)
            OrderDraftCard(2, listOf(OrderLine("Americano", 3, 1400), OrderLine("Postre", 2, 900)),
                false, 0, 0, {}, { _, _ -> }, onComment = {}, time = "5:00 p. m.")
        } } }
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.order_add_round_note)).assertIsDisplayed()
        compose.onNodeWithText("Producto").assertDoesNotExist()
        compose.onNodeWithText("null").assertDoesNotExist()
        capture("ops-round-note-header-expanded.png")
        compose.onNodeWithContentDescription(app.getString(R.string.order_collapse_dispatch,
            app.getString(R.string.order_draft_status))).performClick()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.order_draft_units, 5, 5L)).assertIsDisplayed()
        compose.onNodeWithText("Americano").assertDoesNotExist()
        capture("ops-round-note-header-collapsed.png")
    }

    private fun capture(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        File(app.cacheDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
