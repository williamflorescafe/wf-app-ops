package com.example.wfappops.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.compositeOver

private val WfLightColorScheme = lightColorScheme(
    primary = ColorTokens.Primary,
    onPrimary = ColorTokens.OnPrimary,
    primaryContainer = ColorTokens.PrimarySoft,
    onPrimaryContainer = ColorTokens.PrimaryDeep,
    inversePrimary = ColorTokens.PrimarySoft,
    secondary = ColorTokens.Secondary,
    onSecondary = ColorTokens.OnSecondary,
    secondaryContainer = ColorTokens.SurfaceWarm,
    onSecondaryContainer = ColorTokens.SecondaryDeep,
    tertiary = ColorTokens.SupportDeep,
    onTertiary = ColorTokens.OnPrimary,
    tertiaryContainer = ColorTokens.Support.copy(alpha = 0.20f).compositeOver(ColorTokens.Surface),
    onTertiaryContainer = ColorTokens.OnSupport,
    background = ColorTokens.Canvas,
    onBackground = ColorTokens.Ink,
    surface = ColorTokens.Surface,
    onSurface = ColorTokens.Ink,
    surfaceVariant = ColorTokens.Sunken,
    onSurfaceVariant = ColorTokens.Muted,
    surfaceTint = ColorTokens.Primary,
    surfaceBright = ColorTokens.Surface,
    surfaceDim = ColorTokens.Line,
    surfaceContainerLowest = ColorTokens.Surface,
    surfaceContainerLow = ColorTokens.Canvas,
    surfaceContainer = ColorTokens.Sunken,
    surfaceContainerHigh = ColorTokens.Sunken,
    surfaceContainerHighest = ColorTokens.Line,
    inverseSurface = ColorTokens.PrimaryDeep,
    inverseOnSurface = ColorTokens.OnPrimary,
    outline = ColorTokens.FieldLine,
    outlineVariant = ColorTokens.Line,
    error = ColorTokens.DangerDeep,
    onError = ColorTokens.OnDanger,
    errorContainer = ColorTokens.DangerSoft,
    onErrorContainer = ColorTokens.DangerDeep,
    scrim = ColorTokens.Ink,
)

@Composable
internal fun WfTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides LightStatusColors) {
        MaterialTheme(
            colorScheme = WfLightColorScheme,
            typography = wfTypography(),
            shapes = WfShapes,
            content = content,
        )
    }
}

/** Roles de Web Store que no forman parte del esquema de Material 3. */
internal object WfThemeExtras {
    val statusColors: StatusColors
        @Composable get() = LocalStatusColors.current

    val cartaCanvas = ColorTokens.CartaCanvas
    val activeIndicator = ColorTokens.ActiveIndicator
    val warmSurface = ColorTokens.SurfaceWarm
    val onAdvanceAction = ColorTokens.SurfaceWarm
    val logoutAction = ColorTokens.Logout
    val logoutSurface = ColorTokens.LogoutSoft
    val campaignSurface = ColorTokens.SurfaceCampaign
    val purchaseAction = ColorTokens.Accent
    val onPurchaseAction = ColorTokens.OnAccent
    val selectedOption = ColorTokens.AccentSoft
    val onSelectedOption = ColorTokens.AccentInk
}
