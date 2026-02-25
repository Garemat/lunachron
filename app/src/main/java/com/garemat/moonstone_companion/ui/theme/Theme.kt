package com.garemat.moonstone_companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.garemat.moonstone_companion.AppTheme
import com.garemat.moonstone_companion.LayoutDensity

val LocalAppTheme = staticCompositionLocalOf { AppTheme.DEFAULT }

private val MoonstoneColorScheme = lightColorScheme(
    primary = Color(0xFF2C1810),
    secondary = Color(0xFF8B4513),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E4BC),
    onPrimaryContainer = Color(0xFF2C1810),
    secondaryContainer = Color(0xFFEADBB0),
    onSecondaryContainer = Color(0xFF2C1810),
    surface = Color(0xFFF4E4BC),
    onSurface = Color(0xFF2C1810),
    surfaceVariant = Color(0xFFEADBB0),
    onSurfaceVariant = Color(0xFF2C1810),
    background = Color(0xFFF4E4BC),
    onBackground = Color(0xFF2C1810),
    outline = Color(0xFF2C1810).copy(alpha = 0.5f)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6650a4),
    secondary = Color(0xFF625b71),
    tertiary = Color(0xFF7D5260)
)

@Composable
fun MoonstonecompanionTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    layoutDensity: LayoutDensity = LayoutDensity.COZY,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.MOONSTONE -> MoonstoneColorScheme
        AppTheme.DEFAULT -> if (darkTheme) DarkColorScheme else LightColorScheme
    }
    
    val typography = when (appTheme) {
        AppTheme.MOONSTONE -> MoonstoneTypography
        AppTheme.DEFAULT -> DefaultTypography
    }

    val themeProperties = getThemeProperties(appTheme, layoutDensity)

    CompositionLocalProvider(
        LocalAppTheme provides appTheme,
        LocalAppThemeProperties provides themeProperties
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
