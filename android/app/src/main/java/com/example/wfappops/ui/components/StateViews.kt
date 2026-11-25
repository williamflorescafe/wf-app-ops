package com.example.wfappops.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.ui.theme.StatusTone
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras

/** Estado vacío con la composición de icono, título y explicación de WF Team. */
@Composable
internal fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    WfCard(modifier.fillMaxWidth()) {
        StateMessage(title, message, StatusTone.NEUTRAL, R.drawable.ic_state_empty)
    }
}

/** Error persistente con reintento opcional, bloqueado mientras se realiza la operación. */
@Composable
internal fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_error_title),
    busy: Boolean = false,
    retryLabel: String = stringResource(R.string.auth_retry)
) {
    WfCard(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        StateMessage(title, message, StatusTone.CRITICAL, R.drawable.ic_error) {
            if (onRetry != null) {
                WfButton(
                    text = retryLabel,
                    onClick = onRetry,
                    style = WfButtonStyle.SECONDARY,
                    leadingIcon = R.drawable.ic_refresh,
                    isLoading = busy
                )
            }
        }
    }
}

/** Composición común de estados y confirmaciones para mantener sus proporciones. */
@Composable
internal fun StateMessage(
    title: String,
    message: String?,
    tone: StatusTone,
    icon: Int,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = WfThemeExtras.statusColors.of(tone)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WfSpacing.md)
    ) {
        Box(Modifier.size(64.dp).background(colors.container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        content()
    }
}
