package com.example.wfappops

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderDispatch
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.view.CreateOrderScreen
import com.example.wfappops.orders.view.RegisteredDispatchCard
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.ui.theme.WfTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderDispatchCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectingTheCardAndMarkingAProductKeepItExpanded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.order_dispatch, 1)
        val collapse = context.getString(R.string.order_collapse_dispatch, title)
        val expand = context.getString(R.string.order_expand_dispatch, title)
        val selected = mutableStateOf(false)
        val marked = mutableStateOf(emptySet<Int>())
        val dispatch = OrderDispatch(Instant.EPOCH, "Ana", listOf(OrderLine("Espresso", 1, 800)))

        compose.setContent {
            WfTheme {
                RegisteredDispatchCard(
                    dispatch = dispatch,
                    ordinal = 1,
                    selected = selected.value,
                    busy = false,
                    markedLines = marked.value,
                    onSelect = { selected.value = !selected.value },
                    onToggleLine = if (selected.value) ({ index -> marked.value = setOf(index) }) else null
                )
            }
        }

        compose.onNodeWithText(title).performTouchInput { click() }
        compose.runOnIdle { assertFalse(selected.value) }
        compose.onNodeWithText(title).performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(selected.value) }
        compose.onNodeWithText("Espresso").assertIsDisplayed().performClick().assertIsOn()
        compose.onNodeWithContentDescription(collapse).performClick()
        compose.onNodeWithText("Espresso").assertDoesNotExist()
        compose.onNodeWithContentDescription(expand).performClick()
        compose.onNodeWithText("Espresso").assertIsDisplayed().assertIsOn()
    }

    @Test
    fun confirmingCancellationClearsSelectionBeforeTheVisitRefreshes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.order_dispatch, 1)
        val cancelSelection = context.getString(R.string.order_cancel_lines)
        val newDispatch = context.getString(R.string.order_new_dispatch)
        val dispatch = OrderDispatch(Instant.EPOCH, "Ana", listOf(OrderLine("Espresso", 1, 800)))
        val visit = TableVisit(id = "visit-1", table = 1, dispatches = listOf(dispatch))
        var cancellation: Pair<Int, Set<Int>>? = null
        showOrder(
            mutableStateOf(OrderUiState(table = visit.table, visit = visit, simulated = true)),
            onCancelLines = { index, lines -> cancellation = index to lines }
        )

        compose.onNodeWithText(title).performTouchInput { longClick() }
        compose.onNodeWithText("Espresso").performClick().assertIsOn()
        compose.onNodeWithText(cancelSelection).performClick()
        compose.onNode(hasText(cancelSelection) and hasAnyAncestor(isDialog())).performClick()

        compose.runOnIdle { assertEquals(0 to setOf(0), cancellation) }
        compose.onNodeWithText(cancelSelection).assertDoesNotExist()
        compose.onNodeWithText(newDispatch).assertIsDisplayed()
        compose.onNodeWithText("Espresso").assertIsDisplayed()
    }

    @Test
    fun cashierOpensPendingCardAndRequiresServiceBeforeConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.order_dispatch, 1)
        val cashier = context.getString(R.string.order_cashier_title)
        val send = context.getString(R.string.order_cashier_send)
        val serve = context.getString(R.string.order_mark_dispatch_delivered)
        val line = OrderLine("Espresso", 1, 800, kitchenStatus = KitchenItemStatus.READY)
        val dispatch = OrderDispatch(Instant.EPOCH, "Ana", listOf(line))
        val visit = TableVisit(id = "visit-1", table = 1, orderNumber = 1, dispatches = listOf(dispatch))
        val order = mutableStateOf(OrderUiState(table = visit.table, visit = visit, simulated = true))
        var sentToCashier = false
        showOrder(
            order,
            onDeliverDispatch = {
                val served = dispatch.copy(lines = listOf(line.copy(kitchenStatus = KitchenItemStatus.DELIVERED)))
                order.value = order.value.copy(visit = visit.copy(dispatches = listOf(served)))
            },
            onSendToCashier = { sentToCashier = true }
        )

        compose.onNodeWithContentDescription(context.getString(R.string.order_collapse_dispatch, title)).performClick()
        compose.onNodeWithText("Espresso").assertDoesNotExist()
        compose.onNodeWithContentDescription(cashier).performClick()
        compose.onNodeWithText("Espresso").assertIsDisplayed()
        compose.onNodeWithText(send).assertDoesNotExist()

        compose.onNodeWithText(serve).performClick()
        compose.onNodeWithContentDescription(cashier).performClick()
        compose.onNodeWithText(send).performClick()
        compose.runOnIdle { assertTrue(sentToCashier) }
    }

    private fun showOrder(
        order: State<OrderUiState>,
        onCancelLines: (Int, Set<Int>) -> Unit = { _, _ -> },
        onDeliverDispatch: (Int) -> Unit = {},
        onSendToCashier: () -> Unit = {}
    ) {
        compose.setContent {
            WfTheme {
                CreateOrderScreen(
                    state = order.value,
                    catalog = null,
                    onLoadCatalog = {},
                    onReload = {},
                    onOpenTable = {},
                    onAddProduct = {},
                    onChangeQuantity = { _, _ -> },
                    onSaveNote = { _, _ -> },
                    onSaveComment = {},
                    onSendToCashier = onSendToCashier,
                    onSendOrder = {},
                    onDeliverLine = { _, _ -> },
                    onDeliverDispatch = onDeliverDispatch,
                    onCancelLines = onCancelLines,
                    onDismissKitchenUnavailable = {},
                    onCancel = {}
                )
            }
        }
    }
}
