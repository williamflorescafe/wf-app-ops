package com.example.wfappops.tables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.ui.components.StatusChip
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.model.OperationalStateColor
import com.example.wfappops.ui.model.operationalStateColor
import com.example.wfappops.ui.theme.color
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing
import java.time.format.DateTimeFormatter

private val tableStartTime = DateTimeFormatter.ofPattern("HH:mm").withZone(tableZone)
private val tableStartDescription = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(tableZone)

/** Mesa ocupada en blanco; mesa sin atención local vigente en verde. */
@Composable
internal fun OpsTableCard(
    table: AvailableTable,
    modifier: Modifier = Modifier,
    attention: TableAttention = TableAttention.Free,
    enabled: Boolean = true,
    onSelect: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val stateColor = attention.operationalStateColor()
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().drawBehind {
            if (stateColor == OperationalStateColor.ORANGE) {
                drawRect(stateColor.color, size = Size(WfSize.statusStripeWidth.toPx(), size.height))
            }
        }.padding(WfSpacing.md), verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                    Icon(painterResource(R.drawable.ic_tables), contentDescription = null,
                        tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    Text(table.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(pluralStringResource(R.plurals.tables_people, table.seats, table.seats), style = MaterialTheme.typography.labelMedium)
            }
            StatusChip(
                stringResource(if (attention is TableAttention.Free) R.string.tables_free else R.string.tables_occupied_status),
                if (stateColor == OperationalStateColor.GREEN) StatusTone.POSITIVE else StatusTone.WARNING
            )
            HorizontalDivider(color = scheme.primary.copy(alpha = 0.16f))
            when (attention) {
                TableAttention.Free -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedIconButton(
                        onClick = { (onAdd ?: onSelect)?.invoke() }, enabled = enabled && (onAdd != null || onSelect != null),
                        shape = CircleShape, modifier = Modifier.size(WfSize.minTouchTarget),
                        border = BorderStroke(1.5.dp, scheme.primary),
                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = scheme.primary)
                    ) {
                        Icon(painterResource(R.drawable.ic_carta_add),
                            contentDescription = stringResource(R.string.tables_open_action), modifier = Modifier.size(22.dp))
                    }
                }
                is TableAttention.Occupied -> Row(
                    Modifier.fillMaxWidth().heightIn(min = WfSize.minTouchTarget), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)
                ) {
                    Text(attention.attendant?.shortName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tables_attendant_unknown),
                        modifier = Modifier.weight(1f).semantics {
                            attention.attendant?.fullName?.takeIf { it.isNotBlank() }?.let { contentDescription = it }
                        }, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, modifier = Modifier.size(14.dp))
                    val time = attention.startedAt?.let(tableStartTime::format) ?: "—"
                    val description = attention.startedAt?.let { stringResource(R.string.tables_started_at, tableStartDescription.format(it)) }
                    Text(time, modifier = Modifier.semantics { if (description != null) contentDescription = description },
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    val colors = CardDefaults.cardColors(
        containerColor = if (stateColor == OperationalStateColor.GREEN) scheme.primaryContainer else Color.White,
        disabledContainerColor = if (stateColor == OperationalStateColor.GREEN) scheme.primaryContainer else Color.White,
        disabledContentColor = scheme.onPrimaryContainer,
        contentColor = scheme.onPrimaryContainer
    )
    val border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.12f))
    if (onSelect != null) {
        Card(onClick = onSelect, enabled = enabled, modifier = modifier, shape = MaterialTheme.shapes.large,
            colors = colors, border = border) { content() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border) { content() }
    }
}
