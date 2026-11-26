package com.example.wfappops.orders.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.designsystem.icons.WfIcons
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.ColorTokens

/** Dos CTA estables: agregar productos y enviar; el envío permanece visible sin borrador. */
@Composable
internal fun OrderBottomActions(
    busy: Boolean, hasDraft: Boolean, hasDispatches: Boolean, selectedCount: Int?,
    onAdd: () -> Unit, onSend: () -> Unit, onCancelLines: () -> Unit,
    addingEnabled: Boolean = true, sendingEnabled: Boolean = true
) {
    Box(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(WfSpacing.md), contentAlignment = Alignment.Center) {
        if (selectedCount != null) {
            WfButton(stringResource(R.string.order_cancel_lines), onClick = onCancelLines,
                enabled = selectedCount > 0 && !busy, fullWidth = true, style = WfButtonStyle.DESTRUCTIVE)
        } else {
            val scheme = MaterialTheme.colorScheme
            Surface(modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                shape = CircleShape, color = scheme.surfaceContainer,
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)), shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically) {
                    OrderActionIcon(WfIcons.AddProduct,
                        stringResource(if (!hasDraft && hasDispatches) R.string.order_new_dispatch else R.string.order_add_products),
                        onAdd, enabled = !busy && addingEnabled, primary = false, modifier = Modifier.weight(1f),
                        visibleLabel = stringResource(R.string.order_products_cta))
                    OrderActionIcon(WfIcons.SendToKitchen,
                        stringResource(R.string.order_send), onSend, enabled = hasDraft && !busy && sendingEnabled,
                        primary = true, loading = busy && hasDraft, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OrderActionIcon(
    icon: Int, label: String, onClick: () -> Unit, enabled: Boolean, primary: Boolean,
    modifier: Modifier = Modifier, loading: Boolean = false, visibleLabel: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (primary) scheme.onPrimary else scheme.primary
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.height(52.dp).semantics { contentDescription = label }, shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = if (primary) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) ColorTokens.PrimaryDeep else Color.White,
            contentColor = foreground,
            disabledContainerColor = if (primary) ColorTokens.PrimaryDeep else Color.White,
            disabledContentColor = if (loading) scheme.onPrimary else foreground.copy(alpha = 0.38f)
        )) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = foreground, strokeWidth = 2.dp)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            visibleLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
