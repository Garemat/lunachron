package io.github.garemat.lunachron.ui.theme

import kotlinx.serialization.Serializable

/**
 * Serialisable representation of a full app theme. All fields are nullable so that absent
 * JSON fields deserialise as null, enabling inheritance: ThemeRepository merges a child
 * definition over its base (resolved via [extends]), with non-null child fields winning.
 *
 * Built-in themes (id = "default", "moonstone") are fully specified with no nulls and
 * serve as the roots of the inheritance tree. Every custom theme ultimately falls back to
 * "default" for any field it does not specify.
 */
@Serializable
data class ThemeDefinition(
    val id: String,
    val name: String,
    /** ID of the theme this one extends. Absent = implicit "default" base. Max 1 level deep. */
    val extends: String? = null,
    val fonts: FontDefinition? = null,
    val colorScheme: ColorSchemeDefinition? = null,
    val factionColors: FactionColorDefinition? = null,
    val shapes: ShapeDefinition? = null,
    val spacing: SpacingDefinition? = null,
    val typography: TypographyDefinition? = null,
    val gameColors: GameColorDefinition? = null,
    val characterCard: CharacterCardDefinition? = null,
    val showFactionBackgrounds: Boolean? = null,
    val gameplayPreferences: GameplayPreferencesDefinition? = null,
)

// ---------------------------------------------------------------------------
// Sub-definitions
// ---------------------------------------------------------------------------

/**
 * Font families to use for display (headings) and body text.
 * Built-in system names: "default", "serif", "monospace", "cursive", "sans-serif".
 * Bundled font names (e.g. "cinzel", "lora") are resolved from assets/fonts/<name>/.
 * Unknown names fall back to "serif" for display and "default" for body.
 */
@Serializable
data class FontDefinition(
    val display: String? = null,
    val body: String? = null,
    val mono: String? = null,
)

/**
 * Material3 colour scheme colours as #RRGGBB or #AARRGGBB hex strings.
 * Only the colours used by the app need to be specified; missing ones fall back via
 * the base theme.
 */
@Serializable
data class ColorSchemeDefinition(
    val primary: String? = null,
    val onPrimary: String? = null,
    val primaryContainer: String? = null,
    val onPrimaryContainer: String? = null,
    val secondary: String? = null,
    val onSecondary: String? = null,
    val secondaryContainer: String? = null,
    val onSecondaryContainer: String? = null,
    val surface: String? = null,
    val onSurface: String? = null,
    val surfaceVariant: String? = null,
    val onSurfaceVariant: String? = null,
    val background: String? = null,
    val onBackground: String? = null,
    val outline: String? = null,
    val error: String? = null,
    val onError: String? = null,
    /** When true, the dark Material3 colour scheme is used as the base. */
    val isDark: Boolean? = null,
)

/** Per-faction identity colours as #RRGGBB hex strings. */
@Serializable
data class FactionColorDefinition(
    val commonwealth: String? = null,
    val dominion: String? = null,
    val leshavult: String? = null,
    val shades: String? = null,
)

/**
 * Shape configuration. Corner radii are in dp.
 * [navItemShape] and [drawerShape] accept "circle", "rect", or "rounded" (uses [cardCornerRadius]).
 */
@Serializable
data class ShapeDefinition(
    val cardCornerRadius: Float? = null,
    val navItemShape: String? = null,
    val drawerShape: String? = null,
)

/** Layout spacing in dp, per density tier. */
@Serializable
data class SpacingDefinition(
    val compact: Float? = null,
    val cozy: Float? = null,
    val spacious: Float? = null,
    val cardPaddingCompact: Float? = null,
    val cardPaddingCozy: Float? = null,
    val cardPaddingSpacious: Float? = null,
)

/**
 * Reference to a Material3 typography slot with optional overrides.
 * [base] is one of: displayLarge, displayMedium, displaySmall, headlineLarge,
 * headlineMedium, headlineSmall, titleLarge, titleMedium, titleSmall,
 * bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall.
 */
@Serializable
data class TypographyStyleRef(
    val base: String? = null,
    val sizeSp: Float? = null,
    /** "thin", "extraLight", "light", "normal", "medium", "semiBold", "bold", "extraBold", "black" */
    val weight: String? = null,
)

/** Typography slot overrides applied on top of the resolved font family. */
@Serializable
data class TypographyDefinition(
    val titleStyle: TypographyStyleRef? = null,
    val headerStyle: TypographyStyleRef? = null,
    val labelStyle: TypographyStyleRef? = null,
    val buttonTextStyle: TypographyStyleRef? = null,
)

/** Semantic game and UI colours as #RRGGBB hex strings. */
@Serializable
data class GameColorDefinition(
    val moonstone: String? = null,
    val positive: String? = null,
    val rankingGold: String? = null,
    val rankingSilver: String? = null,
    val rankingBronze: String? = null,
    val ready: String? = null,
    val scoreCircle: String? = null,
    val magicalDamage: String? = null,
    val followUpHighlight: String? = null,
    val arcaneGreen: String? = null,
    val arcaneBlue: String? = null,
    val arcanePurple: String? = null,
    val catastrophe: String? = null,
    val healthPip: String? = null,
)

/** Preferred game tracking mode applied when the user switches to this theme. */
@Serializable
data class GameplayPreferencesDefinition(
    val defaultTrackingMode: String? = null, // "LOW_DETAIL" | "FULL_TRACKING"
)

/** Flags controlling character card layout and rendering behaviour. */
@Serializable
data class CharacterCardDefinition(
    /** Show the character portrait as a faded background on expanded cards. */
    val showBackgroundImageOverlay: Boolean? = null,
    /** Use the large Moonstone-style stat header (name + keywords large, faction symbol). */
    val showExpandedStatsHeader: Boolean? = null,
    /** Show a HorizontalDivider between the collapsed header and expanded body. */
    val showCardDivider: Boolean? = null,
    /** Use damage-type icon images instead of abbreviated text (I/S/P). */
    val useDamageTypeIcons: Boolean? = null,
)
