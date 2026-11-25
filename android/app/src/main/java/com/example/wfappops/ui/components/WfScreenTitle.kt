package com.example.wfappops.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.example.wfappops.ui.theme.WfSpacing

/** Título alineado al margen común de las pantallas principales de OPS. */
@Composable
internal fun WfScreenTitle(title: String, trailingText: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WfSpacing.lg, vertical = WfSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)
    ) {
        WfTitleText(title, Modifier.weight(1f))
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** La misma tipografía en encabezados de página y títulos de sección principales. */
@Composable
internal fun WfTitleText(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}
