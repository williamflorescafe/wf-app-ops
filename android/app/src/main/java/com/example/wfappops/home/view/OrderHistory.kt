package com.example.wfappops.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.home.viewmodel.OrderHistoryUiState
import com.example.wfappops.orders.view.ActiveTableDot
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.tables.tableActivityDay
import com.example.wfappops.tables.tableActivityTime
import com.example.wfappops.tables.tableLocalDate
import com.example.wfappops.tables.tableZone
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras
import java.time.LocalDate

@Composable
internal fun OrderHistoryScreen(
    history: OrderHistoryUiState,
    onRetry: () -> Unit,
    canOpenOrder: Boolean,
    onOpenOrder: (RegisteredOrder) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(WfSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right), stringResource(R.string.auth_back),
                    Modifier.rotate(180f)
                )
            }
            Text(stringResource(R.string.profile_order_history), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(WfSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            orderHistoryItems(history, onRetry, canOpenOrder, onOpenOrder)
        }
    }
}

internal fun LazyListScope.orderHistoryItems(
    history: OrderHistoryUiState,
    onRetry: () -> Unit,
    canOpenOrder: Boolean,
    onOpenOrder: (RegisteredOrder) -> Unit,
    limit: Int = Int.MAX_VALUE
) {
    val orders = history.orders
    when {
        history.result == null -> item { CircularProgressIndicator(Modifier.padding(WfSpacing.sm)) }
        orders == null -> item {
            Column {
                Text(stringResource(R.string.home_orders_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.auth_retry)) }
            }
        }
        orders.isEmpty() -> item {
            Text(
                stringResource(R.string.home_orders_empty),
                modifier = Modifier.fillMaxWidth().padding(vertical = WfSpacing.sm),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        else -> orders.asSequence().take(limit).groupBy { it.lastActivityAt.tableLocalDate() }.forEach { (day, group) ->
            item(key = "day-$day") {
                Text(
                    if (day == LocalDate.now(tableZone)) stringResource(R.string.home_orders_today)
                    else tableActivityDay.format(day),
                    modifier = Modifier.padding(top = WfSpacing.sm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(group, key = { it.visitId }) { order ->
                OrderHistoryCard(order, enabled = canOpenOrder, onClick = { onOpenOrder(order) })
            }
        }
    }
}

@Composable
private fun OrderHistoryCard(order: RegisteredOrder, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val status = when (order.status) {
        RegisteredOrderStatus.OPEN -> stringResource(R.string.home_order_open)
        RegisteredOrderStatus.DRAFT -> stringResource(R.string.home_order_draft)
        RegisteredOrderStatus.SENT -> stringResource(R.string.order_sent_status)
        RegisteredOrderStatus.PREPARING -> stringResource(R.string.order_preparing)
        RegisteredOrderStatus.DELIVERED -> stringResource(R.string.order_delivered)
        RegisteredOrderStatus.CLOSED -> stringResource(R.string.order_closed)
        RegisteredOrderStatus.READY -> pluralStringResource(
            R.plurals.home_order_ready, if (order.readyQuantity == 1L) 1 else 2, order.readyQuantity
        )
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(WfSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.md)
        ) {
            Box(Modifier.size(42.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_tables), contentDescription = null, tint = scheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
                ) {
                    Text(stringResource(R.string.tables_number, order.table), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.order_number, order.number),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant
                    )
                    Text(
                        tableActivityTime.format(order.lastActivityAt),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                    if (order.status == RegisteredOrderStatus.READY) ActiveTableDot()
                    if (order.status == RegisteredOrderStatus.PREPARING || order.status == RegisteredOrderStatus.DRAFT) {
                        val indicator = if (order.status == RegisteredOrderStatus.PREPARING) {
                            WfThemeExtras.statusColors.warning.content
                        } else scheme.outline
                        Box(Modifier.size(6.dp).background(indicator, CircleShape))
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
