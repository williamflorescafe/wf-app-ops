package com.example.wfappops.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.wfappops.home.model.HomeOrderFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.orders.view.ActiveTableDot
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.tables.tableActivityDay
import com.example.wfappops.tables.tableActivityTime
import com.example.wfappops.tables.tableLocalDate
import com.example.wfappops.tables.tableZone
import com.example.wfappops.ui.components.EmptyState
import com.example.wfappops.ui.components.ErrorState
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras
import java.time.LocalDate
import java.time.Instant

internal fun LazyListScope.orderHistoryItems(
    result: Result<List<RegisteredOrder>>?,
    onRetry: () -> Unit,
    canOpenOrder: Boolean,
    onOpenOrder: (RegisteredOrder) -> Unit,
    emptyMessage: Int,
    limit: Int = Int.MAX_VALUE,
    filter: HomeOrderFilter = HomeOrderFilter.ALL
) {
    val orders = result?.getOrNull()
    when {
        result == null -> item { CircularProgressIndicator(Modifier.padding(WfSpacing.sm)) }
        orders == null -> item {
            ErrorState(stringResource(R.string.home_orders_error), onRetry)
        }
        orders.isEmpty() -> item {
            EmptyState(stringResource(R.string.state_empty_orders_title), stringResource(emptyMessage))
        }
        else -> orders.asSequence().take(limit).groupBy { order ->
            order.lastActivityAt.tableLocalDate()
        }.forEach { (day, group) ->
            item(key = "day-$day") {
                Text(
                    when (day) {
                        LocalDate.now(tableZone) -> stringResource(R.string.home_orders_today)
                        else -> tableActivityDay.format(day)
                    },
                    modifier = Modifier.padding(top = WfSpacing.sm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(group, key = { it.visitId }) { order ->
                OrderHistoryCard(order, enabled = canOpenOrder, onClick = { onOpenOrder(order) }, filter = filter,
                    modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
internal fun OrderHistoryCard(
    order: RegisteredOrder,
    enabled: Boolean,
    onClick: () -> Unit,
    filter: HomeOrderFilter = HomeOrderFilter.ALL,
    modifier: Modifier = Modifier
) = OrderSummaryCard(order, enabled, onClick, filter, modifier)
