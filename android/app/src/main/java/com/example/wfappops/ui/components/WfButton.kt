package com.example.wfappops.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.wfappops.ui.theme.WfRadius
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras

/** Variante visual de [WfButton]. Una pantalla tiene como máximo un botón [PRIMARY]. */
internal enum class WfButtonStyle {
    /** Acción principal: fondo verde bosque. */
    PRIMARY,

    /** Agregar un producto o avanzar al carrito. */
    PURCHASE,

    /** Acción alternativa con borde café. */
    ALTERNATIVE,

    /** Volver o cancelar la interacción actual. */
    BACK,

    /** Acceso a cerrar sesión. */
    LOGOUT_SOFT,

    /** Confirmar el cierre de sesión. */
    LOGOUT,

    /** Confirmación de una acción destructiva. */
    DESTRUCTIVE,
}

// Escala al presionar: da respuesta táctil sin animaciones llamativas (docs/design/stitch/DESIGN.md).
private const val PRESSED_SCALE = 0.98f

/**
 * Botón de la app.
 *
 * Mientras [isLoading] es verdadero muestra un indicador en lugar del texto, mantiene su tamaño y
 * colores, e ignora los toques para evitar envíos duplicados.
 */
@Composable
internal fun WfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: WfButtonStyle = WfButtonStyle.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: Int? = null,
    fullWidth: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, label = "buttonScale")
    val colors = buttonColors(style, keepColorsWhileLoading = isLoading)
    val scheme = MaterialTheme.colorScheme
    val border = if (!enabled && !isLoading) null else when (style) {
        WfButtonStyle.ALTERNATIVE -> BorderStroke(1.dp, scheme.secondary)
        WfButtonStyle.BACK -> BorderStroke(1.dp, scheme.outlineVariant)
        else -> null
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = WfSize.control)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(WfRadius.md),
        colors = colors,
        border = border,
        contentPadding = PaddingValues(horizontal = WfSpacing.xl, vertical = WfSpacing.md),
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // El contenido se oculta sin quitarse para que el botón no cambie de ancho al cargar.
            Row(modifier = Modifier.alpha(if (isLoading) 0f else 1f), verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.let {
                    Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(WfSpacing.sm))
                }
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.contentColor,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun buttonColors(style: WfButtonStyle, keepColorsWhileLoading: Boolean): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (style) {
        WfButtonStyle.PRIMARY -> scheme.primary to WfThemeExtras.onAdvanceAction
        WfButtonStyle.PURCHASE -> WfThemeExtras.purchaseAction to WfThemeExtras.onPurchaseAction
        WfButtonStyle.ALTERNATIVE -> Color.Transparent to scheme.secondary
        WfButtonStyle.BACK -> scheme.surface to scheme.primary
        WfButtonStyle.LOGOUT_SOFT -> WfThemeExtras.logoutSurface to WfThemeExtras.logoutAction
        WfButtonStyle.LOGOUT -> WfThemeExtras.logoutAction to scheme.onError
        WfButtonStyle.DESTRUCTIVE -> scheme.error to scheme.onError
    }
    return ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = content,
        disabledContainerColor = if (keepColorsWhileLoading) {
            container
        } else {
            scheme.outlineVariant
        },
        disabledContentColor = if (keepColorsWhileLoading) content else scheme.onSurfaceVariant,
    )
}
