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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wfappops.R
import com.example.wfappops.carta.formatCartaPrice
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderDispatch
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.tables.tableActivityTime
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.components.WfDialog
import com.example.wfappops.designsystem.icons.WfIcons
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
    cashierBlocked: Boolean,
    onCashier: (() -> Unit)?,
    workerName: String = "",
    onSelectTable: (() -> Unit)? = null,
    onChangeResponsible: (() -> Unit)? = null,
    onCancelOrder: (() -> Unit)? = null,
    cashierEnabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val orderNumber = visit?.orderNumber
    val startedAt = visit?.orderStartedAt
    val isActive = visit?.closed == false && orderNumber != null && startedAt != null
    val activeLabel = stringResource(R.string.tables_open)
    Column {
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
                    if (orderNumber != null) stringResource(R.string.order_header_number,
                        stringResource(R.string.order_number, orderNumber)) else visit?.localDraftNumber?.let {
                            stringResource(R.string.order_local_draft_number, it) } ?: stringResource(R.string.order_draft_status),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onCancelOrder != null) androidx.compose.material3.TextButton(onClick = onCancelOrder, enabled = enabled) {
                Text(stringResource(R.string.order_cancel_action), color = scheme.onPrimary)
            }
            if (onCashier != null) {
                val blockedLabel = stringResource(R.string.order_cashier_pending)
                IconButton(
                    onClick = onCashier,
                    enabled = enabled && cashierEnabled,
                    modifier = Modifier.semantics { if (cashierBlocked) stateDescription = blockedLabel }
                ) {
                    Icon(
                        painterResource(WfIcons.Cashier), stringResource(R.string.order_cashier_title),
                        modifier = Modifier.alpha(if (cashierBlocked) 0.55f else 1f)
                    )
                }
            }
        }
    }
    Surface(color = scheme.surfaceContainerLowest, contentColor = scheme.onSurface) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = WfSpacing.sm)) {
            OrderHeaderField(
                label = stringResource(R.string.order_attendant_label),
                value = visit?.responsible?.name?.takeIf { it.isNotBlank() }
                    ?: visit?.openedBy?.name?.takeIf { it.isNotBlank() } ?: workerName,
                icon = R.drawable.ic_person,
                action = stringResource(R.string.order_change_assignment),
                actionDescription = stringResource(R.string.tables_change_responsible),
                enabled = enabled,
                onAction = onChangeResponsible,
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = WfSpacing.xs), color = scheme.outlineVariant)
            val hasTable = visit?.remoteTableId != null || (visit != null && !visit.apiBacked)
            OrderHeaderField(
                label = stringResource(R.string.order_table_label),
                value = if (hasTable) visit?.tableName?.takeIf { it.isNotBlank() }
                    ?: visit?.tableCode?.takeIf { it.isNotBlank() } ?: number?.toString().orEmpty()
                    else stringResource(R.string.order_table_unassigned),
                icon = R.drawable.ic_tables,
                action = stringResource(if (hasTable) R.string.order_change_assignment else R.string.order_select_table),
                actionDescription = stringResource(if (hasTable) R.string.tables_switch_table else R.string.tables_choose_table),
                enabled = enabled,
                onAction = onSelectTable,
                modifier = Modifier.weight(1f)
            )
        }
    }
    HorizontalDivider(color = scheme.outlineVariant)
    }
}

@Composable
private fun OrderHeaderField(
    label: String,
    value: String,
    icon: Int,
    action: String,
    actionDescription: String,
    enabled: Boolean,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = WfSpacing.md), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant)
            FilledTonalButton(
                onClick = { onAction?.invoke() }, enabled = enabled && onAction != null,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 32.dp).semantics { contentDescription = actionDescription },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer)
            ) { Text(action, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(icon), null, Modifier.padding(top = 2.dp).size(16.dp), tint = scheme.primary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
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
    onAdjust: (OrderLine, Int) -> Unit,
    onComment: (() -> Unit)? = null,
    comment: String? = null,
    time: String? = null
) {
    val units = lines.sumOf { it.quantity.toLong() }
    OrderSection(
        stringResource(R.string.order_draft_status), unitsCount = units,
        draft = true, time = time, expandRequest = expandRequest, attentionRequest = attentionRequest,
        onComment = onComment, comment = comment, commentEnabled = !busy,
        commentLabel = stringResource(R.string.order_edit_draft_comment)
    ) {
        lines.forEachIndexed { index, line ->
            if (index > 0) HorizontalDivider()
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
    onAdvanceLine: ((Int, KitchenItemStatus) -> Unit)? = null,
    attentionRequest: Int = 0,
    selectionEnabled: Boolean = true,
    statusActionsOnly: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    val select = onSelect.takeIf { selectionEnabled && !busy && dispatch.canceledAt == null && dispatch.activeLines.isNotEmpty() }
    var showComment by rememberSaveable(dispatch.remoteId, ordinal) { mutableStateOf(false) }
    OrderSection(
        stringResource(R.string.order_dispatch, ordinal),
        unitsCount = dispatch.lines.sumOf { it.quantity.toLong() },
        draft = false, time = tableActivityTime.format(dispatch.sentAt),
        selected = selected, served = dispatch.isDelivered, canceled = dispatch.isCanceled,
        onSelect = select, attentionRequest = attentionRequest,
        onComment = { showComment = true }, comment = dispatch.note,
        commentLabel = stringResource(R.string.order_view_dispatch_comment, ordinal)
    ) {
        dispatch.lines.forEachIndexed { index, line ->
            if (index > 0) HorizontalDivider()
            val canMark = dispatch.canceledAt == null && line.canCancel
            val nextStatus = when (line.dispatchStatus) {
                KitchenItemStatus.SENT -> R.string.order_preparing
                KitchenItemStatus.IN_PREPARATION -> R.string.order_ready
                else -> null
            }
            val action = onAdvanceLine?.takeIf { !selected && dispatch.canceledAt == null }
            val advance = action?.takeIf { nextStatus != null }
            OrderLineContent(
                line = if (dispatch.canceledAt == null) line else line.copy(kitchenStatus = KitchenItemStatus.CANCELED),
                busy = busy,
                statusActionsOnly = statusActionsOnly,
                marked = canMark && index in markedLines,
                onToggleMark = onToggleLine?.takeIf { canMark }?.let { toggle -> { toggle(index) } },
                onAdvance = advance?.let { action -> { action(index, line.dispatchStatus) } },
                onDeliver = action?.takeIf { line.dispatchStatus == KitchenItemStatus.READY }
                    ?.let { deliver -> { deliver(index, KitchenItemStatus.READY) } },
                advanceLabel = nextStatus?.let { stringResource(R.string.order_advance_status, line.product, stringResource(it)) },
                onLongPress = select?.let { selectDispatch ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectDispatch()
                    }
                }
            )
        }
    }
    if (showComment) WfDialog(
        title = stringResource(R.string.order_dispatch_comment_title, ordinal),
        message = dispatch.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.order_dispatch_comment_empty),
        confirmLabel = stringResource(R.string.order_comment_close), dismissLabel = null,
        onConfirm = { showComment = false }, onDismiss = { showComment = false }
    )
}

@Composable
private fun OrderSection(
    title: String,
    unitsCount: Long = 0,
    draft: Boolean,
    time: String? = null,
    selected: Boolean = false,
    served: Boolean = false,
    canceled: Boolean = false,
    onSelect: (() -> Unit)? = null,
    expandRequest: Int = 0,
    attentionRequest: Int = 0,
    onComment: (() -> Unit)? = null,
    comment: String? = null,
    commentEnabled: Boolean = true,
    commentLabel: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val commentPreview = comment?.trim()?.takeIf { it.isNotEmpty() }
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
        else -> scheme.onSurface
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
                        .padding(start = WfSpacing.md, end = WfSpacing.xs, top = WfSpacing.xs, bottom = WfSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                    FilledTonalButton(onClick = { onComment?.invoke() }, enabled = onComment != null && commentEnabled,
                        modifier = Modifier.weight(1f, fill = false).semantics { contentDescription = commentLabel },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = WfSpacing.sm, vertical = WfSpacing.xs),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = scheme.primary.copy(alpha = if (commentPreview != null) 0.14f else 0.09f),
                            contentColor = scheme.primary,
                            disabledContainerColor = scheme.primary.copy(alpha = 0.05f),
                            disabledContentColor = scheme.onSurfaceVariant)) {
                        Row(Modifier.heightIn(min = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(WfIcons.Comment), null, Modifier.size(20.dp),
                                tint = if (commentPreview != null) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Text(commentPreview ?: stringResource(if (draft) R.string.order_add_round_note else R.string.order_no_round_note),
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    }
                    val toggleLabel = stringResource(if (expanded) R.string.order_collapse_dispatch else R.string.order_expand_dispatch, title)
                    Row(Modifier.sizeIn(minWidth = WfSize.minTouchTarget, minHeight = WfSize.minTouchTarget)
                        .semantics { contentDescription = toggleLabel }
                        .clickable(role = Role.Button, onClickLabel = toggleLabel) { expanded = !expanded }
                        .padding(horizontal = WfSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (time != null) Text(time, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                        Icon(painterResource(R.drawable.ic_chevron_right), null,
                            Modifier.size(18.dp).rotate(if (expanded) -90f else 90f))
                    }
                }
            }
            if (expanded) Column(Modifier.padding(horizontal = WfSpacing.md, vertical = WfSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WfSpacing.xs), content = content)
            else Row(Modifier.fillMaxWidth().padding(horizontal = WfSpacing.md, vertical = WfSpacing.sm),
                horizontalArrangement = Arrangement.End) {
                Text(pluralStringResource(R.plurals.order_draft_units, if (unitsCount == 1L) 1 else 2, unitsCount),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
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
    onAdvance: (() -> Unit)? = null,
    onDeliver: (() -> Unit)? = null,
    advanceLabel: String? = null,
    onLongPress: (() -> Unit)? = null,
    statusActionsOnly: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = when {
        onToggleMark != null -> Modifier.toggleable(
            value = marked, enabled = !busy, onValueChange = { onToggleMark() }
        )
        onAdvance != null && !statusActionsOnly -> Modifier.combinedClickable(
            enabled = !busy,
            role = Role.Button,
            onClickLabel = advanceLabel,
            onClick = onAdvance,
            onLongClick = onLongPress
        )
        else -> Modifier
    }
    Surface(
        modifier = Modifier.fillMaxWidth().then(interaction),
        color = if (marked) scheme.errorContainer else Color.Transparent,
        contentColor = if (marked) scheme.onErrorContainer else LocalContentColor.current,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
            OrderLineDetails(line, busy, onAdjust, onEditNote,
                onStatusClick = if (statusActionsOnly) onAdvance ?: onDeliver else null,
                statusClickLabel = advanceLabel ?: if (onDeliver != null) stringResource(R.string.order_confirm_delivery, line.product) else null)
            if (onDeliver != null) {
                val deliveryLabel = stringResource(R.string.order_confirm_delivery, line.product)
                WfButton(
                    text = stringResource(R.string.order_mark_delivered),
                    onClick = onDeliver,
                    modifier = Modifier.semantics { contentDescription = deliveryLabel },
                    style = WfButtonStyle.SECONDARY,
                    enabled = !busy,
                    fullWidth = true
                )
            }
        }
    }
}

@Composable
private fun OrderLineDetails(
    line: OrderLine,
    busy: Boolean,
    onAdjust: ((Int) -> Unit)?,
    onEditNote: (() -> Unit)?,
    onStatusClick: (() -> Unit)? = null,
    statusClickLabel: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(WfSpacing.xxs)
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
                    style = typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val visibleOptions = line.options.filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                if (visibleOptions.isNotEmpty()) {
                    Text(visibleOptions.joinToString(), style = MaterialTheme.typography.bodySmall)
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
            Text(formatCartaPrice(line.totalInCents, line.currencyCode), Modifier.width(68.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
            if (onAdjust == null) {
                val action = if (onStatusClick == null) Modifier else Modifier.heightIn(min = WfSize.minTouchTarget)
                    .clickable(enabled = !busy, onClickLabel = statusClickLabel, role = Role.Button, onClick = onStatusClick)
                Column(Modifier.width(KitchenStatusWidth).then(action), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    KitchenStatusLabel(line.dispatchStatus)
                }
            }
        }
        val note = line.note
        if (!note.isNullOrBlank() && !note.equals("null", ignoreCase = true)) {
            StatusLabel(stringResource(R.string.order_note_text, note), scheme.surfaceContainer, scheme.onSurface)
        }
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
