package com.example.wfappops.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras

/**
 * Etiqueta en forma de píldora para un estado (por ejemplo, "Tardanza").
 *
 * [label] es el texto que envía la API; [tone] solo aporta el color. Con [showDot] se agrega un punto
 * del mismo color, útil para estados "en curso".
 */
@Composable
internal fun StatusChip(label: String, tone: StatusTone, modifier: Modifier = Modifier, showDot: Boolean = false) {
    val colors = WfThemeExtras.statusColors.of(tone)
    Row(
        modifier = modifier
            .background(colors.container, CircleShape)
            .padding(horizontal = WfSpacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) Box(Modifier.size(8.dp).background(colors.content, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.content)
    }
}
