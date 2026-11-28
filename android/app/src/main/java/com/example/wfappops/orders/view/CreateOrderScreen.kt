package com.example.wfappops.orders.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.wfappops.R
import com.example.wfappops.carta.CartaCollection
import com.example.wfappops.carta.OrderNoteField
import com.example.wfappops.carta.formatCartaPrice
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.canCancelUnitSelection
import com.example.wfappops.orders.model.FulfillmentRequest
import com.example.wfappops.orders.domain.TrackingWarning
import com.example.wfappops.ui.components.InlineMessage
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.orders.model.FulfillmentAction
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.orders.viewmodel.OrderErrorSource
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.ErrorState
import com.example.wfappops.ui.components.WfDialog
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class CashierAttention(val request: Int, val cardIndex: Int?, val message: Int)
private data class SelectedFulfillment(val visit: TableVisit, val line: OrderLine, val action: FulfillmentAction)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateOrderScreen(
    state: OrderUiState,
    catalog: Result<List<CartaCollection>>?,
    onLoadCatalog: () -> Unit,
    onReload: () -> Unit,
    onAddProduct: (OrderLine) -> Unit,
    onChangeQuantity: (OrderLine, Int) -> Unit,
    onSaveNote: (OrderLine, String) -> Unit,
    onSaveComment: (String) -> Unit,
    onSendToCashier: () -> Unit,
    onSendOrder: () -> Unit,
    onAdvanceLine: (Int, Int, KitchenItemStatus) -> Unit,
    onCancelLines: (Int, Set<Int>) -> Unit,
    onDismissKitchenUnavailable: () -> Unit,
    onCancel: () -> Unit,
    onOpenApiTable: (String) -> Unit = {},
    onLoadTables: (String?, Boolean) -> Unit = { _, _ -> },
    onRefreshCatalog: () -> Unit = onLoadCatalog,
    onMenuLocation: (String) -> Unit = {},
    onSendSelection: (Map<Int, Int>) -> Unit = { onSendOrder() },
    onCancelOrder: (Int, String) -> Unit = { _, _ -> },
    onRecoverDraft: () -> Unit = {},
    onCancelUnits: (Int, Set<Int>, String, Int) -> Unit = { index, lines, _, _ -> onCancelLines(index, lines) },
    onRetryPreparation: () -> Unit = {},
    onFulfillmentCommand: (FulfillmentRequest) -> Unit = {},
    availableActions: (TableVisit, OrderLine) -> List<FulfillmentAction> = { _, _ -> emptyList() },
    onCheckClosure: () -> Unit = {},
    onCloseAttention: () -> Unit = {},
    onDismissClosure: () -> Unit = {}
) {
    val number = state.table
    val visit = state.visit
    val activeVisit = visit?.takeUnless { it.closed }
    val draft = visit?.draft.orEmpty()
    val draftLocked = state.busy || visit?.pendingSend != null || visit?.pendingPreparation != null || visit?.pendingClose != null || visit?.closed == true
    var fulfillmentDecision by remember { mutableStateOf<SelectedFulfillment?>(null) }
    var showSendChoice by remember { mutableStateOf(false) }
    var selectingDraft by rememberSaveable(visit?.id, draft) { mutableStateOf(false) }
    var draftMarked by rememberSaveable(visit?.id, draft) { mutableStateOf(intArrayOf()) }
    val selectedDraftLines = draftMarked.filterTo(mutableSetOf()) { it in draft.indices }
    var cancellationVersion by remember { mutableStateOf<Int?>(null) }
    var cancellationReason by rememberSaveable { mutableStateOf("") }
    val busy = state.busy
    val failed = state.storageError
    var showProducts by rememberSaveable(number, activeVisit?.id) {
        mutableStateOf(activeVisit?.remoteTableId != null && activeVisit.remoteOrderId == null && draft.isEmpty() && activeVisit.dispatches.isEmpty())
    }
    var showTablePicker by rememberSaveable { mutableStateOf(false) }
    var selectingTable by rememberSaveable { mutableStateOf(false) }
    fun openTablePicker() {
        val currentVisit = activeVisit ?: return
        if (busy) return
        showProducts = false
        onLoadTables(currentVisit.locationId, false)
        showTablePicker = true
    }
    LaunchedEffect(activeVisit?.remoteTableId) {
        if (activeVisit?.remoteTableId != null && activeVisit.remoteOrderId == null && draft.isEmpty() && activeVisit.dispatches.isEmpty()) showProducts = true
    }
    LaunchedEffect(state.busy, state.storageError, state.visit?.remoteTableId) {
        if (selectingTable && !state.busy) {
            selectingTable = false
            if (!state.storageError && state.visit?.remoteTableId != null) {
                showTablePicker = false
                showProducts = draft.isEmpty() && activeVisit?.remoteOrderId == null && activeVisit?.dispatches?.isEmpty() == true
            }
        }
    }
    var pulseNewDispatch by rememberSaveable { mutableStateOf(false) }
    var selectedDispatchIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var markedLines by rememberSaveable { mutableStateOf(intArrayOf()) }
    var confirmCancelLines by rememberSaveable { mutableStateOf(false) }
    var unitCancellationReason by rememberSaveable(visit?.id) { mutableStateOf("") }
    var unitCancellationVersion by rememberSaveable { mutableIntStateOf(0) }
    var confirmCashier by rememberSaveable(visit?.id) { mutableStateOf(false) }
    var cashierAttention by remember(activeVisit?.id) { mutableStateOf<CashierAttention?>(null) }
    var cashierPulseRequest by remember { mutableIntStateOf(0) }
    val cashierFeedback = remember { SnackbarHostState() }
    val canSendToCashier = activeVisit?.canSendToCashier == true
    var editingComment by rememberSaveable(visit?.id) { mutableStateOf(false) }
    var comment by rememberSaveable(visit?.id) { mutableStateOf("") }
    var handledComments by rememberSaveable { mutableIntStateOf(state.savedComments) }
    var draftFocusRequest by rememberSaveable { mutableIntStateOf(0) }
    var productCountAtOpening by rememberSaveable { mutableIntStateOf(state.addedProducts) }

    BackHandler(enabled = busy) {}
    BackHandler(enabled = selectingDraft && !busy) { selectingDraft = false; draftMarked = intArrayOf() }
    val selectedIndex = selectedDispatchIndex?.takeIf { index -> activeVisit?.dispatches?.getOrNull(index)?.let {
        it.canceledAt == null && it.activeLines.isNotEmpty()
    } == true }
    val selectedDispatch = selectedIndex?.let { visit?.dispatches?.get(it) }
    val canMarkLines = state.canUpdateLines && activeVisit != null && activeVisit.pendingCancellation == null && activeVisit.pendingSend == null && selectedDispatch?.lines?.any { it.canCancelUnitSelection() } == true
    val selectedLines = markedLines.filterTo(mutableSetOf()) { index ->
        canMarkLines && selectedDispatch.lines.getOrNull(index)?.canCancelUnitSelection() == true
    }

    fun selectDispatch(index: Int?) {
        selectedDispatchIndex = index
        markedLines = intArrayOf()
        confirmCancelLines = false
    }

    val listState = rememberLazyListState()
    LaunchedEffect(failed, showProducts) {
        if (failed && !showProducts) listState.animateScrollToItem(0)
    }
    val firstDispatchIndex = (if (state.trackingWarning != null) 1 else 0) + (if (failed && !showProducts) 1 else 0) +
        (if (state.openTables == null && busy) 1 else 0) + (if (visit?.pendingSend != null) 1 else 0) +
        (if (visit?.closed == true && draft.isNotEmpty()) 1 else 0) +
        (if (state.openTables != null && visit == null) 1 else 0) + (if (visit?.closed == true) 1 else 0)
    val draftIndex = firstDispatchIndex + (visit?.dispatches?.size ?: 0)
    val cashierHint = cashierAttention?.let { stringResource(it.message) }
    LaunchedEffect(cashierAttention, canSendToCashier) {
        cashierPulseRequest = 0
        val attention = cashierAttention ?: return@LaunchedEffect
        if (canSendToCashier) return@LaunchedEffect
        if (cashierHint != null) launch { cashierFeedback.showSnackbar(cashierHint) }
        attention.cardIndex?.let { index ->
            val position = firstDispatchIndex + index
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > position }
            listState.animateScrollToItem(position)
            cashierPulseRequest = attention.request
        }
    }
    LaunchedEffect(draftFocusRequest) {
        if (draftFocusRequest > 0 && draft.isNotEmpty() && !showProducts) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > draftIndex }
            listState.animateScrollToItem(draftIndex)
        }
    }
    var editingNote by remember { mutableStateOf<OrderLine?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.savedNotes) { editingNote = null }
    LaunchedEffect(state.savedComments) {
        if (handledComments != state.savedComments) editingComment = false
        handledComments = state.savedComments
    }
    LaunchedEffect(visit?.closed) { if (visit?.closed == true) confirmCashier = false }
    BackHandler(enabled = selectedIndex != null && !busy) { selectDispatch(null) }

    OpsScaffold(containerColor = WfThemeExtras.operationsBackground, snackbarHost = { SnackbarHost(cashierFeedback) }, topBar = {
        OrderTableHeader(
            number, visit, enabled = !busy,
            workerName = state.workerName,
            onSelectTable = if (activeVisit != null && activeVisit.remoteOrderId == null && !draftLocked) ({ openTablePicker() }) else null,
            onCancelOrder = if (activeVisit?.remoteOrderId != null && activeVisit.pendingSend == null && state.canCancelOrder) ({ cancellationVersion = activeVisit.remoteVersion }) else null,
            onBack = { if (selectedIndex != null) selectDispatch(null) else onCancel() },
            cashierBlocked = activeVisit?.remoteOrderId == null && !canSendToCashier,
            cashierEnabled = activeVisit?.remoteOrderId != null && !draftLocked && activeVisit.pendingCancellation == null || activeVisit?.apiBacked != true,
            onCashier = activeVisit?.let { openVisit ->
                {
                    if (openVisit.remoteOrderId != null) {
                        onCheckClosure()
                    } else if (canSendToCashier) {
                        confirmCashier = true
                    } else {
                        selectDispatch(null)
                        val pendingIndex = openVisit.dispatches.indexOfFirst { dispatch ->
                            dispatch.activeLines.any { it.dispatchStatus != KitchenItemStatus.DELIVERED }
                        }.takeIf { it >= 0 }
                        val pending = pendingIndex?.let { openVisit.dispatches[it].activeLines }
                        val message = when {
                            pending?.any { it.dispatchStatus == KitchenItemStatus.READY } == true -> R.string.order_cashier_serve_first
                            pending != null -> R.string.order_cashier_preparation_pending
                            draft.isNotEmpty() -> R.string.order_cashier_draft_pending
                            else -> R.string.order_cashier_no_dispatch
                        }
                        cashierAttention = CashierAttention(
                            request = (cashierAttention?.request ?: 0) + 1,
                            cardIndex = pendingIndex ?: openVisit.dispatches.size.takeIf { draft.isNotEmpty() },
                            message = message
                        )
                    }
                }
            }
        )
    }, bottomBar = {
        val standaloneActionModifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(WfSpacing.md)
        if (visit?.pendingClose != null) {
            WfButton(stringResource(R.string.closure_retry), onClick = onCloseAttention,
                enabled = !busy, fullWidth = true, modifier = standaloneActionModifier)
        } else if (visit?.pendingPreparation != null) {
            WfButton(stringResource(R.string.fulfillment_retry), onClick = onRetryPreparation,
                enabled = !busy, fullWidth = true, modifier = standaloneActionModifier)
        } else if (visit?.pendingCancellation != null) {
            WfButton(stringResource(R.string.order_retry_unit_cancellation), onClick = { onCancelUnits(-1, emptySet(), "", 0) }, enabled = !busy, fullWidth = true, modifier = standaloneActionModifier)
        } else if (activeVisit != null && selectedIndex != null && !state.canUpdateLines) {
            WfButton(stringResource(R.string.order_clear_selection), onClick = { selectDispatch(null) },
                enabled = !busy, fullWidth = true, style = WfButtonStyle.SECONDARY,
                modifier = standaloneActionModifier)
        } else if (activeVisit != null && (selectedIndex == null || canMarkLines)) {
            OrderBottomActions(
                busy = busy, hasDraft = draft.isNotEmpty(), hasDispatches = activeVisit.dispatches.isNotEmpty(),
                selectedCount = selectedIndex?.let { selectedLines.size },
                onAdd = {
                    if (selectingDraft) { selectingDraft = false; draftMarked = intArrayOf() }
                    else {
                        pulseNewDispatch = false
                        productCountAtOpening = state.addedProducts
                        showProducts = true
                    }
                },
                onSend = {
                    if (activeVisit.pendingSend != null) onSendOrder()
                    else if (activeVisit.remoteTableId == null) openTablePicker()
                    else if (selectingDraft) onSendSelection(selectedDraftLines.associateWith { draft[it].quantity })
                    else if (draft.size == 1) onSendSelection(mapOf(0 to draft.single().quantity))
                    else showSendChoice = true
                },
                addingEnabled = !draftLocked,
                selectingDraft = selectingDraft,
                sendingEnabled = (!activeVisit.apiBacked || state.canSend) && (!selectingDraft || selectedDraftLines.size in 1..60),
                onCancelLines = { unitCancellationReason = ""; unitCancellationVersion = activeVisit.remoteVersion ?: 0; confirmCancelLines = true }
            )
        }
    }) {
        if (state.openingSelectedTable) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (number != null) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(WfSpacing.md),
                verticalArrangement = Arrangement.spacedBy(WfSpacing.md)
            ) {
                state.trackingWarning?.let { warning -> item(key = "tracking-warning") {
                    InlineMessage(stringResource(if (warning == TrackingWarning.ACCESS_DENIED)
                        R.string.order_tracking_access_denied else R.string.order_tracking_unavailable), StatusTone.WARNING)
                } }
                if (failed && !showProducts) item {
                    ErrorState(
                        state.operationMessage ?: stringResource(R.string.state_save_unconfirmed), onRetry = onReload,
                        busy = busy, retryLabel = stringResource(R.string.state_reload)
                    )
                }
                if (visit?.pendingSend != null) item {
                    Text(stringResource(R.string.order_pending_retry), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (visit?.closed == true && draft.isNotEmpty()) item {
                    Text(stringResource(R.string.order_retained_draft), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WfButton(stringResource(R.string.order_recover_draft), onClick = onRecoverDraft, enabled = !busy,
                        style = WfButtonStyle.OUTLINED, fullWidth = true)
                }
                if (state.openTables == null && busy) item { CircularProgressIndicator() }
                if (state.openTables != null && visit == null) item {
                    Text(stringResource(R.string.order_table_unavailable))
                }
                if (visit?.closed == true) item {
                    val label = when {
                        visit.remoteStatus == "canceled" -> R.string.order_canceled
                        visit.isDiscardedDraft -> R.string.order_draft_discarded
                        visit.cashierHandoff != null -> R.string.order_cashier_registered
                        else -> R.string.order_closed
                    }
                    Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    visit.remoteCancelReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                if (visit != null) {
                    val realTracking = visit.remoteFulfillment.isNotEmpty()
                    val canAdvance = realTracking && activeVisit != null && selectedIndex == null && !draftLocked
                    items(visit.dispatches.size, key = { index -> "dispatch-$index" }) { index ->
                        RegisteredDispatchCard(
                            visit.dispatches[index], ordinal = visit.dispatches[index].sequence ?: (index + 1),
                            selectionEnabled = activeVisit != null,
                            statusActionsOnly = state.canSimulateLineStatus || realTracking,
                            canAdvanceLine = { line -> realTracking && line.dispatchStatus == KitchenItemStatus.READY && FulfillmentAction.DELIVER in availableActions(visit, line) },
                            operations = if (realTracking) ({ line -> availableActions(visit, line) }) else null,
                            onOperation = { line, action -> fulfillmentDecision = SelectedFulfillment(visit, line, action) },
                            selected = selectedIndex == index,
                            busy = draftLocked,
                            attentionRequest = cashierPulseRequest.takeIf { cashierAttention?.cardIndex == index } ?: 0,
                            markedLines = if (selectedIndex == index) markedLines.toSet() else emptySet(),
                            onSelect = {
                                if (!busy) selectDispatch(if (selectedIndex == index) null else index)
                            },
                            onToggleLine = if (activeVisit != null && !draftLocked) {
                                { lineIndex ->
                                    val current = if (selectedIndex == index) markedLines.toSet() else emptySet()
                                    val next = if (lineIndex in current) current - lineIndex else current + lineIndex
                                    selectedDispatchIndex = index.takeIf { next.isNotEmpty() }
                                    markedLines = next.toIntArray()
                                    confirmCancelLines = false
                                }
                            } else null,
                            onAdvanceLine = if (canAdvance) {
                                { lineIndex, expected -> onAdvanceLine(index, lineIndex, expected) }
                            } else null
                        )
                    }
                    if (draft.isNotEmpty()) item(key = "draft") {
                        OrderDraftCard(
                            lines = draft,
                            selecting = selectingDraft, markedLines = selectedDraftLines,
                            onToggleLine = { index ->
                                draftMarked = (if (index in selectedDraftLines) selectedDraftLines - index else selectedDraftLines + index).toIntArray()
                            },
                            busy = draftLocked,
                            attentionRequest = cashierPulseRequest.takeIf { cashierAttention?.cardIndex == visit.dispatches.size } ?: 0,
                            onEditNote = { line ->
                                note = line.note.orEmpty()
                                editingNote = line
                            },
                            onAdjust = onChangeQuantity,
                            onComment = activeVisit?.takeUnless { draftLocked }?.let { openVisit ->
                                { comment = openVisit.comment.orEmpty(); editingComment = true }
                            },
                            comment = visit.comment,
                            time = (visit.orderStartedAt ?: visit.openedAt)?.let { com.example.wfappops.tables.tableActivityTime.format(it) }
                        )
                    }
                }
            }
        }
    }

    state.closureReadiness?.let { ready ->
        var finished by remember(ready) { mutableStateOf(false) }
        WfDialog(title = stringResource(R.string.closure_title),
            message = if (ready.canClose) stringResource(R.string.closure_hint) else ready.blockers
                .map { stringResource(closureBlockerMessage(it)) }.distinct().joinToString("\n")
                .ifBlank { stringResource(R.string.closure_blocked) },
            confirmLabel = stringResource(R.string.closure_action), confirmEnabled = ready.canClose && finished,
            busy = busy, onDismiss = onDismissClosure, onConfirm = onCloseAttention) {
            if (ready.canClose) Row(horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = finished, onCheckedChange = { finished = it }, enabled = !busy)
                Text(stringResource(R.string.closure_confirm_finished))
            }
        }
    }
    fulfillmentDecision?.let { decision ->
        FulfillmentDecision(decision.action, decision.line.product, onDismiss = { fulfillmentDecision = null }) { reason, confirmed ->
            val command = FulfillmentRequest(decision.visit, decision.line, decision.action, reason, confirmed)
            fulfillmentDecision = null
            onFulfillmentCommand(command)
        }
    }
    if (showTablePicker) {
        ApiOrderTableSheet(state, onLoadTables, { showTablePicker = false }, {
            selectingTable = true
            onOpenApiTable(it)
        })
    } else if (showProducts && activeVisit != null) {
        LaunchedEffect(Unit) { onLoadCatalog() }
        OrderProductSheet(
            lines = draft, busy = busy || draftLocked,
            failed = failed && state.errorSource == OrderErrorSource.ADD_PRODUCT, addedProducts = state.addedProducts,
            catalog = catalog, onRetry = onLoadCatalog,
            onDismiss = {
                showProducts = false
                pulseNewDispatch = draft.isEmpty()
                if (state.addedProducts > productCountAtOpening && draft.isNotEmpty()) draftFocusRequest++
            },
            onAdd = onAddProduct,
            refreshing = state.menuLoading,
            errorMessage = if (state.productSelectionChanged) R.string.menu_selection_changed else null,
            operationMessage = state.operationMessage,
            onRefresh = onRefreshCatalog,
            locations = state.menuLocations,
            selectedLocationId = state.menuLocationId,
            onLocation = onMenuLocation.takeIf { activeVisit.remoteTableId == null && !activeVisit.hasRecordedProducts }
        )
    }

    if (showSendChoice && activeVisit != null && draft.isNotEmpty()) {
        WfDialog(title = stringResource(R.string.order_send_draft_title),
            message = stringResource(R.string.order_send_draft_question),
            confirmLabel = stringResource(R.string.order_send_all), dismissLabel = stringResource(R.string.order_select_products),
            inlineActions = true, busy = busy, confirmEnabled = draft.size <= 60,
            onDismiss = { showSendChoice = false },
            onDismissAction = {
                showSendChoice = false; selectDispatch(null); selectingDraft = true; draftMarked = intArrayOf(); draftFocusRequest++
            },
            onConfirm = {
                showSendChoice = false
                onSendSelection(draft.indices.associateWith { draft[it].quantity })
            })
    }
    if (cancellationVersion != null && activeVisit != null) {
        val expectedVersion = checkNotNull(cancellationVersion)
        WfDialog(title = stringResource(R.string.order_cancel_title), confirmLabel = stringResource(R.string.order_cancel_action),
            message = stringResource(R.string.order_cancel_hint), busy = busy,
            confirmEnabled = cancellationReason.trim().length in 3..255,
            confirmStyle = WfButtonStyle.DESTRUCTIVE, onDismiss = { cancellationVersion = null },
            onConfirm = { cancellationVersion = null; onCancelOrder(expectedVersion, cancellationReason) }) {
            OrderNoteField(cancellationReason, { cancellationReason = it.take(255) },
                label = stringResource(R.string.order_cancel_reason), enabled = !busy)
        }
    }
    if (confirmCancelLines && selectedIndex != null && selectedLines.isNotEmpty()) {
        WfDialog(
            title = pluralStringResource(R.plurals.order_cancel_lines_title, selectedLines.size, selectedLines.size),
            message = stringResource(R.string.order_cancel_lines_hint),
            confirmLabel = stringResource(R.string.order_cancel_action),
            confirmEnabled = unitCancellationReason.trim().length in 3..255,
            confirmStyle = WfButtonStyle.DESTRUCTIVE,
            onDismiss = { confirmCancelLines = false },
            busy = busy,
            onConfirm = {
                selectDispatch(null)
                onCancelUnits(selectedIndex, selectedLines, unitCancellationReason, unitCancellationVersion)
            }
        ) {
            OrderNoteField(unitCancellationReason, { unitCancellationReason = it.take(255) }, label = stringResource(R.string.order_cancel_reason), enabled = !busy)
        }
    }

    if (editingComment && activeVisit != null) {
        val commentSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        ModalBottomSheet(
            onDismissRequest = { if (!busy) editingComment = false },
            sheetState = commentSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f)) {
                OrderProductForm(
                    name = stringResource(R.string.order_draft_comment_title), description = null, price = "",
                    note = comment, onNote = { comment = it }, busy = busy,
                    actionLabel = stringResource(R.string.order_save),
                    error = if (failed && state.errorSource == OrderErrorSource.DRAFT_COMMENT)
                        stringResource(R.string.order_comment_save_failed) else null,
                    onAction = { onSaveComment(comment) }
                )
            }
        }
    }

    if (confirmCashier && activeVisit != null) {
        WfDialog(
            title = stringResource(R.string.order_cashier_title),
            message = if (activeVisit.canSendToCashier) {
                stringResource(R.string.order_cashier_hint, formatCartaPrice(activeVisit.totalInCents))
            } else stringResource(R.string.order_cashier_blocked),
            confirmLabel = stringResource(if (activeVisit.canSendToCashier) R.string.order_cashier_send else R.string.tables_confirm),
            onConfirm = { if (activeVisit.canSendToCashier) onSendToCashier() else confirmCashier = false },
            onDismiss = { confirmCashier = false },
            busy = busy
        ) {
            if (failed) ErrorState(stringResource(R.string.tables_storage_error), onRetry = null)
        }
    }

    editingNote?.takeIf { activeVisit != null }?.let { line ->
        val noteSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        val product = catalog?.getOrNull().orEmpty().asSequence().flatMap { it.sections.asSequence() }
            .flatMap { it.products.asSequence() }.firstOrNull { it.id == line.productId }
        ModalBottomSheet(
            onDismissRequest = { if (!busy) editingNote = null },
            sheetState = noteSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f)) {
                OrderProductForm(
                    name = line.product, description = product?.description,
                    price = formatCartaPrice(line.unitPriceInCents, line.currencyCode),
                    note = note, onNote = { note = it }, busy = busy,
                    actionLabel = stringResource(R.string.order_save),
                    error = if (failed && state.errorSource == OrderErrorSource.DRAFT_NOTE)
                        stringResource(R.string.order_note_save_failed) else null,
                    onAction = { onSaveNote(line, note) }
                )
            }
        }
    }
    if (state.kitchenUnavailable) {
        WfDialog(
            title = stringResource(R.string.order_send_unavailable),
            message = stringResource(R.string.order_send_unavailable_hint),
            confirmLabel = stringResource(R.string.tables_confirm),
            onConfirm = onDismissKitchenUnavailable,
            onDismiss = onDismissKitchenUnavailable,
            dismissLabel = null
        )
    }
}
