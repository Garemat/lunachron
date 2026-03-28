package io.github.garemat.lunachron.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.R
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import java.io.File

// --- Base Utilities ---

/**
 * Circular character portrait. Prefers {imageName}-head.png (zoomed head crop) for the
 * circular display, then falls back to the full portrait variants, then shows first initial.
 */
@Composable
fun CharacterPortrait(character: Character, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageFile = remember(character.imageName) {
        character.imageName?.let { name ->
            val dir = File(context.filesDir, "images")
            listOf("$name-head.png", name, "$name.jpg", "$name.png", "$name.webp")
                .firstNotNullOfOrNull { candidate -> File(dir, candidate).takeIf { it.exists() } }
        }
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = character.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = character.name.take(1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * A Card that automatically applies the current theme's shape and background color.
 * Use this instead of Card directly so all cards stay consistent with the active theme.
 * Pass [containerColor] only when you need to override (e.g. a "dead" character state).
 */
@Composable
fun ThemedCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppThemeProperties.current
    Card(
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor ?: theme.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        modifier = modifier,
        content = content
    )
}


@Composable
fun NullSymbol(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val theme = LocalAppThemeProperties.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (theme.showExpandedStatsHeader) Color.Transparent else Color.LightGray.copy(alpha = 0.5f))
            .padding(if (size < 20.dp) 1.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = if (size < 20.dp) 1.2.dp.toPx() else 1.5.dp.toPx()
            drawCircle(color = Color.Black, radius = this.size.minDimension / 2.2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth))
            drawLine(color = Color.Black, start = Offset(this.size.width * 0.25f, this.size.height * 0.75f), end = Offset(this.size.width * 0.75f, this.size.height * 0.25f), strokeWidth = strokeWidth)
        }
    }
}

@Composable
fun getMoonstoneInlineContent() = mapOf(
    "nullSymbol" to InlineTextContent(Placeholder(width = 14.sp, height = 14.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)) {
        NullSymbol(size = 14.dp, modifier = Modifier.padding(horizontal = 2.dp))
    }
)

fun highlightText(text: String, searchQuery: String): AnnotatedString = buildAnnotatedString {
    appendWithHighlight(text, searchQuery)
}

fun AnnotatedString.Builder.appendWithHighlight(text: String, searchQuery: String) {
    if (searchQuery.isEmpty() || !text.contains(searchQuery, ignoreCase = true)) {
        append(text)
        return
    }
    val pattern = Regex.escape(searchQuery).toRegex(RegexOption.IGNORE_CASE)
    var lastIndex = 0
    pattern.findAll(text).forEach { match ->
        append(text.substring(lastIndex, match.range.first))
        withStyle(style = SpanStyle(background = Color.Yellow.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)) { append(match.value) }
        lastIndex = match.range.last + 1
    }
    append(text.substring(lastIndex))
}

fun buildArcaneDescription(ability: Ability): String {
    val outcomeLines = ability.arcaneOutcomes.joinToString("\n") { outcome ->
        val cardLabel = outcome.validCards.joinToString(" or ") { card ->
            if (card.colour == "Catastrophe") "Catastrophe" else "[${card.colour.first()}]${card.value}"
        }
        "$cardLabel: ${outcome.text}"
    }
    return when {
        ability.description.isNotEmpty() && outcomeLines.isNotEmpty() -> "${ability.description}\n$outcomeLines"
        outcomeLines.isNotEmpty() -> outcomeLines
        else -> ability.description
    }
}

@Composable
fun parseAbilityDescription(description: String, searchQuery: String = "") = buildAnnotatedString {
    val theme = LocalAppThemeProperties.current
    val regex = "\\[([GBP])\\]([^\\s,.:;]*)|Catastrophe:|\\{Null\\}".toRegex()
    var lastIndex = 0
    regex.findAll(description).forEach { match ->
        appendWithHighlight(description.substring(lastIndex, match.range.first), searchQuery)
        when (match.value) {
            "Catastrophe:" -> withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = theme.catastropheColor)) { append("Catastrophe:") }
            "{Null}" -> appendInlineContent("nullSymbol", "{Null}")
            else -> {
                val colorCode = match.groupValues[1]
                val value = match.groupValues[2]
                val bgColor = when (colorCode) {
                    "G" -> theme.arcaneGreenColor; "B" -> theme.arcaneBlueColor; "P" -> theme.arcanePurpleColor; else -> Color.Transparent
                }
                withStyle(style = SpanStyle(background = bgColor, color = Color.White, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.1f))) { append(" $value ") }
            }
        }
        lastIndex = match.range.last + 1
    }
    appendWithHighlight(description.substring(lastIndex), searchQuery)
}

@Composable
fun SignatureResultDisplay(entry: SigMoveEntry) {
    val isNull = entry.deal == "Null"
    val theme = LocalAppThemeProperties.current

    Box(
        modifier = Modifier
            .size(if (isNull) 24.dp else 28.dp)
            .clip(CircleShape)
            .background(if (entry.isFollowUp) theme.followUpHighlightColor else Color.Transparent)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isNull) {
            NullSymbol(size = 20.dp)
        } else {
            Text(
                text = entry.deal,
                fontWeight = FontWeight.Bold,
                fontSize = if (theme.showExpandedStatsHeader) 20.sp else 14.sp,
                color = if (entry.isFollowUp) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// --- Faction UI ---

@Composable
fun getFactionColor(faction: Faction): Color {
    val theme = LocalAppThemeProperties.current
    return when (faction) {
        Faction.COMMONWEALTH -> theme.factionCommonwealth
        Faction.DOMINION     -> theme.factionDominion
        Faction.LESHAVULT    -> theme.factionLeshavult
        Faction.SHADES       -> theme.factionShades
    }
}

fun getFactionIcon(faction: Faction) = when (faction) {
    Faction.COMMONWEALTH -> Icons.Default.WbSunny; Faction.DOMINION -> Icons.Default.Brightness2; Faction.LESHAVULT -> Icons.Default.Nature; Faction.SHADES -> Icons.Default.Warning
}

@Composable
fun FactionSymbol(faction: Faction, modifier: Modifier = Modifier, tint: Color? = null) {
    val resId = when(faction) {
        Faction.COMMONWEALTH -> R.drawable.commonwealth
        Faction.DOMINION -> R.drawable.dominion
        Faction.LESHAVULT -> R.drawable.leshavult
        Faction.SHADES -> R.drawable.shades
    }
    if (resId != 0) Image(
        painter = painterResource(id = resId),
        contentDescription = faction.name,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
    else Icon(imageVector = getFactionIcon(faction), contentDescription = faction.name, modifier = modifier, tint = tint ?: Color.Unspecified)
}

/**
 * Standard faction symbol with no background or border.
 * [size] sets the layout footprint. Leshavult's antlers are rendered slightly larger
 * so the inner circle matches the other factions visually, with antlers allowed to spill.
 */
// Per-faction scale factors so the inner circle of each icon matches visually.
// Commonwealth sun rays and Leshavult antlers extend well beyond the body circle,
// so we render them larger and let the spikes/antlers spill outside the layout bounds.
private fun factionVisualScale(faction: Faction) = when (faction) {
    Faction.LESHAVULT    -> 4f / 3f   // antlers extend ~33% above head
    Faction.COMMONWEALTH -> 1.25f     // sun-ray spikes extend ~25% beyond body circle
    else                 -> 1f
}

@Composable
fun FactionIcon(faction: Faction, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val visualSize = size * factionVisualScale(faction)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        FactionSymbol(faction = faction, modifier = Modifier.size(visualSize))
    }
}

/**
 * Displays one or two faction symbols blended together, matching the official dual-faction
 * card art. Both icons are centered in the [size] footprint with a small diagonal drift so
 * the primary sits slightly lower-left and the secondary upper-right. The secondary is
 * rendered with [BlendMode.Multiply] so its dark symbol elements show through the lighter
 * areas of the primary, approximating the translucent overlap in the physical card art.
 * For single-faction characters, delegates to [FactionIcon].
 */
/**
 * Displays the primary faction symbol for a character. Multi-faction blending is
 * deferred to a later patch — for now only factions[0] is shown via [FactionIcon].
 */
@Composable
fun MultiFactionIcon(factions: List<Faction>, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    if (factions.isEmpty()) return
    FactionIcon(faction = factions[0], size = size, modifier = modifier)
}

@Composable
fun FactionCircle(
    faction: Faction,
    modifier: Modifier = Modifier,
    isSelected: Boolean = true,
    showBorder: Boolean = false,
    padding: Dp = 0.dp
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isSelected) getFactionColor(faction) else Color.Transparent)
            .then(if (showBorder) Modifier.border(2.dp, getFactionColor(faction), CircleShape) else Modifier)
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        FactionSymbol(
            faction = faction,
            modifier = Modifier.fillMaxSize(),
            tint = if (isSelected) Color.White else getFactionColor(faction)
        )
    }
}

@Composable
fun FactionSelector(selectedFactions: Set<Faction>, onFactionsChange: (Set<Faction>) -> Unit, modifier: Modifier = Modifier, singleSelect: Boolean = false, onPositioned: (LayoutCoordinates) -> Unit = {}) {
    val anySelected = selectedFactions.isNotEmpty()
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).onGloballyPositioned { onPositioned(it) }, horizontalArrangement = Arrangement.SpaceBetween) {
        Faction.entries.forEach { faction ->
            val isSelected = selectedFactions.contains(faction)
            val iconAlpha = if (!anySelected || isSelected) 1f else 0.25f
            FactionIcon(
                faction = faction,
                size = 48.dp,
                modifier = Modifier
                    .clickable { onFactionsChange(if (singleSelect) setOf(faction) else if (isSelected) selectedFactions - faction else selectedFactions + faction) }
                    .alpha(iconAlpha)
            )
        }
    }
}

// --- Common Components ---

@Composable
fun SetupOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeProperties.current
    ThemedCard(modifier = modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CommonStatBox(label: String, value: String, modifier: Modifier = Modifier, horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally, showDivider: Boolean = false) {
    Column(horizontalAlignment = horizontalAlignment, modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (showDivider) HorizontalDivider(modifier = Modifier.width(40.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else if (horizontalAlignment == Alignment.End) TextAlign.End else TextAlign.Start)
    }
}

@Composable
fun CommonAbilityItem(name: String, description: String, searchQuery: String = "", oncePerTurn: Boolean = false, oncePerGame: Boolean = false, reloadable: Boolean = false, isUsed: Boolean = false, onUsedChange: ((Boolean) -> Unit)? = null, isEditable: Boolean = true, passive: Boolean = false, showColon: Boolean = true) {
    val theme = LocalAppThemeProperties.current
    if (passive) {
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { appendWithHighlight(name, searchQuery); append(": ") }
                append(parseAbilityDescription(description, searchQuery))
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = theme.verticalSpacing / 4),
            inlineContent = getMoonstoneInlineContent()
        )
    } else {
        Column(modifier = Modifier.padding(vertical = theme.verticalSpacing / 4)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val title = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendWithHighlight(name, searchQuery)
                        if (showColon) append(": ")
                    }
                    if (oncePerTurn) withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)) { append(" - Once per turn") }
                    if (oncePerGame) withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)) { append(if (reloadable) " - Once per game, unless reloaded" else " - Once per game") }
                }
                Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (oncePerGame && onUsedChange != null) {
                    Box(modifier = Modifier.padding(start = 8.dp).size(16.dp).clip(CircleShape).background(if (isUsed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent).border(1.2.dp, if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape).then(if (isEditable) Modifier.clickable { onUsedChange(!isUsed) } else Modifier), contentAlignment = Alignment.Center) {
                        if (isUsed) Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White)
                    }
                }
            }
            Text(text = parseAbilityDescription(description, searchQuery), style = MaterialTheme.typography.bodySmall, inlineContent = getMoonstoneInlineContent())
        }
    }
}

@Composable
fun CharacterFront(character: Character, searchQuery: String, onFlip: () -> Unit, onFlipPositioned: (LayoutCoordinates) -> Unit = {}) {
    val theme = LocalAppThemeProperties.current
    Column {
        if (theme.showExpandedStatsHeader) MoonstoneHeader(character, searchQuery)
        else Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CommonStatBox("Melee", character.melee.toString(), showDivider = true)
            CommonStatBox("Range", "${character.meleeRange}\"", showDivider = true)
            CommonStatBox("Arcane", character.arcane.toString(), showDivider = true)
            CommonStatBox("Evade", character.evade.toString(), showDivider = true)
        }
        if (theme.showExpandedStatsHeader) MoonstoneStats(character)
        Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
        val passiveAbilities = character.abilities.filter { it.abilityType == "Passive" }
        val activeAbilities = character.abilities.filter { it.abilityType == "Active" }
        val arcaneAbilities = character.abilities.filter { it.abilityType == "Arcane" }
        passiveAbilities.forEach { CommonAbilityItem(it.name, it.description, searchQuery, it.oncePerTurn, it.oncePerGame, passive = true) }
        if (activeAbilities.isNotEmpty()) { AbilityTypeSeparator(); activeAbilities.forEach { CommonAbilityItem("${it.name} (${it.energyCost ?: 0}) ${it.range ?: ""}", it.description, searchQuery, it.oncePerTurn, it.oncePerGame, showColon = false) } }
        if (arcaneAbilities.isNotEmpty()) { AbilityTypeSeparator(); arcaneAbilities.forEach { CommonAbilityItem("${it.name} (${it.energyCost ?: 0}) ${it.range ?: ""}", buildArcaneDescription(it), searchQuery, it.oncePerTurn, it.oncePerGame, showColon = false) } }
        character.signatureMove?.let { sigMove ->
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onFlip() }.onGloballyPositioned { onFlipPositioned(it) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = buildAnnotatedString { append(if (theme.showExpandedStatsHeader) "Signature Move on a " else "Signature Move: "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendWithHighlight(if (theme.showExpandedStatsHeader) sigMove.upgradeFor else sigMove.name, searchQuery) }; append(".") }, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "View signature move", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
        }
        HealthTracker(character.health, character.health, character.energyTrack, {}, isEditable = false)
        Text(text = "Base: ${character.baseSize}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
}

@Composable
private fun MoonstoneHeader(character: Character, searchQuery: String) {
    val nameParts = character.name.split(",", limit = 2)
    val mainName = nameParts[0].trim()
    val subtitle = nameParts.getOrNull(1)?.trim()
    var scaleFactor by remember(character.name) { mutableStateOf(1f) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = (32 * scaleFactor).sp)) { appendWithHighlight(mainName, searchQuery) }
                if (subtitle != null) {
                    withStyle(SpanStyle(fontSize = (32 * scaleFactor).sp)) { append(", ") }
                    withStyle(SpanStyle(fontSize = (20 * scaleFactor).sp)) { appendWithHighlight(subtitle, searchQuery) }
                }
            },
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.hasVisualOverflow && scaleFactor > 0.6f) scaleFactor *= 0.9f },
            modifier = Modifier.padding(end = 36.dp)
        )
        Text(text = character.keywords.joinToString(", "), style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MoonstoneStats(character: Character) {
    val theme = LocalAppThemeProperties.current
    Row(modifier = Modifier.fillMaxWidth().padding(top = theme.verticalSpacing / 2), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text("Melee", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text("Range", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)) }
            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text(character.melee.toString(), style = MaterialTheme.typography.headlineMedium); Text("${character.meleeRange}\"", style = MaterialTheme.typography.headlineMedium) }
        }
        val separatorColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.height(40.dp).width(30.dp)) { drawLine(color = separatorColor, start = Offset(0f, size.height), end = Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx()) }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text("Arcane", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text("Evade", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)) }
            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text(character.arcane.toString(), style = MaterialTheme.typography.headlineMedium); Text(character.evade.toString(), style = MaterialTheme.typography.headlineMedium) }
        }
    }
}

@Composable
fun CharacterBack(character: Character, searchQuery: String, onFlip: () -> Unit, onFlipPositioned: (LayoutCoordinates) -> Unit = {}) {
    val theme = LocalAppThemeProperties.current
    Column {
        val sigMove = character.signatureMove
        Text(text = highlightText(sigMove?.name ?: "", searchQuery), style = if (theme.showExpandedStatsHeader) MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp) else MaterialTheme.typography.titleLarge, fontWeight = if (theme.showExpandedStatsHeader) null else FontWeight.Bold, color = if (theme.showExpandedStatsHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().onGloballyPositioned { onFlipPositioned(it) })
        if (sigMove == null) {
            Text(text = "No signature move.", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(text = buildAnnotatedString { append("Upgrade for "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(sigMove.upgradeFor) } }, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = if (theme.showExpandedStatsHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            sigMove.possibleDamageTypes.takeIf { it.isNotEmpty() }?.let { types -> Text(text = "Damage Type:", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text(text = types.joinToString(", "), style = if (theme.showExpandedStatsHeader) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(theme.verticalSpacing / 4)) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Opponent plays:", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text("deal", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)) }
            Column(modifier = Modifier.fillMaxWidth()) {
                sigMove.positionEntries().forEach { (pos, entry) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = theme.verticalSpacing / 8), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(pos, style = if (theme.showExpandedStatsHeader) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyMedium)
                        SignatureResultDisplay(entry)
                    }
                }
            }
            if (sigMove.extraText.isNotEmpty() || sigMove.endStepEffect.isNotEmpty()) {
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                if (sigMove.extraText.isNotEmpty()) Text(text = parseAbilityDescription(sigMove.extraText, searchQuery), style = MaterialTheme.typography.bodyMedium, inlineContent = getMoonstoneInlineContent())
                if (sigMove.endStepEffect.isNotEmpty()) Text(text = buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("End Step Effect: ") }; append(parseAbilityDescription(sigMove.endStepEffect, searchQuery)) }, style = MaterialTheme.typography.bodyMedium, inlineContent = getMoonstoneInlineContent())
            }
            Spacer(modifier = Modifier.height(theme.verticalSpacing))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onFlip() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.KeyboardArrowLeft, contentDescription = "Back to character stats", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(text = "Character stats", style = MaterialTheme.typography.labelMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun CommonCharacterCard(character: Character, searchQuery: String, isExpanded: Boolean, onExpandClick: () -> Unit, modifier: Modifier = Modifier, cardTargetName: String = "CharacterCard", onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }, forceFlipped: Boolean? = null, selectionControl: @Composable (RowScope.() -> Unit)? = null, bottomContent: (@Composable () -> Unit)? = null) {
    var isFlippedState by remember { mutableStateOf(false) }; val isFlipped = forceFlipped ?: isFlippedState
    val context = LocalContext.current
    val theme = LocalAppThemeProperties.current
    val density = LocalDensity.current
    var frontHeightPx by remember { mutableStateOf(0) }
    val bgImageFile = remember(character.imageName) {
        character.imageName?.let { name ->
            val dir = File(context.filesDir, "images")
            listOf("", ".jpg", ".png", ".webp").firstNotNullOfOrNull { ext ->
                File(dir, "$name$ext").takeIf { it.exists() }
            }
        }
    }
    ThemedCard(modifier = modifier.fillMaxWidth().animateContentSize().onGloballyPositioned { onPositioned(cardTargetName, it) }) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { onExpandClick() }.padding(theme.cardContentPadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (selectionControl != null) { selectionControl(); Spacer(modifier = Modifier.width(4.dp)) }
                Box(modifier = Modifier.size(if (selectionControl != null) 40.dp else 56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    CharacterPortrait(character = character, size = if (selectionControl != null) 40.dp else 56.dp)
                }
                Spacer(modifier = Modifier.width(if (selectionControl != null) 12.dp else 16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = highlightText(character.name, searchQuery), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = character.keywords.joinToString(", "), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
            }
            if (isExpanded) {
                if (theme.showCardDivider) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (theme.showBackgroundImageOverlay && bgImageFile != null) {
                        AsyncImage(model = bgImageFile, contentDescription = null, modifier = Modifier.matchParentSize().alpha(0.25f), contentScale = ContentScale.Crop)
                    }
                    // Faction symbol(s) peeking from top-right corner, half-clipped by card edge
                    if (character.factions.isNotEmpty()) {
                        MultiFactionIcon(
                            factions = character.factions,
                            size = 64.dp,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 32.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(theme.cardContentPadding)
                            .then(if (!isFlipped) Modifier.onSizeChanged { frontHeightPx = it.height }
                                  else Modifier.heightIn(min = with(density) { frontHeightPx.toDp() }))
                    ) {
                        if (!isFlipped) CharacterFront(character = character, searchQuery = searchQuery, onFlip = { isFlippedState = true }, onFlipPositioned = { onPositioned("FlipButton", it) })
                        else CharacterBack(character = character, searchQuery = searchQuery, onFlip = { isFlippedState = false }, onFlipPositioned = { onPositioned("FlipButton", it) })
                    }
                }
            }
            bottomContent?.invoke()
        }
    }
}

@Composable
fun UpgradeCardUI(card: UpgradeCard, searchQuery: String, isExpanded: Boolean, onExpandClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppThemeProperties.current
    ThemedCard(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpandClick() }.padding(theme.cardContentPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Upgrade", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic)
                    Text(text = highlightText(card.name, searchQuery), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(modifier = Modifier.padding(theme.cardContentPadding)) {
                    val restriction = buildList {
                        if (card.allowedKeywords.isNotEmpty()) add(card.allowedKeywords.joinToString(", "))
                        if (card.restrictedKeywords.isNotEmpty()) add("Not: " + card.restrictedKeywords.joinToString(", "))
                    }.joinToString(" · ")
                    if (restriction.isNotEmpty()) {
                        Text(text = "Restrictions: $restriction", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    card.abilities.forEach { CommonAbilityItem(it.name, it.description, searchQuery) }
                }
            }
        }
    }
}

@Composable
fun CampaignCardUI(card: CampaignCard, searchQuery: String, isExpanded: Boolean, onExpandClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppThemeProperties.current
    ThemedCard(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpandClick() }.padding(theme.cardContentPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Campaign Card", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic)
                    Text(text = highlightText(card.name, searchQuery), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = highlightText(card.timing, searchQuery), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(modifier = Modifier.padding(theme.cardContentPadding)) {
                    Text(text = parseAbilityDescription(card.description, searchQuery), style = MaterialTheme.typography.bodyMedium, inlineContent = getMoonstoneInlineContent())
                    card.extraDescription?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    }
                }
            }
        }
    }
}

// --- Shared Internal Helpers ---

@Composable
fun AbilityTypeSeparator() {
    val theme = LocalAppThemeProperties.current
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = theme.verticalSpacing / 4), contentAlignment = Alignment.Center) {
        HorizontalDivider(modifier = Modifier.fillMaxWidth(0.6f), thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = if (theme.showExpandedStatsHeader) 0.3f else 0.1f))
    }
}

@Composable
fun MitigationIcon(resId: Int, value: String) {
    if (resId != 0) {
        val strikeColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(id = resId), contentDescription = null, modifier = Modifier.fillMaxSize())
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = strikeColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
    }
}

@Composable
fun ModifierDisplay(character: Character, isOffense: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalAppThemeProperties.current

    if (theme.useDamageTypeIcons) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            if (isOffense) {
                val piercing = character.piercingDamageBuff ?: 0
                if (piercing != 0) {
                    Image(painter = painterResource(id = R.drawable.piercing), contentDescription = "Piercing", modifier = Modifier.size(16.dp))
                    Text(text = piercing.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                }

                val impact = character.impactDamageBuff ?: 0
                if (impact != 0) {
                    Image(painter = painterResource(id = R.drawable.impact), contentDescription = "Impact", modifier = Modifier.size(16.dp))
                    Text(text = impact.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                }

                val slicing = character.slicingDamageBuff ?: 0
                if (slicing != 0) {
                    Image(painter = painterResource(id = R.drawable.slicing), contentDescription = "Slicing", modifier = Modifier.size(16.dp))
                    Text(text = slicing.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                }

                if (character.dealsMagicalDamage) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Magical", modifier = Modifier.size(14.dp), tint = theme.magicalDamageColor)
                }
            } else {
                if (character.allDamageMitigation >= 1) {
                    Image(painter = painterResource(id = R.drawable.alldamagemitigation), contentDescription = "All Mitigation", modifier = Modifier.size(16.dp))
                    Text(text = character.allDamageMitigation.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
                    if (character.piercingDamageMitigation != 0) MitigationIcon(R.drawable.piercing, character.piercingDamageMitigation.toString())
                    if (character.impactDamageMitigation != 0) MitigationIcon(R.drawable.impact, character.impactDamageMitigation.toString())
                    if (character.slicingDamageMitigation != 0) MitigationIcon(R.drawable.slicing, character.slicingDamageMitigation.toString())
                    if (character.magicalDamageMitigation != 0) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Magical Mitigation", modifier = Modifier.size(14.dp), tint = theme.magicalDamageColor)
                        Text(text = character.magicalDamageMitigation.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        val modifiers = mutableListOf<@Composable () -> Unit>()
        fun addMod(prefix: String, value: Int?, offense: Boolean) {
            if (value == null) modifiers.add { Row(verticalAlignment = Alignment.CenterVertically) { Text(prefix, fontSize = 11.sp, fontWeight = FontWeight.Bold); NullSymbol(size = 12.dp, modifier = Modifier.padding(horizontal = 1.dp)) } }
            else if (value != 0) modifiers.add { val sign = if (offense) { if (value > 0) "+" else "" } else { if (value > 0) "-" else "+" }; Text("$prefix$sign$value", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        if (isOffense) { addMod("I", character.impactDamageBuff, true); addMod("S", character.slicingDamageBuff, true); addMod("P", character.piercingDamageBuff, true) }
        else { if (character.allDamageMitigation != 0) addMod("ALL", character.allDamageMitigation, false) else { addMod("I", character.impactDamageMitigation, false); addMod("S", character.slicingDamageMitigation, false); addMod("P", character.piercingDamageMitigation, false) }; addMod("M", character.magicalDamageMitigation, false) }
        
        if (modifiers.isNotEmpty() || (isOffense && character.dealsMagicalDamage)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
                Icon(imageVector = if (isOffense) Icons.Default.Hardware else Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
                if (isOffense && character.dealsMagicalDamage) Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = theme.magicalDamageColor)
                modifiers.forEachIndexed { i, m -> m(); if (i < modifiers.size - 1) Text(", ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        } else Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
fun HealthTracker(totalHealth: Int, currentHealth: Int, energyTrack: List<Int>, onHealthChange: (Int) -> Unit, modifier: Modifier = Modifier, isEditable: Boolean = true) {
    val theme = LocalAppThemeProperties.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start), modifier = modifier.padding(top = theme.verticalSpacing / 2), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..totalHealth) {
            val isLost = i > currentHealth; val isEnergy = energyTrack.contains(i)
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(when { isLost -> Color.Transparent; isEnergy -> if (isEditable) theme.moonstoneColor else theme.moonstoneColor.copy(alpha = 0.5f); else -> if (isEditable) theme.positiveColor else theme.positiveColor.copy(alpha = 0.5f) }).border(1.dp, if (isEnergy) theme.moonstoneColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline, CircleShape).then(if (isEditable) Modifier.clickable { onHealthChange(if (i <= currentHealth) i - 1 else i) } else Modifier), contentAlignment = Alignment.Center) {
                if (isLost) Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun HealthPipsChunked(
    total: Int,
    current: Int,
    energyTrack: List<Int>,
    compact: Boolean,
    isEditable: Boolean,
    onHealthChange: (Int) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val pipSize = if (compact) 9.dp else 13.dp
    val rowChunks = (1..total).toList().chunked(10)
    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rowChunks.forEach { rowPips ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val firstFive = rowPips.take(5)
                val secondFive = rowPips.drop(5)
                firstFive.forEach { pip ->
                    HealthPip(pip, current, energyTrack, pipSize, isEditable, theme, onHealthChange)
                    Spacer(Modifier.width(2.dp))
                }
                if (secondFive.isNotEmpty()) {
                    Text("|", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 2.dp))
                    secondFive.forEach { pip ->
                        HealthPip(pip, current, energyTrack, pipSize, isEditable, theme, onHealthChange)
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HealthPip(
    pip: Int,
    current: Int,
    energyTrack: List<Int>,
    size: Dp,
    isEditable: Boolean,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onHealthChange: (Int) -> Unit
) {
    val isEnergy = energyTrack.contains(pip)
    val isAlive = pip <= current
    val color = when {
        isEnergy && isAlive -> theme.moonstoneColor
        isAlive -> theme.positiveColor
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(if (isEditable) Modifier.clickable { onHealthChange(if (pip <= current) pip - 1 else pip) } else Modifier)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterFilterHeader(searchQuery: String, onSearchQueryChange: (String) -> Unit, selectedFactions: Set<Faction>, onFactionsChange: (Set<Faction>) -> Unit, selectedTags: Set<String>, onTagsChange: (Set<String>) -> Unit, availableTags: List<String>, modifier: Modifier = Modifier, isFactionFixed: Boolean = false, showCollapseAll: Boolean = false, onCollapseAll: () -> Unit = {}, onClearAll: () -> Unit = {}, onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }) { // showCollapseAll retained for API compat but no longer rendered
    val theme = LocalAppThemeProperties.current
    Column(modifier = modifier.padding(theme.screenPadding)) {
        OutlinedTextField(value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("SearchField", it) }, placeholder = { Text("Search name or abilities...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") } }, singleLine = true, shape = theme.cardShape)
        if (!isFactionFixed) { Spacer(modifier = Modifier.height(theme.verticalSpacing)); FactionSelector(selectedFactions = selectedFactions, onFactionsChange = onFactionsChange, onPositioned = { onTargetPositioned("FactionFilter", it) }) }
        if (availableTags.isNotEmpty()) {
            var showKeywordsDialog by remember { mutableStateOf(false) }
            Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
            Row(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("TagFilter", it) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showKeywordsDialog = true },
                    shape = theme.cardShape
                ) {
                    Text("Keywords")
                }
                if (selectedTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedTags.toList()) { tag ->
                            FilterChip(
                                selected = true,
                                onClick = { onTagsChange(selectedTags - tag) },
                                label = { Text(tag) },
                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                shape = theme.cardShape
                            )
                        }
                    }
                }
            }
            if (showKeywordsDialog) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showKeywordsDialog = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Keywords", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            if (selectedTags.isNotEmpty()) {
                                TextButton(onClick = { onTagsChange(emptySet()) }) { Text("Clear") }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableTags.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { tag ->
                                        val isSelected = selectedTags.contains(tag)
                                        if (isSelected) {
                                            Button(
                                                onClick = { onTagsChange(selectedTags - tag) },
                                                modifier = Modifier.weight(1f),
                                                shape = theme.cardShape
                                            ) { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        } else {
                                            OutlinedButton(
                                                onClick = { onTagsChange(selectedTags + tag) },
                                                modifier = Modifier.weight(1f),
                                                shape = theme.cardShape
                                            ) { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        }
                                    }
                                    // Fill empty cell if odd number of tags
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (searchQuery.isNotEmpty() || selectedFactions.isNotEmpty() || selectedTags.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearAll) { Text("Clear All") }
            }
        }
    }
}
