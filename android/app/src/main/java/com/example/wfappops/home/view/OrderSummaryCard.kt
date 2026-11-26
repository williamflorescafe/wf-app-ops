package com.example.wfappops.home.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.home.model.productsFor
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.view.ActiveTableDot
import com.example.wfappops.tables.tableActivityTime
import com.example.wfappops.ui.model.operationalStateColor
import com.example.wfappops.ui.theme.color
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfSize

@Composable
internal fun OrderSummaryCard(order: RegisteredOrder, enabled: Boolean, onClick: () -> Unit, filter: HomeOrderFilter,
                              modifier: Modifier = Modifier) {
    if (!order.hasDisplayableProducts) return
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(order.visitId, filter) { mutableStateOf(false) }
    val products = order.productsFor(filter).filter { it.quantity > 0 && it.status != KitchenItemStatus.CANCELED }
    val canExpand = products.isNotEmpty()
    val readyQuantity = products.filter { it.status == KitchenItemStatus.READY }.sumOf { it.quantity }
    val cookingQuantity = products.filter { it.status == KitchenItemStatus.SENT || it.status == KitchenItemStatus.IN_PREPARATION }.sumOf { it.quantity }
    val servedQuantity = products.filter { it.status == KitchenItemStatus.DELIVERED }.sumOf { it.quantity }
    val cooking = order.productsFor(HomeOrderFilter.PREPARING).isNotEmpty()
    val stripe = order.operationalStateColor().color
    val statusDescription = stringResource(when {
        order.status == RegisteredOrderStatus.CLOSED -> R.string.order_closed
        order.status == RegisteredOrderStatus.CANCELED -> R.string.order_canceled
        order.isFullyServed -> R.string.order_delivered
        readyQuantity > 0 -> R.string.order_ready
        cooking -> R.string.order_preparing
        order.status == RegisteredOrderStatus.DELIVERED -> R.string.order_delivered
        order.hasKitchenDispatches -> R.string.order_sent_status
        else -> R.string.home_order_draft
    })
    Card(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().semantics { stateDescription = statusDescription },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().animateContentSize().drawBehind {
            drawRect(stripe, size = Size(WfSize.statusStripeWidth.toPx(), size.height))
        }.padding(horizontal = WfSpacing.md, vertical = WfSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WfSpacing.xxs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                    Text(if (order.localDraft) order.localDraftNumber?.let { stringResource(R.string.order_local_draft_number, it) }
                        ?: stringResource(R.string.order_draft_status) else stringResource(R.string.order_number, order.number),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Row(Modifier.weight(1f, fill = false).semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_tables), null, Modifier.size(16.dp), tint = scheme.primary)
                        Text(if (!order.hasAssignedTable) stringResource(R.string.order_table_unassigned)
                            else order.tableName?.takeIf { it.isNotBlank() } ?: order.tableCode?.takeIf { it.isNotBlank() } ?: order.table.toString(),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(tableActivityTime.format(order.lastActivityAt), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                    if (order.status == RegisteredOrderStatus.CLOSED) {
                        Text(stringResource(R.string.order_closed), style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant)
                    } else {
                    if (readyQuantity > 0) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                        Text(pluralStringResource(R.plurals.order_products_ready_count, if (readyQuantity == 1L) 1 else 2, readyQuantity),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        ActiveTableDot()
                    }
                    if (cookingQuantity > 0) Text(
                        pluralStringResource(R.plurals.order_products_preparing_count, if (cookingQuantity == 1L) 1 else 2, cookingQuantity),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    if (servedQuantity > 0) Text(
                        pluralStringResource(R.plurals.order_products_served_count, if (servedQuantity == 1L) 1 else 2, servedQuantity),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    if (products.isEmpty()) Text(when {
                        order.status == RegisteredOrderStatus.CANCELED -> stringResource(R.string.order_canceled)
                        order.summaryUnitsCount != null -> pluralStringResource(R.plurals.order_summary_products_count,
                            if (order.summaryUnitsCount == 1L) 1 else 2, order.summaryUnitsCount)
                        order.draftProducts.isNotEmpty() -> order.draftProducts.sumOf { it.quantity }.let { count ->
                            pluralStringResource(R.plurals.order_summary_products_count, if (count == 1L) 1 else 2, count) }
                        else -> stringResource(R.string.home_order_draft)
                    },
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
                if (canExpand) IconButton(onClick = { expanded = !expanded }, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_chevron_right),
                        stringResource(if (expanded) R.string.order_products_collapse else R.string.order_products_expand),
                        Modifier.size(20.dp).rotate(if (expanded) 270f else 90f))
                }
            }
            AnimatedVisibility(expanded && canExpand) {
                Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                    HorizontalDivider(color = scheme.outlineVariant)
                    products.groupBy { it.name to it.status }.forEach { (key, lines) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(WfSpacing.md)) {
                            Column(Modifier.weight(1f)) {
                                Text(key.first, style = MaterialTheme.typography.bodyMedium)
                                if (key.second == com.example.wfappops.orders.model.KitchenItemStatus.CANCELED) {
                                    Text(stringResource(R.string.order_canceled), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text(lines.sumOf { it.quantity }.toString(), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
