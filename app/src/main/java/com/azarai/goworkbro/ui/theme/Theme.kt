package com.azarai.goworkbro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Theme mode persisted in user_settings (`theme_mode`). */
object ThemeMode {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"
}

/** Extra named colors exposed alongside MaterialTheme.colorScheme (v1 parity). */
data class AppColors(
    val primary: Color,
    val secondary: Color,
    val scaffold: Color,
    val card: Color,
    val cardBorder: Color,
    val inputFill: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textBody: Color,
    val textMuted: Color,
    val textFaint: Color,
    val navUnselected: Color,
    val chart: List<Color>,
)

val LightAppColors = AppColors(
    primary = PrimaryLight,
    secondary = LightSecondary,
    scaffold = LightScaffold,
    card = LightCard,
    cardBorder = LightCardBorder,
    inputFill = LightInputFill,
    divider = LightDivider,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textBody = LightTextBody,
    textMuted = LightTextMuted,
    textFaint = LightTextFaint,
    navUnselected = LightNavUnselected,
    chart = ChartColors,
)

val DarkAppColors = AppColors(
    primary = PrimaryDark,
    secondary = DarkSecondary,
    scaffold = DarkScaffold,
    card = DarkCard,
    cardBorder = DarkCardBorder,
    inputFill = DarkInputFill,
    divider = DarkDivider,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textBody = DarkTextBody,
    textMuted = DarkTextMuted,
    textFaint = DarkTextFaint,
    navUnselected = DarkNavUnselected,
    chart = ChartColors,
)

fun appColors(dark: Boolean): AppColors = if (dark) DarkAppColors else LightAppColors

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

object AppTheme {
    /** Local composition accessor for the extended palette. */
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

/** Extended palette provider — set by [GoWorkBroTheme] so it follows the
 *  in-app theme mode, not just the system setting. */
val LocalAppColors = androidx.compose.runtime.staticCompositionLocalOf {
    LightAppColors
}

/** Theme wrapper applying font + extended colors; Material colorScheme carries
 *  the light/dark surfaces so M3 components style themselves correctly. */
@Composable
fun GoWorkBroTheme(
    themeMode: String,
    fontChoice: String,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val extended = appColors(dark)
    val colorScheme = if (dark) {
        androidx.compose.material3.darkColorScheme(
            primary = extended.primary,
            secondary = extended.secondary,
            background = extended.scaffold,
            surface = extended.card,
            surfaceVariant = extended.cardBorder,
            outlineVariant = extended.divider,
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = extended.primary,
            secondary = extended.secondary,
            background = extended.scaffold,
            surface = extended.card,
            surfaceVariant = extended.cardBorder,
            outlineVariant = extended.divider,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(fontChoice),
            shapes = AppShapes,
            content = content,
        )
    }
}
