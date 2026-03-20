package io.github.garemat.lunachron.ui.theme

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
import io.github.garemat.lunachron.AppTheme
import io.github.garemat.lunachron.LayoutDensity

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
    val screenPadding: Dp,
    val showFactionBackgrounds: Boolean = false,
    // Semantic game / UI colors
    val moonstoneColor: Color = Color(0xFF2196F3),
    val positiveColor: Color = Color(0xFF4CAF50),
    val rankingGoldColor: Color = Color(0xFFFFD700),
    val rankingSilverColor: Color = Color(0xFFC0C0C0),
    val rankingBronzeColor: Color = Color(0xFFCD7F32),
    val readyColor: Color = Color(0xFF2E7D32),
    val scoreCircleColor: Color = Color(0xFF1976D2),
    val cardBackground: Color = Color.Unspecified,
    // Game mechanic colors
    val magicalDamageColor: Color = Color(0xFF00B0FF),
    val followUpHighlightColor: Color = Color(0xFFFFEB3B),
    val arcaneGreenColor: Color = Color(0xFF2E7D32),
    val arcaneBlueColor: Color = Color(0xFF1565C0),
    val arcanePurpleColor: Color = Color(0xFFC2185B),
    val catastropheColor: Color = Color.Red,
    // Faction identity colors
    val factionCommonwealth: Color = Color(0xFFFBC02D),
    val factionDominion: Color = Color(0xFF1976D2),
    val factionLeshavult: Color = Color(0xFF388E3C),
    val factionShades: Color = Color(0xFF424242),
    // Character card behaviour flags
    val showBackgroundImageOverlay: Boolean = false,
    val showExpandedStatsHeader: Boolean = false,
    val showCardDivider: Boolean = true,
    val useDamageTypeIcons: Boolean = false,
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
        verticalSpacing = 16.dp,
        screenPadding = 16.dp
    )
}

@Composable
fun getThemeProperties(appTheme: AppTheme, density: LayoutDensity): AppThemeProperties {
    val spacing = when (density) {
        LayoutDensity.COMPACT -> 8.dp
        LayoutDensity.COZY -> 16.dp
        LayoutDensity.SPACIOUS -> 24.dp
    }
    
    val cardPadding = when (density) {
        LayoutDensity.COMPACT -> 8.dp
        LayoutDensity.COZY -> 12.dp
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
            cardContentPadding = cardPadding,
            verticalSpacing = spacing,
            screenPadding = spacing,
            showFactionBackgrounds = true,
            cardBackground = MaterialTheme.colorScheme.surfaceVariant
        )
        AppTheme.DEFAULT -> AppThemeProperties(
            cardShape = RoundedCornerShape(12.dp),
            drawerShape = DrawerDefaults.shape,
            navItemShape = CircleShape,
            titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            headerStyle = MaterialTheme.typography.headlineMedium,
            labelStyle = MaterialTheme.typography.labelSmall,
            buttonTextStyle = MaterialTheme.typography.labelLarge,
            secondaryColor = MaterialTheme.colorScheme.primary,
            unselectedNavColor = MaterialTheme.colorScheme.onSurfaceVariant,
            surfaceElevation = 4.dp,
            navigationBarElevation = 3.dp,
            cardContentPadding = cardPadding,
            verticalSpacing = spacing,
            screenPadding = spacing,
            showFactionBackgrounds = false,
            cardBackground = MaterialTheme.colorScheme.surface
        )
    }
}
