package com.example.wfappops.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.wfappops.ui.theme.WfSpacing

/** Tarjeta de WF Team para contenido, mensajes y confirmaciones de OPS. */
@Composable
internal fun WfCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(WfSpacing.cardPadding),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val primary = MaterialTheme.colorScheme.primary
    val cardModifier = modifier.shadow(
        elevation = 6.dp,
        shape = shape,
        ambientColor = primary.copy(alpha = 0.08f),
        spotColor = primary.copy(alpha = 0.08f)
    )
    val border = BorderStroke(1.dp, primary.copy(alpha = 0.06f))
    val color = MaterialTheme.colorScheme.surfaceContainerLowest
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, modifier = cardModifier, shape = shape, color = color, border = border, content = body)
    } else {
        Surface(modifier = cardModifier, shape = shape, color = color, border = border, content = body)
    }
}
