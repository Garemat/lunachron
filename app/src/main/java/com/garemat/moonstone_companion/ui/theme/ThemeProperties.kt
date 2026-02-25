package com.garemat.moonstone_companion.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.AppTheme
import com.garemat.moonstone_companion.LayoutDensity

@Immutable
data class AppThemeProperties(
    val cardShape: Shape,
    val drawerShape: Shape,
    val navItemShape: Shape,
    val titleStyle: TextStyle,
    val headerStyle: TextStyle,
    val labelStyle: TextStyle,
    val buttonTextStyle: TextStyle,
    val secondaryColor: Color,
    val unselectedNavColor: Color,
    val surfaceElevation: Dp,
    val navigationBarElevation: Dp,
    val cardContentPadding: Dp,
    val verticalSpacing: Dp,
    val showFactionBackgrounds: Boolean = false
)

val LocalAppThemeProperties = staticCompositionLocalOf {
    AppThemeProperties(
        cardShape = RoundedCornerShape(12.dp),
        drawerShape = RoundedCornerShape(0.dp),
        navItemShape = CircleShape,
        titleStyle = TextStyle.Default,
        headerStyle = TextStyle.Default,
        labelStyle = TextStyle.Default,
        buttonTextStyle = TextStyle.Default,
        secondaryColor = Color.Unspecified,
        unselectedNavColor = Color.Unspecified,
        surfaceElevation = 4.dp,
        navigationBarElevation = 8.dp,
        cardContentPadding = 16.dp,
        verticalSpacing = 8.dp
    )
}

@Composable
fun getThemeProperties(appTheme: AppTheme, density: LayoutDensity): AppThemeProperties {
    val padding = when (density) {
        LayoutDensity.COMPACT -> 4.dp
        LayoutDensity.COZY -> 8.dp
        LayoutDensity.SPACIOUS -> 16.dp
    }
    
    val spacing = when (density) {
        LayoutDensity.COMPACT -> 4.dp
        LayoutDensity.COZY -> 8.dp
        LayoutDensity.SPACIOUS -> 16.dp
    }

    return when (appTheme) {
        AppTheme.MOONSTONE -> AppThemeProperties(
            cardShape = RoundedCornerShape(0.dp),
            drawerShape = RoundedCornerShape(0.dp),
            navItemShape = RoundedCornerShape(0.dp),
            titleStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            headerStyle = MaterialTheme.typography.displayLarge,
            labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            buttonTextStyle = MaterialTheme.typography.labelLarge,
            secondaryColor = MaterialTheme.colorScheme.secondary,
            unselectedNavColor = MaterialTheme.colorScheme.primary,
            surfaceElevation = 0.dp,
            navigationBarElevation = 0.dp,
            cardContentPadding = padding,
            verticalSpacing = spacing,
            showFactionBackgrounds = true
        )
        AppTheme.DEFAULT -> AppThemeProperties(
            cardShape = RoundedCornerShape(12.dp),
            drawerShape = DrawerDefaults.shape,
            navItemShape = CircleShape,
            titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            headerStyle = MaterialTheme.typography.headlineMedium,
            labelStyle = TextStyle.Default,
            buttonTextStyle = MaterialTheme.typography.labelLarge,
            secondaryColor = MaterialTheme.colorScheme.primary,
            unselectedNavColor = MaterialTheme.colorScheme.onSurfaceVariant,
            surfaceElevation = 4.dp,
            navigationBarElevation = 3.dp,
            cardContentPadding = padding,
            verticalSpacing = spacing,
            showFactionBackgrounds = false
        )
    }
}
