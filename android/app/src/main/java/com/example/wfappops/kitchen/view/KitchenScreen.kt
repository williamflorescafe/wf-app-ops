package com.example.wfappops.kitchen.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wfappops.R
import com.example.wfappops.kitchen.viewmodel.KitchenUiState
import com.example.wfappops.kitchen.viewmodel.KitchenViewModel
import com.example.wfappops.orders.model.KitchenDispatch
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.KitchenLine
import com.example.wfappops.orders.view.KitchenStatusLabel
import com.example.wfappops.tables.tableZone
import com.example.wfappops.ui.components.WfSegmentedControl
import com.example.wfappops.ui.theme.WfThemeExtras
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing
import java.time.format.DateTimeFormatter

private val kitchenTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(tableZone)

@Composable
internal fun KitchenRoute(viewModel: KitchenViewModel, storageId: String, employeeId: String, simulated: Boolean) {
    val current by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(storageId, simulated) { if (simulated) viewModel.load(storageId) }
    val state = current.takeIf { simulated && it.storageId == storageId } ?: KitchenUiState()
    KitchenScreen(
        state, employeeId, simulated,
        onFilter = viewModel::filterByOwner,
        onRetry = { viewModel.load(storageId, retry = true) },
        onAdvance = viewModel::advance
    )
}

@Composable
private fun KitchenScreen(
    state: KitchenUiState,
    employeeId: String,
    simulated: Boolean,
    onFilter: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onAdvance: (KitchenLine) -> Unit
) {
    val result = state.result
    val queue = result?.getOrNull()?.let {
        if (state.onlyMine) it.forEmployee(employeeId) else it
    }
    var confirmedExpanded by rememberSaveable(state.storageId) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = WfSpacing.lg, top = WfSpacing.xl, end = WfSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.ops_kitchen), style = MaterialTheme.typography.titleLarge)
            if (simulated) {
                WfSegmentedControl(
                    labels = listOf(stringResource(R.string.kitchen_filter_mine), stringResource(R.string.kitchen_filter_all)),
                    selectedIndex = if (state.onlyMine) 0 else 1,
                    onSelect = { onFilter(it == 0) },
                    modifier = Modifier.padding(start = WfSpacing.sm).weight(1f, fill = false).widthIn(max = 220.dp)
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.md),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.md)
        ) {
            if (state.saveFailed) item {
                Text(stringResource(R.string.kitchen_save_error), color = MaterialTheme.colorScheme.error)
            }
            when {
                !simulated -> item { Text(stringResource(R.string.kitchen_unavailable)) }
                result == null -> item { CircularProgressIndicator(Modifier.padding(WfSpacing.sm)) }
                queue == null -> item {
                    Column {
                        Text(stringResource(R.string.kitchen_load_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.auth_retry)) }
                    }
                }
                else -> {
                    item(key = "in-progress") {
                        Text(
                            stringResource(R.string.kitchen_in_progress),
                            modifier = Modifier.padding(top = WfSpacing.sm),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (queue.inProgress.isEmpty()) item {
                        KitchenEmptyMessage(if (state.onlyMine) R.string.kitchen_mine_empty else R.string.kitchen_empty)
                    }
                    items(queue.inProgress, key = { "pending-${it.visitId}-${it.ordinal}" }) { dispatch ->
                        KitchenDispatchCard(dispatch, !state.saving, onAdvance)
                    }
                    item(key = "confirmed-heading") {
                        val quantity = queue.confirmed.sumOf { dispatch ->
                            dispatch.lines.sumOf { it.line.quantity.toLong() }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget).clickable(
                                onClickLabel = stringResource(
                                    if (confirmedExpanded) R.string.kitchen_collapse_confirmed else R.string.kitchen_expand_confirmed
                                ),
                                role = Role.Button,
                                onClick = { confirmedExpanded = !confirmedExpanded }
                            ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.kitchen_confirmed_count, quantity), style = MaterialTheme.typography.labelLarge)
                            Icon(
                                painterResource(R.drawable.ic_chevron_right), contentDescription = null,
                                modifier = Modifier.size(18.dp).rotate(if (confirmedExpanded) -90f else 90f)
                            )
                        }
                    }
                    if (confirmedExpanded) {
                        if (queue.confirmed.isEmpty()) item { KitchenEmptyMessage(R.string.kitchen_confirmed_empty) }
                        items(queue.confirmed, key = { "confirmed-${it.visitId}-${it.ordinal}" }) { dispatch ->
                            KitchenDispatchCard(dispatch, enabled = false, onAdvance = onAdvance)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KitchenEmptyMessage(label: Int) {
    Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun KitchenDispatchCard(
    dispatch: KitchenDispatch,
    enabled: Boolean,
    onAdvance: (KitchenLine) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val ordinal = stringResource(R.string.order_dispatch, dispatch.ordinal)
    val reference = if (dispatch.orderNumber != null) {
        val number = stringResource(R.string.order_number, dispatch.orderNumber)
        stringResource(R.string.kitchen_dispatch_reference, number, ordinal)
    } else {
        stringResource(R.string.kitchen_dispatch_number, ordinal)
    }

    Card(
        border = WfThemeExtras.operationsCardBorder,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(WfSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                    Text(stringResource(R.string.order_table_number, dispatch.table), style = MaterialTheme.typography.titleMedium)
                    Text(reference, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    KitchenTimestamp(R.string.kitchen_sent_at, kitchenTime.format(dispatch.sentAt))
                    dispatch.receivedAt?.let { KitchenTimestamp(R.string.kitchen_received_at, kitchenTime.format(it)) }
                }
            }
            dispatch.lines.forEach { item ->
                HorizontalDivider(color = scheme.outlineVariant)
                KitchenProduct(item, enabled) { onAdvance(item) }
            }
        }
    }
}

@Composable
private fun KitchenProduct(item: KitchenLine, enabled: Boolean, onAdvance: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val line = item.line
    val status = line.dispatchStatus
    val action = when (status) {
        KitchenItemStatus.SENT -> stringResource(R.string.kitchen_start_preparation)
        KitchenItemStatus.IN_PREPARATION -> stringResource(R.string.kitchen_mark_ready)
        else -> null
    }
    val interaction = if (action == null) Modifier else Modifier.clickable(
        enabled = enabled, onClickLabel = action, role = Role.Button, onClick = onAdvance
    )
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget).then(interaction),
        horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
            Text(stringResource(R.string.kitchen_quantity, line.quantity, line.product), style = MaterialTheme.typography.titleSmall)
            if (line.options.isNotEmpty()) {
                Text(line.options.joinToString(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            line.note?.let { note ->
                Text(stringResource(R.string.order_note_text, note), style = MaterialTheme.typography.bodySmall)
            }
            line.preparationStartedAt?.let { KitchenTimestamp(R.string.kitchen_preparation_at, kitchenTime.format(it)) }
            line.readyAt?.let { KitchenTimestamp(R.string.kitchen_ready_at, kitchenTime.format(it)) }
        }
        KitchenStatusLabel(status)
    }
}

@Composable
private fun KitchenTimestamp(label: Int, time: String) {
    Text(
        stringResource(label, time),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
