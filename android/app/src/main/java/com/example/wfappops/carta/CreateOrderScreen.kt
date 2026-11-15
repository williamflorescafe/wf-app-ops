package com.example.wfappops.carta

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.auth.ActiveOpsAccess
import com.example.wfappops.cart.hasCartAccess
import com.example.wfappops.tables.OrderLine
import com.example.wfappops.tables.TableService
import com.example.wfappops.tables.TableStore
import com.example.wfappops.tables.TableWorker
import com.example.wfappops.tables.addCartLine
import com.example.wfappops.tables.adjustCartLine
import com.example.wfappops.tables.editNote
import com.example.wfappops.tables.readLine
import com.example.wfappops.tables.serviceTableNumbers
import com.example.wfappops.tables.toJson
import com.example.wfappops.ui.components.OpsScaffold
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfRadius
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private val draftSaver = Saver<List<OrderLine>?, String>(
    save = { lines -> lines?.let { JSONArray(it.map(OrderLine::toJson)).toString() } },
    restore = { encoded ->
        val lines = JSONArray(encoded)
        List(lines.length()) { readLine(lines.getJSONObject(it)) }
    }
)

@Composable
internal fun CreateOrderScreen(
    storageId: String,
    worker: TableWorker,
    initialTable: Int?,
    initialVisitId: String?,
    onCancel: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context, storageId) { TableStore(context, storageId) }
    val scope = rememberCoroutineScope()
    var service by remember { mutableStateOf<TableService?>(null) }
    var table by rememberSaveable { mutableStateOf(initialTable) }
    var visitId by rememberSaveable { mutableStateOf(initialVisitId) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var showProducts by rememberSaveable { mutableStateOf(false) }
    var pulseNewDispatch by rememberSaveable { mutableStateOf(false) }
    var selectedDispatchIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmDeleteDispatch by rememberSaveable { mutableStateOf(false) }
    var draftFocusRequest by rememberSaveable { mutableStateOf(0) }
    var addedInProductSheet by rememberSaveable { mutableStateOf(false) }
    var editedDraft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf<List<OrderLine>?>(null) }

    fun update(change: ((TableService) -> TableService)? = null, onSuccess: (TableService) -> Unit = {}) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(context.hasCartAccess(storageId))
                    if (change == null) store.read() else store.update(change)
                }
            }
            busy = false
            result.onSuccess {
                service = it
                onSuccess(it)
            }.onFailure { failed = true }
        }
    }

    LaunchedEffect(store) { update() }
    BackHandler(enabled = busy) {}
    val number = table
    val visit = service?.visits?.firstOrNull { it.id == visitId && it.table == number }
    val activeVisit = visit?.takeUnless { it.closed }
    val draft = if (activeVisit == null) emptyList() else editedDraft ?: activeVisit.draft
    val simulated = ActiveOpsAccess.current?.simulated == true
    val selectedIndex = selectedDispatchIndex?.takeIf { visit?.dispatches?.getOrNull(it) != null }
    val canDeleteDispatch = simulated && activeVisit != null && selectedIndex != null &&
        activeVisit.dispatches[selectedIndex].canceledAt == null
    val listState = rememberLazyListState()
    val draftIndex = (visit?.dispatches?.size ?: 0) + if (failed && !showProducts) 1 else 0
    LaunchedEffect(draftFocusRequest) {
        if (draftFocusRequest > 0 && draft.isNotEmpty() && !showProducts) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > draftIndex }
            listState.animateScrollToItem(draftIndex)
        }
    }
    var editingNote by remember { mutableStateOf<OrderLine?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var showKitchenUnavailable by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selectedIndex != null && !busy) { selectedDispatchIndex = null }

    OpsScaffold(topBar = {
        OrderTableHeader(number, visit, enabled = !busy, onBack = {
            if (selectedIndex != null) selectedDispatchIndex = null else onCancel()
        })
    }, bottomBar = {
        if (activeVisit != null || selectedIndex != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(WfSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
                ) {
                    if (selectedIndex != null) {
                        WfButton(
                            stringResource(R.string.order_edit_dispatch), onClick = {},
                            modifier = Modifier.weight(1f), enabled = false,
                            style = WfButtonStyle.BACK
                        )
                        WfButton(
                            stringResource(R.string.order_delete_dispatch), onClick = { confirmDeleteDispatch = true },
                            modifier = Modifier.weight(1f), enabled = canDeleteDispatch && !busy,
                            style = WfButtonStyle.DESTRUCTIVE
                        )
                    } else if (activeVisit != null) {
                        val pulse = if (pulseNewDispatch && draft.isEmpty() && !showProducts && !busy) {
                            rememberOrderPulse()
                        } else null
                        val halo = MaterialTheme.colorScheme.primary
                        WfButton(
                            stringResource(if (draft.isEmpty()) R.string.order_new_dispatch else R.string.order_add_products),
                            onClick = {
                                pulseNewDispatch = false
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
                                onClick = {
                                    if (simulated) {
                                        update(change = {
                                            check(it.openVisit(activeVisit.table)?.id == activeVisit.id)
                                            it.replaceDraft(activeVisit.id, expected = activeVisit.draft, lines = draft)
                                                .sendLocally(activeVisit.id, worker, Instant.now())
                                        }, onSuccess = { editedDraft = null })
                                    } else {
                                        showKitchenUnavailable = true
                                    }
                                },
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
                    TextButton(onClick = { update() }) { Text(stringResource(R.string.auth_retry)) }
                }
                if (service == null && busy) item { CircularProgressIndicator() }
                if (service != null && visit == null) item {
                    Text(stringResource(R.string.order_table_unavailable))
                }
                if (visit?.closed == true) item {
                    Text(stringResource(R.string.order_closed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (visit != null) {
                    items(visit.dispatches.size, key = { index -> "dispatch-$index" }) { index ->
                        RegisteredDispatchCard(
                            visit.dispatches[index], ordinal = index + 1,
                            selected = selectedIndex == index,
                            onLongPress = {
                                if (!busy) selectedDispatchIndex = if (selectedIndex == index) null else index
                            }
                        )
                    }
                    if (draft.isNotEmpty()) item(key = "draft") {
                        OrderDraftCard(
                            ordinal = visit.dispatches.size + 1,
                            lines = draft,
                            busy = busy,
                            expandRequest = draftFocusRequest,
                            onEditNote = { line ->
                                note = line.note.orEmpty()
                                editingNote = line
                            },
                            onAdjust = { line, delta ->
                                if (!busy && activeVisit != null) editedDraft = draft.adjustCartLine(line, delta)
                            }
                        )
                    }
                }
            }
        }
    }

    if (number == null) {
        OrderTableSheet(service, busy, failed, onRetry = { update() }, onDismiss = { if (!busy) onCancel() }) { selected ->
            update(change = { it.open(selected, worker) }) {
                val selectedVisit = checkNotNull(it.openVisit(selected))
                visitId = selectedVisit.id
                table = selected
                showProducts = selectedVisit.draft.isEmpty() && selectedVisit.dispatches.isEmpty()
            }
        }
    } else if (showProducts && activeVisit != null) {
        OrderProductSheet(
            lines = draft, busy = busy, failed = failed,
            onDismiss = {
                showProducts = false
                pulseNewDispatch = draft.isEmpty()
                if (addedInProductSheet && draft.isNotEmpty()) draftFocusRequest++
                addedInProductSheet = false
            },
            onAdd = { line, onAdded ->
                if (!busy) {
                    editedDraft = (editedDraft ?: activeVisit.draft).addCartLine(line)
                    addedInProductSheet = true
                    onAdded()
                }
            }
        )
    }

    if (confirmDeleteDispatch && selectedIndex != null && activeVisit != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDeleteDispatch = false },
            title = { Text(stringResource(R.string.order_delete_dispatch_title, selectedIndex + 1)) },
            text = { Text(stringResource(R.string.order_delete_dispatch_hint)) },
            confirmButton = {
                TextButton(
                    enabled = canDeleteDispatch && !busy,
                    onClick = {
                        confirmDeleteDispatch = false
                        update(change = {
                            it.cancelLocalDispatch(activeVisit.id, selectedIndex, worker, Instant.now())
                        }, onSuccess = { selectedDispatchIndex = null })
                    }
                ) { Text(stringResource(R.string.order_delete_dispatch), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDispatch = false }) {
                    Text(stringResource(R.string.tables_cancel))
                }
            }
        )
    }

    editingNote?.takeIf { activeVisit != null }?.let { line ->
        AlertDialog(
            onDismissRequest = { if (!busy) editingNote = null },
            title = { Text(line.product) },
            text = {
                OrderNoteField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.order_note),
                    enabled = !busy
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activeVisit != null && !busy) {
                            editedDraft = draft.editNote(line, note.trim().ifEmpty { null })
                            editingNote = null
                        }
                    },
                    enabled = !busy && activeVisit != null
                ) { Text(stringResource(R.string.order_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = null }, enabled = !busy) {
                    Text(stringResource(R.string.tables_cancel))
                }
            }
        )
    }
    if (showKitchenUnavailable) {
        AlertDialog(
            onDismissRequest = { showKitchenUnavailable = false },
            title = { Text(stringResource(R.string.order_send_unavailable)) },
            text = { Text(stringResource(R.string.order_send_unavailable_hint)) },
            confirmButton = {
                TextButton(onClick = { showKitchenUnavailable = false }) {
                    Text(stringResource(R.string.tables_confirm))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderTableSheet(
    service: TableService?,
    busy: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !busy }
        )
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(0.72f),
            contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            item { Text(stringResource(R.string.tables_choose_table), style = MaterialTheme.typography.titleLarge) }
            if (failed) item {
                Text(stringResource(R.string.tables_storage_error), color = MaterialTheme.colorScheme.error)
                if (service == null) TextButton(onClick = onRetry) { Text(stringResource(R.string.auth_retry)) }
            }
            if (service == null && busy) item { CircularProgressIndicator() }
            if (service != null) items(serviceTableNumbers.toList()) { number ->
                val open = service.openVisit(number) != null
                Card(
                    onClick = { onSelect(number) }, enabled = !busy,
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
