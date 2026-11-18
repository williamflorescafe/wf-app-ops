package com.example.wfappops.orders.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.wfappops.R
import com.example.wfappops.carta.formatCartaPrice
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderDispatch
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.tables.tableActivityTime
import com.example.wfappops.ui.theme.WfThemeExtras
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing

private val KitchenStatusWidth = 96.dp

@Composable
internal fun OrderTableHeader(
    number: Int?,
    visit: TableVisit?,
    enabled: Boolean,
    onBack: () -> Unit,
    onComment: (() -> Unit)?,
    cashierBlocked: Boolean,
    onCashier: (() -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    val orderNumber = visit?.orderNumber
    val startedAt = visit?.orderStartedAt
    val isActive = visit?.closed == false && orderNumber != null && startedAt != null
    val activeLabel = stringResource(R.string.tables_open)
    Surface(color = scheme.primary, contentColor = scheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp)
                .padding(horizontal = WfSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right), stringResource(R.string.auth_back),
                    Modifier.rotate(180f)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(start = WfSpacing.xs).semantics(mergeDescendants = true) {
                    if (isActive) stateDescription = activeLabel
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (number == null) stringResource(R.string.order_table_title)
                    else stringResource(R.string.order_table_number, number),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) ActiveTableDot()
                if (orderNumber != null && startedAt != null) {
                    Text(
                        stringResource(R.string.order_number, orderNumber),
                        modifier = Modifier.padding(start = WfSpacing.sm),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
            if (onComment != null) {
                IconButton(onClick = onComment, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_order_comment), stringResource(R.string.order_comment_title))
                }
            }
            if (onCashier != null) {
                val blockedLabel = stringResource(R.string.order_cashier_pending)
                IconButton(
                    onClick = onCashier,
                    enabled = enabled,
                    modifier = Modifier.semantics { if (cashierBlocked) stateDescription = blockedLabel }
                ) {
                    Icon(
                        painterResource(R.drawable.ic_order_cashier), stringResource(R.string.order_cashier_title),
                        modifier = Modifier.alpha(if (cashierBlocked) 0.55f else 1f)
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
    attentionRequest: Int,
    onEditNote: (OrderLine) -> Unit,
    onAdjust: (OrderLine, Int) -> Unit
) {
    OrderCard(
        stringResource(R.string.order_dispatch, ordinal), stringResource(R.string.order_draft_status),
        draft = true, expandRequest = expandRequest, attentionRequest = attentionRequest
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
    busy: Boolean,
    markedLines: Set<Int>,
    onSelect: () -> Unit,
    onToggleLine: ((Int) -> Unit)?,
    onDeliverLine: ((Int) -> Unit)? = null,
    onDeliverDispatch: (() -> Unit)? = null,
    attentionRequest: Int = 0
) {
    val deliverDispatch = onDeliverDispatch.takeIf { dispatch.canDeliver }
    val status = when {
        dispatch.isCanceled -> R.string.order_canceled
        dispatch.isDelivered -> R.string.order_dispatch_delivered
        else -> R.string.order_sent_status
    }
    OrderCard(
        stringResource(R.string.order_dispatch, ordinal),
        stringResource(status),
        draft = false, time = tableActivityTime.format(dispatch.sentAt),
        selected = selected, served = dispatch.isDelivered, canceled = dispatch.isCanceled,
        onSelect = onSelect.takeUnless { busy }, attentionRequest = attentionRequest
    ) {
        OrderColumns(editable = false)
        dispatch.lines.forEachIndexed { index, line ->
            HorizontalDivider()
            val canMark = dispatch.canceledAt == null && line.canCancel
            OrderLineContent(
                line = if (dispatch.canceledAt == null) line else line.copy(kitchenStatus = KitchenItemStatus.CANCELED),
                busy = busy,
                marked = canMark && index in markedLines,
                onToggleMark = onToggleLine?.takeIf { canMark }?.let { toggle -> { toggle(index) } },
                onDeliver = onDeliverLine?.takeIf {
                    deliverDispatch == null && dispatch.canceledAt == null && line.kitchenStatus == KitchenItemStatus.READY
                }?.let { deliver -> { deliver(index) } }
            )
        }
        if (deliverDispatch != null) {
            TextButton(onClick = deliverDispatch, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.order_mark_dispatch_delivered))
            }
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
    served: Boolean = false,
    canceled: Boolean = false,
    onSelect: (() -> Unit)? = null,
    expandRequest: Int = 0,
    attentionRequest: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(expandRequest) { mutableStateOf(true) }
    val attention = remember { Animatable(0f) }
    LaunchedEffect(attentionRequest) {
        attention.snapTo(0f)
        if (attentionRequest > 0) {
            expanded = true
            repeat(2) {
                attention.animateTo(1f, tween(160))
                attention.animateTo(0f, tween(300))
            }
        }
    }
    val pulse = attention.value
    val border = when {
        selected -> BorderStroke(2.dp, WfThemeExtras.activeIndicator)
        pulse > 0f -> BorderStroke((1f + 2f * pulse).dp, scheme.primary.copy(alpha = 0.55f + 0.45f * pulse))
        draft -> BorderStroke(1.dp, scheme.onSurface)
        canceled -> BorderStroke(1.dp, scheme.error.copy(alpha = 0.45f))
        else -> WfThemeExtras.operationsCardBorder
    }
    val positive = WfThemeExtras.statusColors.positive
    val headerColor = when {
        canceled -> scheme.errorContainer
        served -> positive.container
        draft -> scheme.tertiaryContainer
        else -> scheme.surfaceContainer
    }
    val headerContentColor = when {
        canceled -> scheme.onErrorContainer
        served -> positive.content
        else -> LocalContentColor.current
    }
    val haptics = LocalHapticFeedback.current
    val selectDispatch by rememberUpdatedState {
        onSelect?.let {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            it()
        }
    }
    val selectLabel = stringResource(R.string.order_select_dispatch)
    val interaction = Modifier
        .semantics {
            if (!draft) this.selected = selected
            if (onSelect != null) onLongClick(selectLabel) { selectDispatch(); true }
        }
        .then(if (onSelect == null) Modifier else Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { selectDispatch() })
        })
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLowest,
        border = border
    ) {
        Column(interaction) {
            Surface(color = headerColor, contentColor = headerContentColor) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget)
                        .padding(start = WfSpacing.md, end = WfSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        StatusLabel(
                            status,
                            when {
                                canceled -> scheme.error
                                served -> scheme.surface
                                else -> scheme.surfaceContainerHighest
                            },
                            when {
                                canceled -> scheme.onError
                                served -> positive.content
                                else -> scheme.onSurfaceVariant
                            }
                        )
                    }
                    if (time != null) Text(
                        time,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End
                    )
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(WfSize.minTouchTarget)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            stringResource(if (expanded) R.string.order_collapse_dispatch else R.string.order_expand_dispatch, title),
                            Modifier.size(18.dp).rotate(if (expanded) -90f else 90f)
                        )
                    }
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
    onEditNote: (() -> Unit)? = null,
    marked: Boolean = false,
    onToggleMark: (() -> Unit)? = null,
    onDeliver: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val selection = if (onToggleMark != null) {
        Modifier.toggleable(
            value = marked, enabled = !busy, onValueChange = { onToggleMark() }
        )
    } else Modifier
    Surface(
        modifier = Modifier.fillMaxWidth().then(selection),
        color = if (marked) scheme.errorContainer else Color.Transparent,
        contentColor = if (marked) scheme.onErrorContainer else LocalContentColor.current,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        OrderLineDetails(line, busy, onAdjust, onEditNote, onDeliver)
    }
}

@Composable
private fun OrderLineDetails(
    line: OrderLine,
    busy: Boolean,
    onAdjust: ((Int) -> Unit)?,
    onEditNote: (() -> Unit)?,
    onDeliver: (() -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
        ) {
            val noteAction = stringResource(R.string.order_edit_note, line.product)
            val product = Modifier.weight(1f)
            val productTarget = if (onEditNote == null) product else product.heightIn(min = WfSize.minTouchTarget)
                .clickable(enabled = !busy, onClickLabel = noteAction, role = Role.Button, onClick = onEditNote)
            Column(productTarget, verticalArrangement = Arrangement.Center) {
                val typography = MaterialTheme.typography
                Text(
                    line.product,
                    style = typography.titleSmall.copy(lineHeight = 1.25.em),
                    autoSize = ProductNameSize(typography.titleSmall.fontSize, typography.bodyMedium.fontSize),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
                Column(Modifier.width(KitchenStatusWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    KitchenStatusLabel(line.dispatchStatus)
                    if (onDeliver != null) {
                        val action = stringResource(R.string.order_confirm_delivery, line.product)
                        TextButton(
                            onClick = onDeliver,
                            enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = action }
                        ) {
                            Text(stringResource(R.string.order_mark_delivered))
                        }
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

private data class ProductNameSize(val regular: TextUnit, val compact: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val layout = performLayout(constraints, text, regular)
        return if (layout.lineCount != 1 || layout.hasVisualOverflow || layout.isLineEllipsized(0)) compact else regular
    }
}

@Composable
internal fun KitchenStatusLabel(status: KitchenItemStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        KitchenItemStatus.SENT -> StatusLabel(
            stringResource(R.string.order_line_sent), scheme.surfaceContainer, scheme.onSurfaceVariant
        )
        KitchenItemStatus.IN_PREPARATION -> WfThemeExtras.statusColors.warning.let {
            StatusLabel(stringResource(R.string.order_preparing), it.container, it.content)
        }
        KitchenItemStatus.READY, KitchenItemStatus.DELIVERED -> WfThemeExtras.statusColors.positive.let {
            val label = if (status == KitchenItemStatus.READY) R.string.order_ready else R.string.order_delivered
            StatusLabel(stringResource(label), it.container, it.content)
        }
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
