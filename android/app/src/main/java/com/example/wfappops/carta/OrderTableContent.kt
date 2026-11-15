package com.example.wfappops.carta

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.tables.KitchenItemStatus
import com.example.wfappops.tables.OrderDispatch
import com.example.wfappops.tables.OrderLine
import com.example.wfappops.tables.TableVisit
import com.example.wfappops.tables.tableActivityTime
import com.example.wfappops.ui.theme.WfThemeExtras
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing

private val KitchenStatusWidth = 96.dp

@Composable
internal fun OrderTableHeader(number: Int?, visit: TableVisit?, enabled: Boolean, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val orderNumber = visit?.orderNumber
    val startedAt = visit?.orderStartedAt
    val isActive = visit?.closed == false && orderNumber != null && startedAt != null
    val activeLabel = stringResource(R.string.tables_open)
    Surface(color = scheme.primary, contentColor = scheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp)
                .padding(start = WfSpacing.xs, end = WfSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right), stringResource(R.string.auth_back),
                    Modifier.rotate(180f)
                )
            }
            Row(
                modifier = Modifier.padding(start = WfSpacing.xs).semantics(mergeDescendants = true) {
                    if (isActive) stateDescription = activeLabel
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (number == null) stringResource(R.string.order_table_title)
                    else stringResource(R.string.order_table_number, number),
                    style = MaterialTheme.typography.titleLarge
                )
                if (isActive) ActiveTableDot()
                if (orderNumber != null && startedAt != null) {
                    Text(
                        stringResource(R.string.order_number, orderNumber),
                        modifier = Modifier.padding(start = WfSpacing.sm),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActiveTableDot() {
    val color = WfThemeExtras.activeIndicator
    val pulse = rememberOrderPulse()
    Canvas(Modifier.size(20.dp)) {
        val progress = pulse.value
        val radius = 4.dp.toPx()
        drawCircle(color, radius = radius * (1f + 1.5f * progress), alpha = 0.5f * (1f - progress))
        drawCircle(color, radius = radius)
    }
}

@Composable
internal fun rememberOrderPulse(): State<Float> =
    rememberInfiniteTransition(label = "Pulso de comanda").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                // Pulso de disponibilidad de Web Store.
                durationMillis = 1_800
                0f at 0 using CubicBezierEasing(0f, 0f, 0.58f, 1f)
                1f at 1_440
            }
        ),
        label = "Halo"
    )

@Composable
internal fun OrderDraftCard(
    ordinal: Int,
    lines: List<OrderLine>,
    busy: Boolean,
    expandRequest: Int,
    onEditNote: (OrderLine) -> Unit,
    onAdjust: (OrderLine, Int) -> Unit
) {
    OrderCard(
        stringResource(R.string.order_dispatch, ordinal), stringResource(R.string.order_draft_status),
        draft = true, expandRequest = expandRequest
    ) {
        OrderColumns(editable = true)
        lines.forEach { line ->
            HorizontalDivider()
            OrderLineContent(line, onAdjust = { onAdjust(line, it) }, busy = busy, onEditNote = { onEditNote(line) })
        }
    }
}

@Composable
internal fun RegisteredDispatchCard(
    dispatch: OrderDispatch,
    ordinal: Int,
    selected: Boolean,
    onLongPress: () -> Unit
) {
    val canceled = dispatch.canceledAt != null
    OrderCard(
        stringResource(R.string.order_dispatch, ordinal),
        stringResource(if (canceled) R.string.order_canceled else R.string.order_sent_status),
        draft = false, time = tableActivityTime.format(dispatch.sentAt),
        selected = selected, onLongPress = onLongPress
    ) {
        OrderColumns(editable = false)
        dispatch.lines.forEach { line ->
            HorizontalDivider()
            OrderLineContent(if (canceled) line.copy(kitchenStatus = KitchenItemStatus.CANCELED) else line)
        }
    }
}

@Composable
private fun OrderCard(
    title: String,
    status: String,
    draft: Boolean,
    time: String? = null,
    selected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    expandRequest: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(expandRequest) { mutableStateOf(true) }
    val borderColor = when {
        selected -> WfThemeExtras.activeIndicator
        draft -> scheme.onSurface
        else -> scheme.outlineVariant
    }
    val headerColor = if (draft) scheme.tertiaryContainer else scheme.surfaceContainer
    val interaction = if (onLongPress == null) Modifier else Modifier
        .semantics { this.selected = selected }
        .combinedClickable(
            onClick = { expanded = !expanded },
            onLongClick = onLongPress,
            onLongClickLabel = stringResource(R.string.order_select_dispatch)
        )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(interaction) {
            Surface(color = headerColor) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget)
                        .then(if (onLongPress == null) Modifier.clickable { expanded = !expanded } else Modifier)
                        .padding(WfSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        StatusLabel(status, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
                    }
                    if (time != null) Text(
                        time,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End
                    )
                    Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp).rotate(if (expanded) -90f else 90f))
                }
            }
            if (expanded) Column(Modifier.padding(WfSpacing.md), verticalArrangement = Arrangement.spacedBy(WfSpacing.sm), content = content)
        }
    }
}

@Composable
private fun OrderColumns(editable: Boolean) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
        Text(stringResource(R.string.order_product_column), Modifier.weight(1f), color = color, style = style)
        Text(
            stringResource(R.string.order_quantity_column), Modifier.width(if (editable) 112.dp else 38.dp),
            textAlign = TextAlign.Center, color = color, style = style
        )
        Text(stringResource(R.string.order_price_column), Modifier.width(68.dp), textAlign = TextAlign.End, color = color, style = style)
        if (!editable) {
            Box(Modifier.width(KitchenStatusWidth), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.order_status_column), color = color, style = style)
            }
        }
    }
}

@Composable
private fun OrderLineContent(
    line: OrderLine,
    busy: Boolean = false,
    onAdjust: ((Int) -> Unit)? = null,
    onEditNote: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            val noteAction = stringResource(R.string.order_edit_note, line.product)
            val product = Modifier.weight(1f)
            val productTarget = if (onEditNote == null) product else product.heightIn(min = WfSize.minTouchTarget)
                .clickable(enabled = !busy, onClickLabel = noteAction, role = Role.Button, onClick = onEditNote)
            Column(productTarget, verticalArrangement = Arrangement.Center) {
                Text(line.product, style = MaterialTheme.typography.titleSmall)
                if (line.options.isNotEmpty()) {
                    Text(line.options.joinToString(), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (onAdjust != null) {
                Surface(
                    shape = CircleShape, border = BorderStroke(1.dp, scheme.primary),
                    color = scheme.surface, contentColor = scheme.primary
                ) {
                    val quantityColors = IconButtonDefaults.iconButtonColors(contentColor = scheme.primary)
                    Row(Modifier.width(112.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onAdjust(-1) }, enabled = !busy,
                            modifier = Modifier.size(WfSize.minTouchTarget), colors = quantityColors
                        ) {
                            Icon(painterResource(R.drawable.ic_order_minus), stringResource(R.string.carta_quantity_less), Modifier.size(18.dp))
                        }
                        Text(line.quantity.toString(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = { onAdjust(1) }, enabled = !busy && line.quantity < Int.MAX_VALUE,
                            modifier = Modifier.size(WfSize.minTouchTarget), colors = quantityColors
                        ) {
                            Icon(painterResource(R.drawable.ic_carta_add), stringResource(R.string.carta_quantity_more), Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Text(line.quantity.toString(), Modifier.width(38.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            }
            Text(formatCartaPrice(line.totalInCents), Modifier.width(68.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
            if (onAdjust == null) {
                Box(Modifier.width(KitchenStatusWidth), contentAlignment = Alignment.Center) {
                    val status = line.kitchenStatus
                    if (status != null) {
                        KitchenStatusLabel(status)
                    } else {
                        StatusLabel(stringResource(R.string.order_status_unknown), scheme.surfaceContainer, scheme.onSurfaceVariant)
                    }
                }
            }
        }
        val note = line.note
        if (note != null) {
            StatusLabel(stringResource(R.string.order_note_text, note), scheme.surfaceContainer, scheme.onSurface)
        }
    }
}

@Composable
private fun KitchenStatusLabel(status: KitchenItemStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        KitchenItemStatus.IN_PREPARATION -> WfThemeExtras.statusColors.warning.let {
            StatusLabel(stringResource(R.string.order_preparing), it.container, it.content)
        }
        KitchenItemStatus.READY -> WfThemeExtras.statusColors.positive.let {
            StatusLabel(stringResource(R.string.order_ready), it.container, it.content)
        }
        KitchenItemStatus.DELIVERED -> StatusLabel(stringResource(R.string.order_delivered), scheme.surfaceContainerHighest, scheme.onSurface)
        KitchenItemStatus.CANCELED -> StatusLabel(stringResource(R.string.order_canceled), scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
private fun StatusLabel(text: String, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color) {
    Surface(color = background, contentColor = foreground, shape = CircleShape) {
        Text(
            text,
            Modifier.padding(horizontal = WfSpacing.sm, vertical = WfSpacing.xs),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}
