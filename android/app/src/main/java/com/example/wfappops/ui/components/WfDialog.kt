package com.example.wfappops.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wfappops.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.wfappops.ui.theme.WfSpacing

/** Confirmación propia en una tarjeta de WF Team, con acciones y cierre bloqueados durante el guardado. */
@Composable
internal fun WfDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    confirmEnabled: Boolean = true,
    message: String? = null,
    confirmStyle: WfButtonStyle = WfButtonStyle.PRIMARY,
    dismissLabel: String? = stringResource(R.string.tables_cancel),
    inlineActions: Boolean = true,
    onDismissAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy)
    ) {
        WfCard(Modifier.fillMaxWidth().heightIn(max = windowHeight * 0.85f)) {
            Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.lg)) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(WfSpacing.lg)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                        Text(title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center)
                        if (!message.isNullOrBlank()) Text(
                            message, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    content()
                }
                if (inlineActions) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                        if (dismissLabel != null) WfButton(dismissLabel, onDismissAction ?: onDismiss, style = WfButtonStyle.OUTLINED,
                            enabled = !busy, modifier = Modifier.weight(1f))
                        WfButton(confirmLabel, onConfirm, style = confirmStyle, isLoading = busy,
                            enabled = confirmEnabled && !busy, modifier = Modifier.weight(1f))
                    }
                } else {
                    WfButton(confirmLabel, onConfirm, style = confirmStyle, isLoading = busy, enabled = confirmEnabled && !busy, fullWidth = true)
                    if (dismissLabel != null) WfButton(dismissLabel, onDismissAction ?: onDismiss, style = WfButtonStyle.OUTLINED, enabled = !busy, fullWidth = true)
                }
            }
        }
    }
}
