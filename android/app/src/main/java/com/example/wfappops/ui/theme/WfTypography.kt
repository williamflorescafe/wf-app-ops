package com.example.wfappops.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.wfappops.R

/** Inter para texto de lectura: pesos 400, 500 y 600. */
private fun interFamily() = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Inter Display para títulos y cifras grandes: está dibujada para leerse mejor en tamaños grandes. */
private fun interDisplayFamily() = FontFamily(Font(R.font.inter_display_semibold, FontWeight.SemiBold))

/**
 * Escala tipográfica de WF Team adaptada a los roles de Material 3.
 */
internal fun wfTypography(): Typography {
    val text = interFamily()
    val display = interDisplayFamily()
    fun style(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Int, tracking: Double) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
    )
    return Typography(
        displayLarge = style(display, FontWeight.SemiBold, 48, 56, -0.03),
        displayMedium = style(display, FontWeight.SemiBold, 42, 50, -0.03),
        displaySmall = style(display, FontWeight.SemiBold, 36, 44, -0.025),
        headlineLarge = style(display, FontWeight.SemiBold, 32, 38, -0.02),
        headlineMedium = style(display, FontWeight.SemiBold, 28, 34, -0.02),
        headlineSmall = style(display, FontWeight.SemiBold, 24, 30, -0.015),
        titleLarge = style(text, FontWeight.SemiBold, 20, 26, -0.015),
        titleMedium = style(text, FontWeight.SemiBold, 18, 24, -0.01),
        titleSmall = style(text, FontWeight.Medium, 16, 22, -0.005),
        bodyLarge = style(text, FontWeight.Normal, 16, 24, 0.0),
        bodyMedium = style(text, FontWeight.Normal, 14, 20, 0.0),
        bodySmall = style(text, FontWeight.Normal, 12, 16, 0.01),
        labelLarge = style(text, FontWeight.SemiBold, 15, 20, 0.0),
        labelMedium = style(text, FontWeight.Medium, 13, 18, 0.01),
        labelSmall = style(text, FontWeight.SemiBold, 11, 14, 0.04),
    )
}
