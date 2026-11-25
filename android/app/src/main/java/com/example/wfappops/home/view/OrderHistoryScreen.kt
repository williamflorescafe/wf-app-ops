package com.example.wfappops.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.wfappops.R
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.tables.tableActivityDay
import com.example.wfappops.tables.tableLocalDate
import com.example.wfappops.ui.components.EmptyState
import com.example.wfappops.ui.components.ErrorState
import com.example.wfappops.ui.components.WfScreenTitle
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.home.model.productsFor
import com.example.wfappops.ui.components.WfFilterChips
import com.example.wfappops.orders.view.OrderSearchField
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras
import java.time.LocalDate

/** Comandas de hoy y ayer, agrupadas por la fecha en que comenzaron. */
@Composable
internal fun OrderHistoryScreen(
    orders: Result<List<RegisteredOrder>>?,
    day: LocalDate,
    onRetry: () -> Unit,
    canOpenOrder: Boolean,
    onOpenOrder: (RegisteredOrder) -> Unit
) {
    val search = rememberTextFieldState()
    val query = search.text.toString().trim()
    var status by rememberSaveable { mutableIntStateOf(0) }
    val activeFilter = HomeOrderFilter.entries[status]
    val listState = key(day, status, query) { rememberLazyListState() }
    val filteredOrders = orders?.map { list -> list.filter { order ->
        val matchesStatus = activeFilter == HomeOrderFilter.ALL || order.productsFor(activeFilter).isNotEmpty()
        order.hasDisplayableProducts && matchesStatus && (query.isEmpty() || order.number.toString().contains(query, true) ||
            (order.tableCode ?: order.tableName ?: order.table.toString()).contains(query, true))
    } }
    Column(Modifier.fillMaxSize().background(WfThemeExtras.operationsBackground)) {
        WfScreenTitle(stringResource(R.string.ops_orders), stringResource(R.string.orders_last_two_days))
        OrderSearchField(search, stringResource(R.string.orders_search),
            Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.sm))
        WfFilterChips(
            labels = listOf(stringResource(R.string.ops_filter_all), stringResource(R.string.order_preparing), stringResource(R.string.order_ready)),
            selectedIndex = status, onSelect = { status = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(WfSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.md)
        ) {
            val dailyOrders = filteredOrders?.getOrNull()
            when {
                orders == null -> item { CircularProgressIndicator(Modifier.padding(WfSpacing.sm)) }
                dailyOrders == null -> item { ErrorState(stringResource(R.string.home_orders_error), onRetry) }
                dailyOrders.isEmpty() -> item {
                    EmptyState(
                        stringResource(R.string.state_empty_orders_title),
                        stringResource(R.string.orders_recent_empty)
                    )
                }
                else -> dailyOrders.groupBy { it.startedAt.tableLocalDate() }.forEach { (orderDate, group) ->
                    item(key = "order-day-$orderDate") {
                        Text(
                            text = when (orderDate) {
                                day -> stringResource(R.string.home_orders_today)
                                day.minusDays(1) -> stringResource(R.string.orders_yesterday)
                                else -> tableActivityDay.format(orderDate)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(group, key = { it.visitId }) { order ->
                        OrderHistoryCard(order, canOpenOrder, onClick = { onOpenOrder(order) }, filter = activeFilter)
                    }
                }
            }
        }
    }
}
