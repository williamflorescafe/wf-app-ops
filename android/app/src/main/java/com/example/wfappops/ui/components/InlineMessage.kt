package com.example.wfappops.ui.components

import com.example.wfappops.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfRadius
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras

/**
 * Mensaje dentro de un formulario (por ejemplo, "Correo o contraseña incorrectos").
 * Los lectores de pantalla lo anuncian al aparecer.
 */
@Composable
internal fun InlineMessage(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val colors = WfThemeExtras.statusColors.of(tone)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, RoundedCornerShape(WfRadius.md))
            .padding(horizontal = WfSpacing.lg, vertical = WfSpacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (tone == StatusTone.CRITICAL) R.drawable.ic_error else R.drawable.ic_check_circle
        Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.content)
    }
}
