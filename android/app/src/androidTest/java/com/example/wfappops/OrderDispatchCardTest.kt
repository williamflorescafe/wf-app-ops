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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.DispatchLineKey
import com.example.wfappops.orders.model.OrderDispatch
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.advanceLocalKitchenStatus
import com.example.wfappops.orders.model.withLocalKitchenStatuses
import com.example.wfappops.orders.view.CreateOrderScreen
import com.example.wfappops.orders.view.RegisteredDispatchCard
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.ui.theme.WfTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTextInput
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

    @Test fun longPressSelectsARemoteRoundWithoutEnablingPartialCancellation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val line = OrderLine("Producto de prueba", 1, 100, kitchenStatus = KitchenItemStatus.DELIVERED)
        val visit = TableVisit("selection-test", 1, apiBacked = true, remoteOrderId = "order-test", orderNumber = 12,
            dispatches = listOf(OrderDispatch(Instant.now(), "Colaborador", listOf(line))))
        var advances = 0
        showOrder(mutableStateOf(OrderUiState(1, visit = visit, openTables = setOf(1), canSimulateLineStatus = true)),
            onAdvanceLine = { _, _, _ -> advances++ })
        compose.onNodeWithText(line.product).performTouchInput { longClick() }
        val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        compose.onNode(selected).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.order_cancel_lines)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, advances) }
        compose.onNodeWithText(app.getString(R.string.order_clear_selection)).performClick()
        compose.onAllNodes(selected).assertCountEquals(0)
    }

    @Test fun dispatchHeadersShowTheirOwnCommentInsteadOfAnAggregateStateLabel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val dispatch = OrderDispatch(Instant.now(), "Colaborador", listOf(
            OrderLine("Café de prueba", 1, 100, kitchenStatus = KitchenItemStatus.DELIVERED)),
            note = "Todos los platos deben salir al mismo tiempo")
        compose.setContent { WfTheme { Column {
            RegisteredDispatchCard(dispatch, 1, false, false, emptySet(), {}, null)
            RegisteredDispatchCard(dispatch.copy(note = "Sin hielo"), 2, false, false, emptySet(), {}, null)
        } } }
        compose.onNodeWithText(app.getString(R.string.order_dispatch_delivered)).assertDoesNotExist()
        compose.onNodeWithText(dispatch.note!!).assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.order_view_dispatch_comment, 1)).performClick()
        compose.onNode(hasText(dispatch.note) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.order_comment_close)).performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.order_view_dispatch_comment, 2)).performClick()
        compose.onNode(hasText("Sin hielo") and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    @Test fun draftCommentIsEditedFromTheDraftHeaderInsteadOfTheToolbar() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val visit = TableVisit("draft-comment-test", 1, apiBacked = true,
            draft = listOf(OrderLine("Producto de prueba", 1, 100)))
        val state = mutableStateOf(OrderUiState(1, visit = visit, openTables = setOf(1)))
        var saved: String? = null
        compose.setContent { WfTheme { CreateOrderScreen(state.value, catalog = null,
            onLoadCatalog = {}, onReload = {}, onOpenTable = {}, onAddProduct = {},
            onChangeQuantity = { _, _ -> }, onSaveNote = { _, _ -> },
            onSaveComment = { saved = it; state.value = state.value.copy(visit = visit.copy(comment = it), savedComments = 1) },
            onSendToCashier = {}, onSendOrder = {}, onAdvanceLine = { _, _, _ -> }, onCancelLines = { _, _ -> },
            onDismissKitchenUnavailable = {}, onCancel = {}) } }
        compose.onNodeWithContentDescription(app.getString(R.string.order_comment_title)).assertDoesNotExist()
        val unitsLabel = app.resources.getQuantityString(R.plurals.order_draft_units, 1, 1)
        compose.onNodeWithText(unitsLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.order_collapse_dispatch, app.getString(R.string.order_draft_status))).performClick()
        compose.onNodeWithText(unitsLabel).assertIsDisplayed()
        compose.onNodeWithText("Producto de prueba").assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.order_expand_dispatch, app.getString(R.string.order_draft_status))).performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.order_edit_draft_comment)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Servir juntos")
        compose.onNodeWithText(app.getString(R.string.order_save)).performClick()
        compose.runOnIdle { assertEquals("Servir juntos", saved) }
        compose.onNodeWithText(app.getString(R.string.order_draft_comment_title)).assertDoesNotExist()
        compose.onNodeWithText("Servir juntos").assertIsDisplayed()
    }

    @Test fun localSimulationAdvancesOnlyWhenTheStatusLabelIsTapped() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val start = Instant.now()
        val line = OrderLine("Producto de prueba", 1, 100, kitchenStatus = KitchenItemStatus.SENT,
            remoteUnitIds = listOf("unit-test"))
        val service = mutableStateOf(TableService(listOf(TableVisit("local-status-test", 1,
            apiBacked = true, remoteOrderId = "order-test", remoteVersion = 1, orderNumber = 1,
            dispatches = listOf(OrderDispatch(start, "Colaborador", listOf(line), "worker"))))))
        var advances = 0
        compose.setContent { WfTheme {
            val visit = service.value.withLocalKitchenStatuses().visits.single()
            RegisteredDispatchCard(visit.dispatches.single(), 1, false, false, emptySet(), {}, null,
                selectionEnabled = false, statusActionsOnly = true,
                onAdvanceLine = { index, _ ->
                    advances++
                    service.value = service.value.advanceLocalKitchenStatus(DispatchLineKey(visit.id, 0, index),
                        visit.dispatches.single().lines[index], 1, Instant.now())
                })
        } }
        compose.onNodeWithText(line.product).performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, advances) }
        listOf(R.string.order_line_sent, R.string.order_preparing, R.string.order_ready).forEach { label ->
            compose.onNodeWithText(app.getString(label)).performClick()
        }
        compose.runOnIdle {
            assertEquals(3, advances)
            assertEquals(KitchenItemStatus.DELIVERED, service.value.withLocalKitchenStatuses().visits.single().dispatches.single().lines.single().dispatchStatus)
            assertEquals(KitchenItemStatus.SENT, service.value.visits.single().dispatches.single().lines.single().dispatchStatus)
        }
        compose.onNodeWithText(app.getString(R.string.order_mark_delivered)).assertDoesNotExist()
    }

    @Test
    fun selectingTheCardAndMarkingAProductKeepItExpanded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.order_dispatch, 1)
        val collapse = context.getString(R.string.order_collapse_dispatch, title)
        val expand = context.getString(R.string.order_expand_dispatch, title)
        val selected = mutableStateOf(false)
        val marked = mutableStateOf(emptySet<Int>())
        var advances = 0
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
                    onToggleLine = if (selected.value) ({ index -> marked.value = setOf(index) }) else null,
                    onAdvanceLine = { _, _ -> advances++ }
                )
            }
        }

        compose.onNodeWithText(title).performTouchInput { click() }
        compose.runOnIdle { assertFalse(selected.value) }
        compose.onNodeWithText("Espresso").performTouchInput { longClick() }
        compose.runOnIdle {
            assertTrue(selected.value)
            assertEquals(0, advances)
        }
        compose.onNodeWithText("Espresso").assertIsDisplayed().performClick().assertIsOn()
        compose.onNodeWithContentDescription(collapse).performClick()
        compose.onNodeWithText("Espresso").assertDoesNotExist()
        compose.onNodeWithContentDescription(expand).performClick()
        compose.onNodeWithText("Espresso").assertIsDisplayed().assertIsOn()
    }

    @Test
    fun tappingAProductAdvancesOnlyItsStatusUntilServed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val line = OrderLine("Espresso", 1, 800)
        val other = OrderLine("Cortado", 1, 900)
        val dispatch = OrderDispatch(Instant.EPOCH, "Ana", listOf(line, other))
        val visit = TableVisit(id = "visit-1", table = 1, orderNumber = 1, dispatches = listOf(dispatch))
        var service = TableService(listOf(visit))
        val order = mutableStateOf(OrderUiState(table = 1, visit = visit, canUpdateLines = true))
        var advances = 0
        showOrder(order, onAdvanceLine = { dispatchIndex, lineIndex, expected ->
            advances++
            service = service.advanceOrderLine(DispatchLineKey(visit.id, dispatchIndex, lineIndex), expected, Instant.now())
            order.value = order.value.copy(visit = service.openVisit(1))
        })
        listOf(KitchenItemStatus.IN_PREPARATION, KitchenItemStatus.READY).forEach { status ->
            compose.onNodeWithText("Espresso").performClick()
            compose.runOnIdle {
                val lines = requireNotNull(order.value.visit).dispatches.single().lines
                assertEquals(status, lines.first().dispatchStatus)
                assertEquals(KitchenItemStatus.SENT, lines.last().dispatchStatus)
            }
        }
        compose.onNodeWithText("Espresso").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(2, advances)
            assertEquals(KitchenItemStatus.READY, order.value.visit?.dispatches?.single()?.lines?.first()?.dispatchStatus)
        }
        compose.onNodeWithText(context.getString(R.string.order_mark_delivered)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(3, advances)
            assertEquals(KitchenItemStatus.DELIVERED, order.value.visit?.dispatches?.single()?.lines?.first()?.dispatchStatus)
            assertEquals(KitchenItemStatus.SENT, order.value.visit?.dispatches?.single()?.lines?.last()?.dispatchStatus)
        }
        compose.onNodeWithText(context.getString(R.string.order_mark_delivered)).assertDoesNotExist()
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
            mutableStateOf(OrderUiState(table = visit.table, visit = visit, canUpdateLines = true)),
            onCancelLines = { index, lines -> cancellation = index to lines }
        )

        compose.onNodeWithText(title).performTouchInput { longClick() }
        compose.onNodeWithText("Espresso").performClick().assertIsOn()
        compose.onNodeWithText(cancelSelection).performClick()
        compose.onNode(hasText(cancelSelection) and hasAnyAncestor(isDialog())).performClick()

        compose.runOnIdle { assertEquals(0 to setOf(0), cancellation) }
        compose.onNodeWithText(cancelSelection).assertDoesNotExist()
        compose.onNodeWithContentDescription(newDispatch).assertIsDisplayed()
        compose.onNodeWithText("Espresso").assertIsDisplayed()
    }

    @Test
    fun cashierOpensPendingCardAndRequiresServiceBeforeConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.order_dispatch, 1)
        val cashier = context.getString(R.string.order_cashier_title)
        val send = context.getString(R.string.order_cashier_send)
        val line = OrderLine("Espresso", 1, 800, kitchenStatus = KitchenItemStatus.READY)
        val dispatch = OrderDispatch(Instant.EPOCH, "Ana", listOf(line))
        val visit = TableVisit(id = "visit-1", table = 1, orderNumber = 1, dispatches = listOf(dispatch))
        val order = mutableStateOf(OrderUiState(table = visit.table, visit = visit, canUpdateLines = true))
        var sentToCashier = false
        showOrder(
            order,
            onAdvanceLine = { dispatchIndex, lineIndex, expected ->
                assertEquals(0, dispatchIndex)
                assertEquals(0, lineIndex)
                assertEquals(KitchenItemStatus.READY, expected)
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

        compose.onNodeWithText(cashier).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.order_mark_delivered)).performClick()
        compose.onNodeWithContentDescription(cashier).assertIsDisplayed().performClick()
        compose.onNodeWithText(send).performClick()
        compose.runOnIdle { assertTrue(sentToCashier) }
    }

    @Test
    fun discardedDraftShowsReleasedTableWithoutOfferingANewDispatchOnTheCanceledOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val visit = TableVisit(id = "discarded", table = 3, orderNumber = 5,
            orderStartedAt = Instant.EPOCH, closed = true)
        showOrder(mutableStateOf(OrderUiState(table = 3, visit = visit, openTables = emptySet(), canUpdateLines = true)))
        compose.onNodeWithText(context.getString(R.string.order_draft_discarded)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.order_new_dispatch)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.order_send)).assertDoesNotExist()
    }

    private fun showOrder(
        order: State<OrderUiState>,
        onCancelLines: (Int, Set<Int>) -> Unit = { _, _ -> },
        onAdvanceLine: (Int, Int, KitchenItemStatus) -> Unit = { _, _, _ -> },
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
                    onAdvanceLine = onAdvanceLine,
                    onCancelLines = onCancelLines,
                    onDismissKitchenUnavailable = {},
                    onCancel = {}
                )
            }
        }
    }
}
