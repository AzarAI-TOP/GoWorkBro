@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.azarai.goworkbro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.R

/** Extra semantic colors that ride alongside the M3 scheme. */
data class ForestExtras(
    val scaffold: Color,
    val card: Color,
    val cardBorder: Color,
    val inputFill: Color,
    val divider: Color,
    val textSecondary: Color,
    val pastels: List<Color>,
)

val LocalForestExtras = staticCompositionLocalOf {
    ForestExtras(
        scaffold = ForestScaffoldLight,
        card = ForestCardLight,
        cardBorder = ForestCardBorderLight,
        inputFill = ForestInputFillLight,
        divider = ForestDividerLight,
        textSecondary = ForestTextSecondaryLight,
        pastels = PastelPalette,
    )
}

/** The cute rounded font shipped with the app. */
val CuteFont = FontFamily(
    Font(R.font.proto_regular, weight = FontWeight.Normal),
    Font(R.font.proto_bold, weight = FontWeight.SemiBold),
    Font(R.font.proto_bold, weight = FontWeight.Bold),
)

val ForestTypography = Typography(
    headlineLarge = TextStyle(fontFamily = CuteFont, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = CuteFont, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = CuteFont, fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = CuteFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = CuteFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = CuteFont, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = CuteFont, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = CuteFont, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = CuteFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = CuteFont, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = CuteFont, fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

val ForestShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = ForestOnPrimary,
    primaryContainer = ForestPrimaryContainer,
    onPrimaryContainer = ForestOnPrimaryContainer,
    secondary = ForestSecondary,
    onSecondary = ForestOnSecondary,
    secondaryContainer = ForestSecondaryContainer,
    onSecondaryContainer = ForestOnSecondaryContainer,
    tertiary = ForestTertiary,
    onTertiary = ForestOnTertiary,
    tertiaryContainer = ForestTertiaryContainer,
    onTertiaryContainer = ForestOnTertiaryContainer,
    background = ForestScaffoldLight,
    onBackground = ForestTextPrimaryLight,
    surface = ForestCardLight,
    onSurface = ForestTextPrimaryLight,
    surfaceVariant = ForestCardBorderLight,
    onSurfaceVariant = ForestTextSecondaryLight,
    outlineVariant = ForestDividerLight,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = ForestPrimaryDark,
    onPrimary = ForestOnPrimaryDark,
    primaryContainer = ForestPrimaryContainerDark,
    onPrimaryContainer = ForestOnPrimaryContainerDark,
    secondary = ForestSecondaryDark,
    onSecondary = ForestOnSecondaryDark,
    secondaryContainer = ForestSecondaryContainerDark,
    onSecondaryContainer = ForestOnSecondaryContainerDark,
    tertiary = ForestTertiaryDark,
    onTertiary = ForestOnTertiaryDark,
    tertiaryContainer = ForestTertiaryContainerDark,
    onTertiaryContainer = ForestOnTertiaryContainerDark,
    background = ForestScaffoldDark,
    onBackground = ForestTextPrimaryDark,
    surface = ForestCardDark,
    onSurface = ForestTextPrimaryDark,
    surfaceVariant = ForestCardBorderDark,
    onSurfaceVariant = ForestTextSecondaryDark,
    outlineVariant = ForestDividerDark,
)

/** Forest theme — light/night follows the system. */
@Composable
fun GoWorkBroTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkScheme else LightScheme
    val extras = if (dark) {
        ForestExtras(
            scaffold = ForestScaffoldDark,
            card = ForestCardDark,
            cardBorder = ForestCardBorderDark,
            inputFill = ForestInputFillDark,
            divider = ForestDividerDark,
            textSecondary = ForestTextSecondaryDark,
            pastels = PastelPaletteDark,
        )
    } else {
        ForestExtras(
            scaffold = ForestScaffoldLight,
            card = ForestCardLight,
            cardBorder = ForestCardBorderLight,
            inputFill = ForestInputFillLight,
            divider = ForestDividerLight,
            textSecondary = ForestTextSecondaryLight,
            pastels = PastelPalette,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalForestExtras provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ForestTypography,
            shapes = ForestShapes,
            content = content,
        )
    }
}
