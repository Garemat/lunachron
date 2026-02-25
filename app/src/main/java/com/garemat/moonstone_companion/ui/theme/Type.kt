package com.garemat.moonstone_companion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

private val MoonstonePlatformStyle = PlatformTextStyle(
    includeFontPadding = false
)

private val MoonstoneLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

// Dedicated Button Style to ensure vertical centering and consistent look
val MoonstoneButtonTextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    textAlign = TextAlign.Center,
    platformStyle = MoonstonePlatformStyle,
    lineHeightStyle = MoonstoneLineHeightStyle
)

val MoonstoneTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    labelLarge = MoonstoneButtonTextStyle,
    labelMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    )
)

val DefaultTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Start,
        platformStyle = MoonstonePlatformStyle,
        lineHeightStyle = MoonstoneLineHeightStyle
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
)
