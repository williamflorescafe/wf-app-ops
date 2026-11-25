package com.example.wfappops.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado en múltiplos de 4 (docs/design/stitch/DESIGN.md, "Layout & Spacing").
 * Las pantallas usan estos valores en lugar de números sueltos.
 */
internal object WfSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Margen lateral de las pantallas. */
    val screenHorizontal = 16.dp

    /** Relleno interno de las tarjetas. */
    val cardPadding = 20.dp
}

/** Radios de las esquinas. */
internal object WfRadius {
    /** Etiquetas pequeñas. */
    val xs = 6.dp

    /** Botones, campos de texto y mosaicos de ícono. */
    val md = 14.dp

    /** Tarjetas. */
    val lg = 20.dp

    /** Paneles y hojas inferiores. */
    val xl = 28.dp
}

/** Tamaños mínimos de interacción y de controles. */
internal object WfSize {
    val statusStripeWidth = 4.dp
    /** Área táctil mínima (estándar de Android; también cumple en iOS). */
    val minTouchTarget = 48.dp

    /** Alto visible de controles compactos. */
    val compactControl = 44.dp

    /** Alto de botones y campos de texto. */
    val control = 52.dp

    /** Mosaico cuadrado con un ícono dentro (por ejemplo, en las tarjetas del inicio). */
    val iconTile = 40.dp
}

/** Formas de Material 3 alineadas con [WfRadius]. */
internal val WfShapes = Shapes(
    extraSmall = RoundedCornerShape(WfRadius.xs),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(WfRadius.md),
    large = RoundedCornerShape(WfRadius.lg),
    extraLarge = RoundedCornerShape(WfRadius.xl),
)
