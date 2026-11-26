package com.example.wfappops.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wfappops.R
import com.example.wfappops.ui.theme.StatusTone
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
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val destructive = confirmStyle == WfButtonStyle.DESTRUCTIVE
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy)
    ) {
        WfCard(Modifier.fillMaxWidth().heightIn(max = windowHeight * 0.85f)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(WfSpacing.lg)) {
                StateMessage(
                    title, message,
                    if (destructive) StatusTone.CRITICAL else StatusTone.NEUTRAL,
                    if (destructive) R.drawable.ic_error else R.drawable.ic_order_bag
                )
                content()
                WfButton(confirmLabel, onConfirm, style = confirmStyle, isLoading = busy, enabled = confirmEnabled && !busy, fullWidth = true)
                if (dismissLabel != null) {
                    WfButton(dismissLabel, onDismiss, style = WfButtonStyle.OUTLINED, enabled = !busy, fullWidth = true)
                }
            }
        }
    }
}
