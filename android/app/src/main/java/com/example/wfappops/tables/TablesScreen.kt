package com.example.wfappops.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.example.wfappops.R
import com.example.wfappops.orders.model.ServiceTable
import com.example.wfappops.ui.components.StatusChip
import com.example.wfappops.ui.components.ErrorState
import com.example.wfappops.ui.components.EmptyState
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing

@Composable
internal fun TablesScreen(
    result: Result<List<ServiceTable>>?,
    onRetry: () -> Unit,
    canOpenTable: Boolean,
    onOpenTable: (ServiceTable) -> Unit
) {
    val tables = result?.getOrNull()
    val search = rememberTextFieldState()
    val query = search.text.toString()
    val matchingTables = tables?.filter { it.number.matchesTableSearch(query) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    LaunchedEffect(query) { listState.scrollToItem(0) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.tables_title),
            modifier = Modifier.padding(start = WfSpacing.lg, top = WfSpacing.xl, end = WfSpacing.lg),
            style = MaterialTheme.typography.titleLarge
        )
        TableSearchField(
            state = search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.md)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.md),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            when {
                result == null -> item { CircularProgressIndicator(Modifier.padding(WfSpacing.sm)) }
                tables == null -> item {
                    ErrorState(stringResource(R.string.tables_storage_error), onRetry)
                }
                matchingTables.isNullOrEmpty() -> item {
                    EmptyState(
                        stringResource(R.string.tables_title),
                        stringResource(if (tables.isEmpty()) R.string.tables_empty else R.string.tables_search_empty)
                    )
                }
                else -> items(matchingTables, key = { it.number }) { table ->
                    ServiceTableCard(table, enabled = canOpenTable) {
                        focus.clearFocus()
                        onOpenTable(table)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceTableCard(table: ServiceTable, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val opened = table.visitId != null
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget).padding(WfSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
                ) {
                    Text(stringResource(R.string.order_table_number, table.number), style = MaterialTheme.typography.titleSmall)
                    table.orderNumber?.let { number ->
                        Text(stringResource(R.string.order_number, number), style = MaterialTheme.typography.labelSmall)
                    }
                }
                val details = listOfNotNull(table.responsible?.name, table.openedAt?.let(tableActivityTime::format))
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            StatusChip(
                label = stringResource(if (opened) R.string.tables_open else R.string.tables_free),
                tone = if (opened) StatusTone.POSITIVE else StatusTone.NEUTRAL
            )
        }
    }
}
