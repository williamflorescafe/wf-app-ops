package com.example.wfappops.orders.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.carta.CartaCollection
import com.example.wfappops.carta.OrderNoteField
import com.example.wfappops.carta.formatCartaPrice
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.serviceTableNumbers
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras
import com.example.wfappops.ui.theme.WfRadius
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class CashierAttention(val request: Int, val cardIndex: Int?, val message: Int)

@Composable
internal fun CreateOrderScreen(
    state: OrderUiState,
    catalog: Result<List<CartaCollection>>?,
    onLoadCatalog: () -> Unit,
    onReload: () -> Unit,
    onOpenTable: (Int) -> Unit,
    onAddProduct: (OrderLine) -> Unit,
    onChangeQuantity: (OrderLine, Int) -> Unit,
    onSaveNote: (OrderLine, String) -> Unit,
    onSaveComment: (String) -> Unit,
    onSendToCashier: () -> Unit,
    onSendOrder: () -> Unit,
    onDeliverLine: (Int, Int) -> Unit,
    onDeliverDispatch: (Int) -> Unit,
    onCancelLines: (Int, Set<Int>) -> Unit,
    onDismissKitchenUnavailable: () -> Unit,
    onCancel: () -> Unit
) {
    val number = state.table
    val visit = state.visit
    val activeVisit = visit?.takeUnless { it.closed }
    val draft = activeVisit?.draft.orEmpty()
    val busy = state.busy
    val failed = state.storageError
    var showProducts by rememberSaveable(number) {
        mutableStateOf(activeVisit != null && draft.isEmpty() && activeVisit.dispatches.isEmpty())
    }
    var pulseNewDispatch by rememberSaveable { mutableStateOf(false) }
    var selectedDispatchIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var markedLines by rememberSaveable { mutableStateOf(intArrayOf()) }
    var confirmCancelLines by rememberSaveable { mutableStateOf(false) }
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
    val selectedIndex = selectedDispatchIndex?.takeIf { visit?.dispatches?.getOrNull(it) != null }
    val selectedDispatch = selectedIndex?.let { visit?.dispatches?.get(it) }
    val canMarkLines = state.simulated && activeVisit != null && selectedDispatch?.canCancelLines == true
    val selectedLines = markedLines.filterTo(mutableSetOf()) { index ->
        canMarkLines && selectedDispatch?.lines?.getOrNull(index)?.canCancel == true
    }

    fun selectDispatch(index: Int?) {
        selectedDispatchIndex = index
        markedLines = intArrayOf()
        confirmCancelLines = false
    }

    val listState = rememberLazyListState()
    val firstDispatchIndex = (if (failed && !showProducts) 1 else 0) +
        (if (visit?.comment != null) 1 else 0) + (if (state.openTables == null && busy) 1 else 0)
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
            onBack = { if (selectedIndex != null) selectDispatch(null) else onCancel() },
            onComment = activeVisit?.let { openVisit ->
                {
                    comment = openVisit.comment.orEmpty()
                    editingComment = true
                }
            },
            cashierBlocked = !canSendToCashier,
            onCashier = activeVisit?.let { openVisit ->
                {
                    if (canSendToCashier) {
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
        if (activeVisit != null && (selectedIndex == null || canMarkLines)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(WfSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
                ) {
                    if (selectedIndex != null) {
                        WfButton(
                            stringResource(R.string.order_cancel_lines),
                            onClick = { confirmCancelLines = true },
                            modifier = Modifier.weight(1f), enabled = selectedLines.isNotEmpty() && !busy,
                            style = WfButtonStyle.DESTRUCTIVE
                        )
                    } else {
                        val pulse = if (pulseNewDispatch && draft.isEmpty() && !showProducts && !busy) {
                            rememberOrderPulse()
                        } else null
                        val halo = MaterialTheme.colorScheme.primary
                        WfButton(
                            stringResource(if (draft.isEmpty()) R.string.order_new_dispatch else R.string.order_add_products),
                            onClick = {
                                pulseNewDispatch = false
                                productCountAtOpening = state.addedProducts
                                showProducts = true
                            },
                            modifier = Modifier.weight(1f).drawBehind {
                                if (pulse != null) {
                                    val progress = pulse.value
                                    val spread = 8.dp.toPx() * progress
                                    drawRoundRect(
                                        color = halo,
                                        topLeft = Offset(-spread, -spread),
                                        size = Size(size.width + spread * 2f, size.height + spread * 2f),
                                        cornerRadius = CornerRadius(WfRadius.md.toPx() + spread),
                                        alpha = 0.5f * (1f - progress)
                                    )
                                }
                            },
                            enabled = !busy,
                            style = if (draft.isEmpty()) WfButtonStyle.PRIMARY else WfButtonStyle.ALTERNATIVE
                        )
                        if (draft.isNotEmpty()) {
                            WfButton(
                                stringResource(R.string.order_send),
                                onClick = onSendOrder,
                                modifier = Modifier.weight(1f),
                                enabled = !busy
                            )
                        }
                    }
                }
            }
        }
    }) {
        if (number != null) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(WfSpacing.md),
                verticalArrangement = Arrangement.spacedBy(WfSpacing.md)
            ) {
                if (failed && !showProducts) item {
                    Text(stringResource(R.string.tables_storage_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onReload) { Text(stringResource(R.string.auth_retry)) }
                }
                if (state.openTables == null && busy) item { CircularProgressIndicator() }
                if (state.openTables != null && visit == null) item {
                    Text(stringResource(R.string.order_table_unavailable))
                }
                if (visit?.closed == true) item {
                    val label = if (visit.cashierHandoff != null) R.string.order_cashier_registered else R.string.order_closed
                    Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                visit?.comment?.let { savedComment ->
                    item(key = "order-comment") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.medium,
                            border = WfThemeExtras.operationsCardBorder
                        ) {
                            Column(Modifier.padding(WfSpacing.md), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                                Text(stringResource(R.string.order_comment_title), style = MaterialTheme.typography.labelLarge)
                                Text(savedComment, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (visit != null) {
                    val canDeliver = state.simulated && activeVisit != null && selectedIndex == null
                    items(visit.dispatches.size, key = { index -> "dispatch-$index" }) { index ->
                        RegisteredDispatchCard(
                            visit.dispatches[index], ordinal = index + 1,
                            selected = selectedIndex == index,
                            busy = busy,
                            attentionRequest = cashierPulseRequest.takeIf { cashierAttention?.cardIndex == index } ?: 0,
                            markedLines = if (selectedIndex == index) selectedLines else emptySet(),
                            onSelect = {
                                if (!busy) selectDispatch(if (selectedIndex == index) null else index)
                            },
                            onToggleLine = if (selectedIndex == index && canMarkLines) {
                                { lineIndex ->
                                    markedLines = (if (lineIndex in selectedLines) selectedLines - lineIndex
                                    else selectedLines + lineIndex).toIntArray()
                                }
                            } else null,
                            onDeliverLine = if (canDeliver) {
                                { lineIndex -> onDeliverLine(index, lineIndex) }
                            } else null,
                            onDeliverDispatch = if (canDeliver) ({ onDeliverDispatch(index) }) else null
                        )
                    }
                    if (draft.isNotEmpty()) item(key = "draft") {
                        OrderDraftCard(
                            ordinal = visit.dispatches.size + 1,
                            lines = draft,
                            busy = busy,
                            expandRequest = draftFocusRequest,
                            attentionRequest = cashierPulseRequest.takeIf { cashierAttention?.cardIndex == visit.dispatches.size } ?: 0,
                            onEditNote = { line ->
                                note = line.note.orEmpty()
                                editingNote = line
                            },
                            onAdjust = onChangeQuantity
                        )
                    }
                }
            }
        }
    }

    if (number == null) {
        OrderTableSheet(
            openTables = state.openTables, busy = busy, failed = failed,
            onRetry = onReload, onDismiss = onCancel, onSelect = onOpenTable
        )
    } else if (showProducts && activeVisit != null) {
        LaunchedEffect(Unit) { onLoadCatalog() }
        OrderProductSheet(
            lines = draft, busy = busy, failed = failed, addedProducts = state.addedProducts,
            catalog = catalog, onRetry = onLoadCatalog,
            onDismiss = {
                showProducts = false
                pulseNewDispatch = draft.isEmpty()
                if (state.addedProducts > productCountAtOpening && draft.isNotEmpty()) draftFocusRequest++
            },
            onAdd = onAddProduct
        )
    }

    if (confirmCancelLines && selectedIndex != null && selectedLines.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmCancelLines = false },
            title = {
                Text(pluralStringResource(R.plurals.order_cancel_lines_title, selectedLines.size, selectedLines.size))
            },
            text = { Text(stringResource(R.string.order_cancel_lines_hint)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        selectDispatch(null)
                        onCancelLines(selectedIndex, selectedLines)
                    }
                ) { Text(stringResource(R.string.order_cancel_lines), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelLines = false }) {
                    Text(stringResource(R.string.tables_cancel))
                }
            }
        )
    }

    if (editingComment && activeVisit != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) editingComment = false },
            title = { Text(stringResource(R.string.order_comment_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                    if (failed) {
                        Text(stringResource(R.string.tables_storage_error), color = MaterialTheme.colorScheme.error)
                    }
                    OrderNoteField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = stringResource(R.string.order_comment_label),
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onSaveComment(comment) }, enabled = !busy) {
                    Text(stringResource(R.string.order_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingComment = false }, enabled = !busy) {
                    Text(stringResource(R.string.tables_cancel))
                }
            }
        )
    }

    if (confirmCashier && activeVisit != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmCashier = false },
            title = { Text(stringResource(R.string.order_cashier_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                    Text(
                        if (activeVisit.canSendToCashier) {
                            stringResource(R.string.order_cashier_hint, formatCartaPrice(activeVisit.totalInCents))
                        } else {
                            stringResource(R.string.order_cashier_blocked)
                        }
                    )
                    if (failed) {
                        Text(stringResource(R.string.tables_storage_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                if (activeVisit.canSendToCashier) {
                    TextButton(onClick = onSendToCashier, enabled = !busy) { Text(stringResource(R.string.order_cashier_send)) }
                } else {
                    TextButton(onClick = { confirmCashier = false }, enabled = !busy) { Text(stringResource(R.string.tables_confirm)) }
                }
            },
            dismissButton = {
                if (activeVisit.canSendToCashier) {
                    TextButton(onClick = { confirmCashier = false }, enabled = !busy) {
                        Text(stringResource(R.string.tables_cancel))
                    }
                }
            }
        )
    }

    editingNote?.takeIf { activeVisit != null }?.let { line ->
        AlertDialog(
            onDismissRequest = { if (!busy) editingNote = null },
            title = { Text(line.product) },
            text = {
                Column {
                    if (failed) {
                        Text(stringResource(R.string.tables_storage_error), color = MaterialTheme.colorScheme.error)
                    }
                    OrderNoteField(
                        value = note,
                        onValueChange = { note = it },
                        label = stringResource(R.string.order_note),
                        enabled = !busy
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSaveNote(line, note) },
                    enabled = !busy
                ) { Text(stringResource(R.string.order_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = null }, enabled = !busy) {
                    Text(stringResource(R.string.tables_cancel))
                }
            }
        )
    }
    if (state.kitchenUnavailable) {
        AlertDialog(
            onDismissRequest = onDismissKitchenUnavailable,
            title = { Text(stringResource(R.string.order_send_unavailable)) },
            text = { Text(stringResource(R.string.order_send_unavailable_hint)) },
            confirmButton = {
                TextButton(onClick = onDismissKitchenUnavailable) {
                    Text(stringResource(R.string.tables_confirm))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderTableSheet(
    openTables: Set<Int>?,
    busy: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val operationsInProgress by rememberUpdatedState(busy)
    val searchField = rememberTextFieldState()
    val query = searchField.text.toString()
    val matchingTables = serviceTableNumbers.filter { number ->
        val tableNumber = number.toString()
        tableNumber.startsWith(query) || tableNumber.padStart(2, '0').startsWith(query)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !operationsInProgress }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        val searchFocus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val window = LocalWindowInfo.current
        LaunchedEffect(sheetState, window) {
            snapshotFlow { sheetState.currentValue == SheetValue.Expanded && window.isWindowFocused }
                .first { it }
            searchFocus.requestFocus()
            keyboard?.show()
        }

        Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg).padding(top = WfSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
            ) {
                Text(stringResource(R.string.tables_choose_table), style = MaterialTheme.typography.titleLarge)
                OrderSearchField(
                    state = searchField,
                    label = stringResource(R.string.tables_search),
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    keyboardType = KeyboardType.Number,
                    inputTransformation = {
                        if (asCharSequence().any { it !in '0'..'9' }) revertAllChanges()
                    }
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
            ) {
                if (failed) item {
                    Text(stringResource(R.string.tables_storage_error), color = MaterialTheme.colorScheme.error)
                    if (openTables == null) TextButton(onClick = onRetry) { Text(stringResource(R.string.auth_retry)) }
                }
                if (openTables == null && busy) item { CircularProgressIndicator() }
                if (openTables != null) {
                    if (matchingTables.isEmpty()) item {
                        Text(stringResource(R.string.tables_search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(matchingTables, key = { it }) { number ->
                        val open = number in openTables
                        Card(
                            onClick = {
                                focusManager.clearFocus()
                                onSelect(number)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget).padding(WfSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.order_table_number, number), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(if (open) R.string.tables_open else R.string.tables_free))
                            }
                        }
                    }
                }
            }
        }
    }
}
