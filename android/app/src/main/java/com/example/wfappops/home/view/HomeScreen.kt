package com.example.wfappops.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wfappops.R
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.ui.components.WfFilterChips
import com.example.wfappops.ui.components.WfTitleText
import com.example.wfappops.ui.theme.WfSpacing

private val nameSpacing = Regex("\\s+")

@Composable
internal fun HomeScreen(
    employeeName: String,
    canOpenOrder: Boolean,
    onCreateOrder: () -> Unit,
    openOrders: Result<List<RegisteredOrder>>?,
    filter: HomeOrderFilter,
    onFilter: (HomeOrderFilter) -> Unit,
    onRetry: () -> Unit,
    onOpenOrder: (RegisteredOrder) -> Unit,
    ordersAvailable: Boolean = true,
    onNotifications: () -> Unit = {},
    refreshWarning: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val displayName = remember(employeeName) {
        employeeName.substringBefore('·').trim().replace(nameSpacing, " ")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(scheme.background),
        contentPadding = PaddingValues(start = WfSpacing.lg, top = WfSpacing.xs,
            end = WfSpacing.lg, bottom = WfSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                Text(stringResource(R.string.home_greeting, displayName), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 20.sp, stepSize = 0.5.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onNotifications, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_notifications), stringResource(R.string.home_notifications),
                        modifier = Modifier.size(24.dp), tint = scheme.primary)
                }
            }
        }
        item {
            CreateOrderCard(enabled = canOpenOrder, onClick = onCreateOrder)
        }
        item {
            WfTitleText(
                stringResource(R.string.home_my_orders),
                modifier = Modifier.padding(top = WfSpacing.sm)
            )
        }
        item(key = "order-filters") {
            WfFilterChips(
                labels = HomeOrderFilter.entries.map { option ->
                    stringResource(when (option) {
                        HomeOrderFilter.ALL -> R.string.home_filter_all
                        HomeOrderFilter.PREPARING -> R.string.order_preparing
                        HomeOrderFilter.READY -> R.string.order_ready
                    })
                },
                selectedIndex = filter.ordinal,
                onSelect = { onFilter(HomeOrderFilter.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (refreshWarning) item {
            com.example.wfappops.ui.components.InlineMessage(stringResource(R.string.orders_cached_warning),
                com.example.wfappops.ui.theme.StatusTone.WARNING)
        }
        if (!ordersAvailable) item {
            com.example.wfappops.ui.components.EmptyState(
                stringResource(R.string.home_my_orders), stringResource(R.string.operations_unavailable)
            )
        } else orderHistoryItems(
            openOrders, onRetry, canOpenOrder, onOpenOrder,
            emptyMessage = when (filter) {
                HomeOrderFilter.ALL -> R.string.home_open_orders_empty
                HomeOrderFilter.PREPARING -> R.string.home_preparing_empty
                HomeOrderFilter.READY -> R.string.home_ready_empty
            },
            filter = filter
        )
    }
}
