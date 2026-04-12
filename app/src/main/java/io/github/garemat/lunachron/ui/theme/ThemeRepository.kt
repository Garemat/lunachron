package io.github.garemat.lunachron.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads, resolves, and materialises [ThemeDefinition]s into runtime types.
 *
 * Resolution order for a given theme ID:
 *   1. Load the requested definition (assets/themes/ or filesDir/themes/).
 *   2. If it has an [ThemeDefinition.extends] field, load that base (one level only).
 *   3. Merge: child non-null fields win; null fields fall through to the base.
 *   4. Any remaining nulls fall through to the built-in "default" definition.
 *
 * Built-in themes ("default", "moonstone") are fully specified with no nulls.
 */
class ThemeRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    // Cache so we don't re-parse on every recomposition.
    private val cache = mutableMapOf<String, ThemeDefinition>()

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    fun resolve(id: String): ThemeDefinition {
        val child = load(id)
        val baseId = child.extends ?: if (id == "default") null else "default"
        val base = baseId?.let { load(it) }
        val merged = if (base != null) child.mergeOver(base) else child
        // Final safety net: merge over default so no field is ever null.
        return if (id == "default") merged else merged.mergeOver(load("default"))
    }

    fun listAvailable(): List<ThemeDefinition> {
        val assetNames = context.assets.list("themes")
            ?.filter { it.endsWith(".json") }
            ?.map { it.removeSuffix(".json") }
            ?: emptyList()
        val userNames = File(context.filesDir, "themes")
            .takeIf { it.exists() }
            ?.list()
            ?.filter { it.endsWith(".json") }
            ?.map { it.removeSuffix(".json") }
            ?: emptyList()
        return (assetNames + userNames).distinct().mapNotNull { runCatching { load(it) }.getOrNull() }
    }

    // ---------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------

    private fun load(id: String): ThemeDefinition {
        cache[id]?.let { return it }
        val raw = loadRaw(id)
        return json.decodeFromString<ThemeDefinition>(raw).also { cache[id] = it }
    }

    private fun loadRaw(id: String): String {
        // User themes take precedence over bundled ones.
        val userFile = File(context.filesDir, "themes/$id.json")
        if (userFile.exists()) return userFile.readText()
        return context.assets.open("themes/$id.json").bufferedReader().readText()
    }

    // ---------------------------------------------------------------------------
    // Materialisation
    // ---------------------------------------------------------------------------

    /** Build a [FontFamily] from a font name string. Falls back gracefully. */
    fun buildFontFamily(name: String?, fallback: FontFamily): FontFamily {
        if (name == null) return fallback
        return when (name.lowercase()) {
            "default", "sans-serif" -> FontFamily.Default
            "serif"                 -> FontFamily.Serif
            "monospace"             -> FontFamily.Monospace
            "cursive"               -> FontFamily.Cursive
            else                    -> loadBundledFont(name) ?: fallback
        }
    }

    private fun loadBundledFont(name: String): FontFamily? {
        val dir = "fonts/${name.lowercase()}"
        val files = runCatching { context.assets.list(dir) }.getOrNull() ?: return null
        val fonts = mutableListOf<Font>()
        files.filter { it.endsWith(".ttf") || it.endsWith(".otf") }.forEach { file ->
            // Infer weight from filename: contains "Bold" → Bold, else Normal.
            val weight = when {
                file.contains("Bold", ignoreCase = true)  -> FontWeight.Bold
                file.contains("Light", ignoreCase = true) -> FontWeight.Light
                else                                       -> FontWeight.Normal
            }
            runCatching {
                fonts += Font("$dir/$file", assetManager = context.assets, weight = weight)
            }
        }
        return if (fonts.isNotEmpty()) FontFamily(fonts) else null
    }

    fun buildTypography(def: ThemeDefinition, displayFamily: FontFamily, bodyFamily: FontFamily): Typography {
        fun weight(s: String?) = when (s?.lowercase()) {
            "thin"       -> FontWeight.Thin
            "extralight" -> FontWeight.ExtraLight
            "light"      -> FontWeight.Light
            "medium"     -> FontWeight.Medium
            "semibold"   -> FontWeight.SemiBold
            "bold"       -> FontWeight.Bold
            "extrabold"  -> FontWeight.ExtraBold
            "black"      -> FontWeight.Black
            else         -> FontWeight.Normal
        }

        // Build every M3 slot using the resolved font families and any size/weight overrides.
        // Display/headline/title → displayFamily; body/label → bodyFamily.
        fun display(sizeSp: Float, fw: FontWeight = FontWeight.Bold) =
            TextStyle(fontFamily = displayFamily, fontWeight = fw, fontSize = sizeSp.sp, textAlign = TextAlign.Start)

        fun body(sizeSp: Float, fw: FontWeight = FontWeight.Normal) =
            TextStyle(fontFamily = bodyFamily, fontWeight = fw, fontSize = sizeSp.sp, textAlign = TextAlign.Start)

        return Typography(
            displayLarge   = display(57f),
            displayMedium  = display(45f),
            displaySmall   = display(36f),
            headlineLarge  = display(32f),
            headlineMedium = display(28f),
            headlineSmall  = display(24f),
            titleLarge     = display(22f),
            titleMedium    = display(16f, FontWeight.Medium),
            titleSmall     = display(14f, FontWeight.Medium),
            bodyLarge      = body(16f),
            bodyMedium     = body(14f),
            bodySmall      = body(12f),
            labelLarge     = body(14f, FontWeight.Bold),
            labelMedium    = body(12f, FontWeight.Bold),
            labelSmall     = body(11f, FontWeight.Bold),
        )
    }

    fun buildColorScheme(def: ColorSchemeDefinition) =
        if (def.isDark == true) buildDarkScheme(def) else buildLightScheme(def)

    private fun buildLightScheme(d: ColorSchemeDefinition) = lightColorScheme(
        primary              = d.primary?.toColor()            ?: Color(0xFF6650A4),
        onPrimary            = d.onPrimary?.toColor()          ?: Color.White,
        primaryContainer     = d.primaryContainer?.toColor()   ?: Color(0xFFEADDFF),
        onPrimaryContainer   = d.onPrimaryContainer?.toColor() ?: Color(0xFF21005D),
        secondary            = d.secondary?.toColor()          ?: Color(0xFF625B71),
        onSecondary          = d.onSecondary?.toColor()        ?: Color.White,
        secondaryContainer   = d.secondaryContainer?.toColor() ?: Color(0xFFE8DEF8),
        onSecondaryContainer = d.onSecondaryContainer?.toColor() ?: Color(0xFF1D192B),
        surface              = d.surface?.toColor()            ?: Color(0xFFFFFBFE),
        onSurface            = d.onSurface?.toColor()          ?: Color(0xFF1C1B1F),
        surfaceVariant       = d.surfaceVariant?.toColor()     ?: Color(0xFFE7E0EC),
        onSurfaceVariant     = d.onSurfaceVariant?.toColor()   ?: Color(0xFF49454F),
        background           = d.background?.toColor()         ?: Color(0xFFFFFBFE),
        onBackground         = d.onBackground?.toColor()       ?: Color(0xFF1C1B1F),
        outline              = d.outline?.toColor()            ?: Color(0xFF79747E),
        error                = d.error?.toColor()              ?: Color(0xFFB3261E),
        onError              = d.onError?.toColor()            ?: Color.White,
    )

    private fun buildDarkScheme(d: ColorSchemeDefinition) = darkColorScheme(
        primary              = d.primary?.toColor()            ?: Color(0xFFD0BCFF),
        onPrimary            = d.onPrimary?.toColor()          ?: Color(0xFF381E72),
        primaryContainer     = d.primaryContainer?.toColor()   ?: Color(0xFF4F378B),
        onPrimaryContainer   = d.onPrimaryContainer?.toColor() ?: Color(0xFFEADDFF),
        secondary            = d.secondary?.toColor()          ?: Color(0xFFCCC2DC),
        onSecondary          = d.onSecondary?.toColor()        ?: Color(0xFF332D41),
        secondaryContainer   = d.secondaryContainer?.toColor() ?: Color(0xFF4A4458),
        onSecondaryContainer = d.onSecondaryContainer?.toColor() ?: Color(0xFFE8DEF8),
        surface              = d.surface?.toColor()            ?: Color(0xFF1C1B1F),
        onSurface            = d.onSurface?.toColor()          ?: Color(0xFFE6E1E5),
        surfaceVariant       = d.surfaceVariant?.toColor()     ?: Color(0xFF49454F),
        onSurfaceVariant     = d.onSurfaceVariant?.toColor()   ?: Color(0xFFCAC4D0),
        background           = d.background?.toColor()         ?: Color(0xFF1C1B1F),
        onBackground         = d.onBackground?.toColor()       ?: Color(0xFFE6E1E5),
        outline              = d.outline?.toColor()            ?: Color(0xFF938F99),
        error                = d.error?.toColor()              ?: Color(0xFFF2B8B5),
        onError              = d.onError?.toColor()            ?: Color(0xFF601410),
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Parse a #RRGGBB or #AARRGGBB hex string to [Color].
 * Returns [Color.Unspecified] on failure so callers can detect bad values.
 */
fun String.toColor(): Color = try {
    val hex = this.trimStart('#')
    when (hex.length) {
        6 -> Color(android.graphics.Color.parseColor("#$hex"))
        8 -> Color(android.graphics.Color.parseColor("#$hex"))
        else -> Color.Unspecified
    }
} catch (_: Exception) {
    Color.Unspecified
}

/**
 * Merge this [ThemeDefinition] over [base]: every non-null field in this wins;
 * null fields fall through to [base]. Nested sub-definitions are merged recursively.
 */
fun ThemeDefinition.mergeOver(base: ThemeDefinition) = ThemeDefinition(
    id       = id,
    name     = name,
    extends  = extends ?: base.extends,
    fonts    = fonts.mergeOver(base.fonts),
    colorScheme    = colorScheme.mergeOver(base.colorScheme),
    factionColors  = factionColors.mergeOver(base.factionColors),
    shapes         = shapes.mergeOver(base.shapes),
    spacing        = spacing.mergeOver(base.spacing),
    typography     = typography.mergeOver(base.typography),
    gameColors     = gameColors.mergeOver(base.gameColors),
    characterCard  = characterCard.mergeOver(base.characterCard),
    showFactionBackgrounds = showFactionBackgrounds ?: base.showFactionBackgrounds,
    gameplayPreferences = gameplayPreferences.mergeOver(base.gameplayPreferences),
)

private fun FontDefinition?.mergeOver(base: FontDefinition?) = FontDefinition(
    display = this?.display ?: base?.display,
    body    = this?.body    ?: base?.body,
    mono    = this?.mono    ?: base?.mono,
)

private fun ColorSchemeDefinition?.mergeOver(base: ColorSchemeDefinition?) = ColorSchemeDefinition(
    primary              = this?.primary              ?: base?.primary,
    onPrimary            = this?.onPrimary            ?: base?.onPrimary,
    primaryContainer     = this?.primaryContainer     ?: base?.primaryContainer,
    onPrimaryContainer   = this?.onPrimaryContainer   ?: base?.onPrimaryContainer,
    secondary            = this?.secondary            ?: base?.secondary,
    onSecondary          = this?.onSecondary          ?: base?.onSecondary,
    secondaryContainer   = this?.secondaryContainer   ?: base?.secondaryContainer,
    onSecondaryContainer = this?.onSecondaryContainer ?: base?.onSecondaryContainer,
    surface              = this?.surface              ?: base?.surface,
    onSurface            = this?.onSurface            ?: base?.onSurface,
    surfaceVariant       = this?.surfaceVariant       ?: base?.surfaceVariant,
    onSurfaceVariant     = this?.onSurfaceVariant     ?: base?.onSurfaceVariant,
    background           = this?.background           ?: base?.background,
    onBackground         = this?.onBackground         ?: base?.onBackground,
    outline              = this?.outline              ?: base?.outline,
    error                = this?.error                ?: base?.error,
    onError              = this?.onError              ?: base?.onError,
    isDark               = this?.isDark               ?: base?.isDark,
)

private fun FactionColorDefinition?.mergeOver(base: FactionColorDefinition?) = FactionColorDefinition(
    commonwealth = this?.commonwealth ?: base?.commonwealth,
    dominion     = this?.dominion     ?: base?.dominion,
    leshavult    = this?.leshavult    ?: base?.leshavult,
    shades       = this?.shades       ?: base?.shades,
)

private fun ShapeDefinition?.mergeOver(base: ShapeDefinition?) = ShapeDefinition(
    cardCornerRadius = this?.cardCornerRadius ?: base?.cardCornerRadius,
    navItemShape     = this?.navItemShape     ?: base?.navItemShape,
    drawerShape      = this?.drawerShape      ?: base?.drawerShape,
)

private fun SpacingDefinition?.mergeOver(base: SpacingDefinition?) = SpacingDefinition(
    compact             = this?.compact             ?: base?.compact,
    cozy                = this?.cozy                ?: base?.cozy,
    spacious            = this?.spacious            ?: base?.spacious,
    cardPaddingCompact  = this?.cardPaddingCompact  ?: base?.cardPaddingCompact,
    cardPaddingCozy     = this?.cardPaddingCozy     ?: base?.cardPaddingCozy,
    cardPaddingSpacious = this?.cardPaddingSpacious ?: base?.cardPaddingSpacious,
)

private fun TypographyStyleRef?.mergeOver(base: TypographyStyleRef?) = TypographyStyleRef(
    base   = this?.base   ?: base?.base,
    sizeSp = this?.sizeSp ?: base?.sizeSp,
    weight = this?.weight ?: base?.weight,
)

private fun TypographyDefinition?.mergeOver(base: TypographyDefinition?) = TypographyDefinition(
    titleStyle      = this?.titleStyle.mergeOver(base?.titleStyle),
    headerStyle     = this?.headerStyle.mergeOver(base?.headerStyle),
    labelStyle      = this?.labelStyle.mergeOver(base?.labelStyle),
    buttonTextStyle = this?.buttonTextStyle.mergeOver(base?.buttonTextStyle),
)

private fun GameColorDefinition?.mergeOver(base: GameColorDefinition?) = GameColorDefinition(
    moonstone         = this?.moonstone         ?: base?.moonstone,
    positive          = this?.positive          ?: base?.positive,
    rankingGold       = this?.rankingGold       ?: base?.rankingGold,
    rankingSilver     = this?.rankingSilver     ?: base?.rankingSilver,
    rankingBronze     = this?.rankingBronze     ?: base?.rankingBronze,
    ready             = this?.ready             ?: base?.ready,
    scoreCircle       = this?.scoreCircle       ?: base?.scoreCircle,
    magicalDamage     = this?.magicalDamage     ?: base?.magicalDamage,
    followUpHighlight = this?.followUpHighlight ?: base?.followUpHighlight,
    arcaneGreen       = this?.arcaneGreen       ?: base?.arcaneGreen,
    arcaneBlue        = this?.arcaneBlue        ?: base?.arcaneBlue,
    arcanePurple      = this?.arcanePurple      ?: base?.arcanePurple,
    catastrophe       = this?.catastrophe       ?: base?.catastrophe,
)

private fun CharacterCardDefinition?.mergeOver(base: CharacterCardDefinition?) = CharacterCardDefinition(
    showBackgroundImageOverlay = this?.showBackgroundImageOverlay ?: base?.showBackgroundImageOverlay,
    showExpandedStatsHeader    = this?.showExpandedStatsHeader    ?: base?.showExpandedStatsHeader,
    showCardDivider            = this?.showCardDivider            ?: base?.showCardDivider,
    useDamageTypeIcons         = this?.useDamageTypeIcons         ?: base?.useDamageTypeIcons,
)

private fun GameplayPreferencesDefinition?.mergeOver(base: GameplayPreferencesDefinition?) =
    GameplayPreferencesDefinition(
        defaultTrackingMode = this?.defaultTrackingMode ?: base?.defaultTrackingMode,
    )

// ---------------------------------------------------------------------------
// AppThemeProperties builder — converts resolved ThemeDefinition into the
// runtime AppThemeProperties used throughout the composable layer.
// ---------------------------------------------------------------------------

fun ThemeRepository.buildAppThemeProperties(
    def: ThemeDefinition,
    density: io.github.garemat.lunachron.LayoutDensity,
    typography: Typography,
): AppThemeProperties {
    val spacing = def.spacing!!
    val spacingDp = when (density) {
        io.github.garemat.lunachron.LayoutDensity.COMPACT  -> (spacing.compact  ?: 8f).dp
        io.github.garemat.lunachron.LayoutDensity.COZY     -> (spacing.cozy     ?: 16f).dp
        io.github.garemat.lunachron.LayoutDensity.SPACIOUS -> (spacing.spacious ?: 24f).dp
    }
    val cardPadding = when (density) {
        io.github.garemat.lunachron.LayoutDensity.COMPACT  -> (spacing.cardPaddingCompact  ?: 8f).dp
        io.github.garemat.lunachron.LayoutDensity.COZY     -> (spacing.cardPaddingCozy     ?: 12f).dp
        io.github.garemat.lunachron.LayoutDensity.SPACIOUS -> (spacing.cardPaddingSpacious ?: 16f).dp
    }
    val shapes = def.shapes!!
    val cardRadius = (shapes.cardCornerRadius ?: 12f).dp
    val cardShape = RoundedCornerShape(cardRadius)
    fun resolveShape(name: String?) = when (name?.lowercase()) {
        "circle"  -> CircleShape
        "rect"    -> RoundedCornerShape(0.dp)
        else      -> RoundedCornerShape(cardRadius) // "rounded" or any unrecognised value
    }

    val gc = def.gameColors!!
    val fc = def.factionColors!!
    val cc = def.characterCard!!

    return AppThemeProperties(
        cardShape              = cardShape,
        drawerShape            = resolveShape(shapes.drawerShape),
        navItemShape           = resolveShape(shapes.navItemShape),
        titleStyle             = typography.titleLarge,
        headerStyle            = typography.headlineMedium,
        labelStyle             = typography.labelSmall,
        buttonTextStyle        = typography.labelLarge,
        secondaryColor         = Color.Unspecified, // resolved from MaterialTheme at call site
        unselectedNavColor     = Color.Unspecified, // resolved from MaterialTheme at call site
        surfaceElevation       = if (cardRadius == 0.dp) 0.dp else 4.dp,
        navigationBarElevation = if (cardRadius == 0.dp) 0.dp else 3.dp,
        cardContentPadding     = cardPadding,
        verticalSpacing        = spacingDp,
        screenPadding          = spacingDp,
        showFactionBackgrounds = def.showFactionBackgrounds ?: false,
        moonstoneColor         = gc.moonstone?.toColor()         ?: Color(0xFF2196F3),
        positiveColor          = gc.positive?.toColor()          ?: Color(0xFF4CAF50),
        rankingGoldColor       = gc.rankingGold?.toColor()       ?: Color(0xFFFFD700),
        rankingSilverColor     = gc.rankingSilver?.toColor()     ?: Color(0xFFC0C0C0),
        rankingBronzeColor     = gc.rankingBronze?.toColor()     ?: Color(0xFFCD7F32),
        readyColor             = gc.ready?.toColor()             ?: Color(0xFF2E7D32),
        scoreCircleColor       = gc.scoreCircle?.toColor()       ?: Color(0xFF1976D2),
        magicalDamageColor     = gc.magicalDamage?.toColor()     ?: Color(0xFF00B0FF),
        followUpHighlightColor = gc.followUpHighlight?.toColor() ?: Color(0xFFFFEB3B),
        arcaneGreenColor       = gc.arcaneGreen?.toColor()       ?: Color(0xFF2E7D32),
        arcaneBlueColor        = gc.arcaneBlue?.toColor()        ?: Color(0xFF1565C0),
        arcanePurpleColor      = gc.arcanePurple?.toColor()      ?: Color(0xFFC2185B),
        catastropheColor       = gc.catastrophe?.toColor()       ?: Color.Red,
        factionCommonwealth    = fc.commonwealth?.toColor()      ?: Color(0xFFFBC02D),
        factionDominion        = fc.dominion?.toColor()          ?: Color(0xFF1976D2),
        factionLeshavult       = fc.leshavult?.toColor()         ?: Color(0xFF388E3C),
        factionShades          = fc.shades?.toColor()            ?: Color(0xFF424242),
        showBackgroundImageOverlay = cc.showBackgroundImageOverlay ?: false,
        showExpandedStatsHeader    = cc.showExpandedStatsHeader    ?: false,
        showCardDivider            = cc.showCardDivider            ?: true,
        useDamageTypeIcons         = cc.useDamageTypeIcons         ?: false,
        cardBackground             = Color.Unspecified, // resolved from MaterialTheme at call site
    )
}
