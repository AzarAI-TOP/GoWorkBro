@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.azarai.goworkbro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.R

/** Font setting ids persisted in user_settings (`font_family`). */
object FontChoice {
    const val SYSTEM = "system"
    const val PROTO = "proto"
    const val WENKAI = "wenkai"
    const val NOTO = "noto"

    val ALL = listOf(SYSTEM, PROTO, WENKAI, NOTO)
}

private val ProtoFamily = FontFamily(
    Font(R.font.proto_regular, weight = FontWeight.Normal),
    Font(R.font.proto_bold, weight = FontWeight.Bold),
    Font(R.font.proto_bold, weight = FontWeight.SemiBold),
    Font(R.font.proto_bold, weight = FontWeight.W600),
)

private val WenKaiFamily = FontFamily(
    Font(R.font.lxgw_wenkai, weight = FontWeight.Normal),
    Font(R.font.lxgw_wenkai, weight = FontWeight.Bold),
    Font(R.font.lxgw_wenkai, weight = FontWeight.SemiBold),
    Font(R.font.lxgw_wenkai, weight = FontWeight.W600),
)

private val NotoSansScFamily = FontFamily(
    Font(
        R.font.noto_sans_sc,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.noto_sans_sc,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.noto_sans_sc,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** The reading font for USTC news rendering (v1 parity: 霞鹜文楷). */
val NewsFontFamily = WenKaiFamily

fun familyFor(choice: String): FontFamily = when (choice) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.WENKAI -> WenKaiFamily
    FontChoice.NOTO -> NotoSansScFamily
    else -> ProtoFamily
}

/** v1 text-style sizing (see app_theme.dart) on top of Material 3 roles. */
fun appTypography(fontChoice: String): Typography {
    val family = familyFor(fontChoice)
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = family,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 15.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = family, fontSize = 12.sp),
        labelLarge = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}
