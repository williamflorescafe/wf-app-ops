package com.example.wfappops.orders.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.orders.viewmodel.OrderUiState
import com.example.wfappops.tables.OpsTableCard
import com.example.wfappops.tables.TableReference
import com.example.wfappops.tables.TableAttention
import com.example.wfappops.ui.components.EmptyState
import com.example.wfappops.ui.components.ErrorState
import com.example.wfappops.ui.components.WfDropdownField
import com.example.wfappops.ui.components.WfRefreshContainer
import com.example.wfappops.ui.theme.WfSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiOrderTableSheet(
    state: OrderUiState,
    onLoad: (String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val search = rememberTextFieldState()
    val query = search.text.toString().trim()
    val layout = state.apiTables?.getOrNull()
    ModalBottomSheet(
        onDismissRequest = { if (!state.busy && !state.tablesLoading) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { target -> target != SheetValue.Hidden || !state.busy && !state.tablesLoading })
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = WfSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
            Text(stringResource(R.string.tables_choose_table), style = MaterialTheme.typography.titleLarge)
            if (state.locations.size > 1) WfDropdownField(
                label = stringResource(R.string.tables_location),
                selectedLabel = state.locations.firstOrNull { it.id == state.selectedLocationId }?.name.orEmpty(),
                options = state.locations.map { it.id to it.name },
                onSelect = { if (!state.busy && !state.tablesLoading) onLoad(it, false) },
                modifier = Modifier.fillMaxWidth()
            )
            OrderSearchField(search, stringResource(R.string.tables_search_name_code), Modifier.fillMaxWidth())
            WfRefreshContainer(
                isRefreshing = state.tablesLoading,
                onRefresh = { onLoad(state.selectedLocationId, true) },
                enabled = !state.busy && state.locations.isNotEmpty(),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyVerticalGrid(columns = GridCells.Adaptive(156.dp), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = WfSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
                    when {
                        state.tablesLoading && layout == null -> Unit
                        state.apiTables?.isFailure == true -> item(span = { GridItemSpan(maxLineSpan) }) {
                            ErrorState(stringResource(R.string.tables_load_error), { onLoad(state.selectedLocationId, true) })
                        }
                        layout == null || layout.areas.all { it.tables.isEmpty() } -> item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(stringResource(R.string.tables_title), stringResource(R.string.tables_empty))
                        }
                        else -> {
                            val areas = layout.areas.map { area -> area.copy(tables = area.tables.filter {
                                query.isEmpty() || it.name.contains(query, true) || it.code.contains(query, true) || area.name.contains(query, true)
                            }) }.filter { it.tables.isNotEmpty() }
                            if (areas.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.tables_search_empty)) }
                            areas.forEach { area ->
                                item(key = "area:${area.id}", span = { GridItemSpan(maxLineSpan) }) { Text(area.name, style = MaterialTheme.typography.titleMedium) }
                                items(area.tables, key = { "table:${it.id}" }) { table ->
                                    OpsTableCard(table, modifier = Modifier.fillMaxWidth(),
                                        attention = state.attentions[TableReference(layout.location.id, table.id)] ?: TableAttention.Free,
                                        enabled = !state.busy && !state.tablesLoading &&
                                            (state.visit == null || state.attentions[TableReference(layout.location.id, table.id)]
                                                .let { it !is TableAttention.Occupied || it.visitId == state.visit.id }),
                                        onSelect = { onSelect(table.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
