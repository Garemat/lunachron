package io.github.garemat.lunachron.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import io.github.garemat.lunachron.AppTheme
import io.github.garemat.lunachron.LayoutDensity

/**
 * Legacy composition local kept while composables are migrated off direct AppTheme checks.
 * Derives its value from [activeThemeId]: "moonstone" → MOONSTONE, anything else → DEFAULT.
 * Remove once task 6 (Remove AppTheme.MOONSTONE checks) is complete.
 */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.DEFAULT }

@Composable
fun LunachronTheme(
    activeThemeId: String = "default",
    layoutDensity: LayoutDensity = LayoutDensity.COZY,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val repo = remember(context) { ThemeRepository(context) }
    val definition = remember(activeThemeId) { repo.resolve(activeThemeId) }

    val displayFamily = remember(definition.fonts?.display) {
        repo.buildFontFamily(definition.fonts?.display, fallback = androidx.compose.ui.text.font.FontFamily.Default)
    }
    val bodyFamily = remember(definition.fonts?.body) {
        repo.buildFontFamily(definition.fonts?.body, fallback = androidx.compose.ui.text.font.FontFamily.Default)
    }

    val typography = remember(definition, displayFamily, bodyFamily) {
        repo.buildTypography(definition, displayFamily, bodyFamily)
    }
    val colorScheme = remember(definition.colorScheme) {
        repo.buildColorScheme(definition.colorScheme!!)
    }

    // themeProperties must be computed INSIDE MaterialTheme so that
    // MaterialTheme.colorScheme.* resolves to the correct scheme.
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        val themeProperties = remember(definition, layoutDensity) {
            repo.buildAppThemeProperties(definition, layoutDensity, typography)
        }.run {
            // secondaryColor, unselectedNavColor, and cardBackground reference MaterialTheme
            // so they must be resolved here inside the MaterialTheme scope.
            copy(
                secondaryColor     = if (activeThemeId == "moonstone") MaterialTheme.colorScheme.secondary
                                     else MaterialTheme.colorScheme.primary,
                unselectedNavColor = if (activeThemeId == "moonstone") MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                cardBackground     = if (definition.characterCard?.showBackgroundImageOverlay == true)
                                         MaterialTheme.colorScheme.surfaceVariant
                                     else MaterialTheme.colorScheme.surface,
            )
        }

        // Derive legacy AppTheme for backward-compat CompositionLocal.
        val legacyTheme = if (activeThemeId == "moonstone") AppTheme.MOONSTONE else AppTheme.DEFAULT

        CompositionLocalProvider(
            LocalAppTheme provides legacyTheme,
            LocalAppThemeProperties provides themeProperties,
            content = content
        )
    }
}
