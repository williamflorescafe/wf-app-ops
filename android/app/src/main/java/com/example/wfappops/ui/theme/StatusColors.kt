package com.example.wfappops.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Tono visual de un estado (por ejemplo, el de una marcación).
 *
 * La app no decide si un día es tardanza o falta: cada feature traduce el `code` que envía la API a un
 * tono, y el texto mostrado siempre es el `label` de la API. Así el color nunca es la única señal.
 */
internal enum class StatusTone {
    /** Correcto o a tiempo. */
    POSITIVE,

    /** Requiere atención (por ejemplo, tardanza). */
    WARNING,

    /** Problema (por ejemplo, falta). */
    CRITICAL,

    /** Informativo (por ejemplo, justificado o feriado). */
    INFO,

    /** Sin connotación o estado desconocido. Es el valor por defecto para códigos nuevos. */
    NEUTRAL,
}

/** Par de colores de un [StatusTone]: fondo suave y contenido (texto e ícono). */
@Immutable
internal data class StatusColorPair(val container: Color, val content: Color)

/** Colores de estado del tema, que Material 3 no define. */
@Immutable
internal data class StatusColors(
    val positive: StatusColorPair,
    val warning: StatusColorPair,
    val critical: StatusColorPair,
    val info: StatusColorPair,
    val neutral: StatusColorPair,
) {
    /** Devuelve los colores de [tone]. */
    fun of(tone: StatusTone): StatusColorPair = when (tone) {
        StatusTone.POSITIVE -> positive
        StatusTone.WARNING -> warning
        StatusTone.CRITICAL -> critical
        StatusTone.INFO -> info
        StatusTone.NEUTRAL -> neutral
    }
}

/** Colores de estado del tema claro. */
internal val LightStatusColors = StatusColors(
    positive = StatusColorPair(ColorTokens.Success.copy(alpha = 0.20f).compositeOver(ColorTokens.Surface), ColorTokens.OnSupport),
    warning = StatusColorPair(ColorTokens.WarningSoft, ColorTokens.OnWarning),
    critical = StatusColorPair(ColorTokens.DangerSoft, ColorTokens.DangerDeep),
    info = StatusColorPair(ColorTokens.InfoContainer, ColorTokens.OnInfoContainer),
    neutral = StatusColorPair(ColorTokens.Sunken, ColorTokens.Muted),
)

/** Acceso a [StatusColors] desde los componentes; lo provee [WfTheme]. */
internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
