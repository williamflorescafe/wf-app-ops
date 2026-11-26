package com.example.wfappops.orders.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.wfappops.R
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.ui.components.WfDialog
import com.example.wfappops.ui.theme.WfSpacing

@Composable
internal fun OrderSendSelection(lines: List<OrderLine>, busy: Boolean,
    onDismiss: () -> Unit, onConfirm: (Map<Int, Int>) -> Unit) {
    var quantities by remember(lines) { mutableStateOf(lines.mapIndexed { index, line -> index to line.quantity }.toMap()) }
    val units = quantities.values.sumOf { it.toLong() }
    WfDialog(title = stringResource(R.string.order_send), confirmLabel = stringResource(R.string.order_send_selected, units),
        onDismiss = onDismiss, busy = busy, confirmEnabled = units > 0 && quantities.size <= 60,
        message = stringResource(R.string.order_selection_hint), onConfirm = { onConfirm(quantities) }) {
        lines.forEachIndexed { index, line ->
            val quantity = quantities[index] ?: 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                Checkbox(checked = quantity > 0, enabled = !busy, onCheckedChange = { selected ->
                    quantities = if (selected) quantities + (index to line.quantity) else quantities - index
                })
                Column(Modifier.weight(1f)) {
                    Text(line.product, style = MaterialTheme.typography.bodyMedium)
                    if (line.options.isNotEmpty()) Text(line.options.joinToString(), style = MaterialTheme.typography.bodySmall)
                    line.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !busy && quantity > 0, onClick = {
                    quantities = if (quantity > 1) quantities + (index to quantity - 1) else quantities - index
                }) { Text("−") }
                Text(stringResource(R.string.order_send_quantity, quantity, line.quantity), style = MaterialTheme.typography.labelLarge)
                TextButton(enabled = !busy && quantity < line.quantity, onClick = {
                    quantities = quantities + (index to quantity + 1)
                }) { Text("+") }
            }
            HorizontalDivider()
        }
    }
}
