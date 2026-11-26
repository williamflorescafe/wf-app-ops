package com.example.wfappops.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.auth.viewmodel.AccountUiState
import com.example.wfappops.ui.components.EmptyState
import com.example.wfappops.ui.components.ErrorState
import com.example.wfappops.ui.components.WfRefreshContainer
import com.example.wfappops.ui.components.WfScreenTitle
import com.example.wfappops.ui.components.WfFilterChips
import com.example.wfappops.ui.components.WfDropdownField
import com.example.wfappops.orders.view.OrderSearchField
import com.example.wfappops.ui.theme.WfSpacing

/** Distribución del API con las atenciones conocidas por este equipo. */
@Composable
internal fun OpsTablesScreen(
    state: AccountUiState,
    attentions: Map<TableReference, TableAttention> = emptyMap(),
    onSelectTable: ((String, String, TableAttention?) -> Unit)? = null,
    occupancyReady: Boolean = true,
    occupancyLoading: Boolean = false,
    onLoad: (String, Boolean) -> Unit
) {
    val context = state.businessContext ?: return
    val permitted = "pos.tables.view" in context.permissions
    val locations = context.locations.filter { Regex("[1-9][0-9]*").matches(it.id) }
    val selected = locations.firstOrNull { it.id == state.selectedLocationId } ?: locations.firstOrNull()
    LaunchedEffect(context, selected?.id) {
        if (permitted && selected != null) onLoad(selected.id, false)
    }
    val search = rememberTextFieldState()
    val query = search.text.toString().trim()
    var selectedAreaId by rememberSaveable(selected?.id) { mutableStateOf<String?>(null) }
    var occupancy by rememberSaveable { mutableIntStateOf(0) }
    val result = state.tables.takeIf { selected?.id == state.selectedLocationId }
    val layout = result?.getOrNull()
    val selectedArea = layout?.areas?.firstOrNull { it.id == selectedAreaId } ?: layout?.areas?.firstOrNull()
    val searchedAreas = layout?.filterAreas(selectedArea?.id, query).orEmpty()
    fun attention(table: AvailableTable) = selected?.id?.let { attentions[TableReference(it, table.id)] } ?: TableAttention.Free
    val areas = searchedAreas.map { area -> area.copy(tables = area.tables.filter { table ->
        when (occupancy) {
            1 -> attention(table) == TableAttention.Free
            2 -> attention(table) is TableAttention.Occupied
            else -> true
        }
    }) }.filter { it.tables.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        WfScreenTitle(stringResource(R.string.tables_title))
        if (permitted && locations.isNotEmpty()) {
            OrderSearchField(search, stringResource(R.string.tables_search_name_code),
                Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.sm))
            if (locations.size > 1) {
                WfDropdownField(
                    label = stringResource(R.string.tables_location), selectedLabel = selected?.name.orEmpty(),
                    options = locations.map { it.id to it.name }, onSelect = { onLoad(it, false) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.sm)
                )
            }
            WfDropdownField(
                label = stringResource(R.string.tables_area),
                selectedLabel = selectedArea?.name ?: stringResource(R.string.tables_no_areas),
                options = layout?.areas?.map { it.id to it.name }.orEmpty(), onSelect = { selectedAreaId = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.sm)
            )
            WfFilterChips(
                labels = listOf(stringResource(R.string.ops_filter_all), stringResource(R.string.tables_filter_free), stringResource(R.string.tables_filter_occupied)),
                selectedIndex = occupancy, onSelect = { occupancy = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg)
            )
        }
        val apiError = result?.exceptionOrNull() as? AuthApiException
        val canRefresh = permitted && selected != null && apiError?.status !in listOf(403, 404, 409)
        WfRefreshContainer(
            isRefreshing = state.tablesLoading,
            onRefresh = { if (!state.tablesLoading && selected != null) onLoad(selected.id, true) },
            enabled = canRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyVerticalGrid(columns = GridCells.Adaptive(156.dp), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(WfSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(WfSpacing.md),
                verticalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
                when {
                    !permitted -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(stringResource(R.string.tables_permission_denied), null) }
                    selected == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(stringResource(R.string.tables_title), stringResource(R.string.tables_no_locations))
                    }
                    result == null -> Unit
                    result.isFailure -> item(span = { GridItemSpan(maxLineSpan) }) {
                        val error = result.exceptionOrNull()
                        val apiError = error as? AuthApiException
                        val fallback = when (apiError?.status) {
                            403 -> R.string.tables_permission_denied
                            404 -> R.string.tables_location_unavailable
                            409 -> R.string.tables_location_inactive
                            429 -> R.string.auth_rate_limit
                            else -> R.string.auth_request_error
                        }
                        ErrorState(apiError?.serverMessage ?: stringResource(fallback),
                            if (apiError?.status in listOf(403, 404, 409)) null else { { onLoad(selected.id, true) } })
                    }
                    !occupancyReady -> item(span = { GridItemSpan(maxLineSpan) }) {
                        if (occupancyLoading) androidx.compose.material3.CircularProgressIndicator()
                        else ErrorState(stringResource(R.string.orders_occupancy_error), { onLoad(selected.id, true) })
                    }
                    areas.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(stringResource(R.string.tables_title),
                            stringResource(if (query.isEmpty()) R.string.tables_empty else R.string.tables_search_empty))
                    }
                    else -> areas.forEach { area ->
                        items(area.tables, key = { "table:${it.id}" }) { table ->
                            val service = attention(table)
                            val select = onSelectTable?.let { callback -> { callback(selected.id, table.id, service) } }
                            OpsTableCard(table, modifier = Modifier.fillMaxWidth(), attention = service,
                                onSelect = select.takeIf { service is TableAttention.Occupied },
                                onAdd = select.takeIf { service == TableAttention.Free })
                        }
                    }
                }
            }
        }
    }
}
