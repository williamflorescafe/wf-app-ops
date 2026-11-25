package com.example.wfappops.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.ui.components.WfFilterChips
import com.example.wfappops.ui.components.WfTitleText
import com.example.wfappops.ui.theme.WfThemeExtras
import com.example.wfappops.ui.theme.WfSpacing

private val nameSpacing = Regex("\\s+")

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    notificationOrders: Result<List<RegisteredOrder>>? = openOrders
) {
    val scheme = MaterialTheme.colorScheme
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    val shortName = remember(employeeName) {
        employeeName.substringBefore('·').trim().split(nameSpacing, limit = 3).take(2).joinToString(" ")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(scheme.background),
        contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WfTitleText(
                stringResource(R.string.home_greeting, shortName),
                modifier = Modifier.weight(1f).padding(bottom = WfSpacing.sm)
            )
            IconButton(onClick = { showNotifications = true }) {
                Icon(painterResource(R.drawable.ic_notifications), stringResource(R.string.home_notifications), tint = scheme.primary)
            }
            }
        }
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(WfSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WfSpacing.md)
                ) {
                    Box(
                        Modifier.size(56.dp).background(WfThemeExtras.warmSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_order_bag), contentDescription = null,
                            modifier = Modifier.size(26.dp), tint = scheme.primary
                        )
                    }
                    Button(
                        onClick = onCreateOrder,
                        enabled = canOpenOrder,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary, contentColor = WfThemeExtras.onAdvanceAction,
                            disabledContainerColor = scheme.outlineVariant,
                            disabledContentColor = scheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.home_create_order))
                    }
                }
            }
        }
        item {
            WfTitleText(
                stringResource(R.string.home_my_orders),
                modifier = Modifier.padding(top = WfSpacing.lg, bottom = WfSpacing.xs)
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
    if (showNotifications) ModalBottomSheet(onDismissRequest = { showNotifications = false }) {
        LazyColumn(contentPadding = PaddingValues(WfSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
            item { WfTitleText(stringResource(R.string.home_notifications)) }
            orderHistoryItems(notificationOrders, onRetry, canOpenOrder, {
                showNotifications = false
                onOpenOrder(it)
            }, emptyMessage = R.string.home_notifications_empty, filter = HomeOrderFilter.READY)
        }
    }
}
