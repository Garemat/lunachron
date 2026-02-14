package com.garemat.moonstone_companion.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppTheme
import com.garemat.moonstone_companion.R

// --- Base Utilities ---

@Composable
fun NullSymbol(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val appTheme = LocalAppTheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (appTheme == AppTheme.MOONSTONE) Color.Transparent else Color.LightGray.copy(alpha = 0.5f))
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

@Composable
fun parseAbilityDescription(description: String, searchQuery: String = "") = buildAnnotatedString {
    val regex = "\\[([GBP])\\]([^\\s,.:;]*)|Catastrophe:|\\{Null\\}".toRegex()
    var lastIndex = 0
    regex.findAll(description).forEach { match ->
        appendWithHighlight(description.substring(lastIndex, match.range.first), searchQuery)
        when (match.value) {
            "Catastrophe:" -> withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Red)) { append("Catastrophe:") }
            "{Null}" -> appendInlineContent("nullSymbol", "{Null}")
            else -> {
                val colorCode = match.groupValues[1]
                val value = match.groupValues[2]
                val bgColor = when (colorCode) {
                    "G" -> Color(0xFF2E7D32); "B" -> Color(0xFF1565C0); "P" -> Color(0xFFC2185B); else -> Color.Transparent
                }
                withStyle(style = SpanStyle(background = bgColor, color = Color.White, fontWeight = FontWeight.Bold, baselineShift = BaselineShift(0.1f))) { append(" $value ") }
            }
        }
        lastIndex = match.range.last + 1
    }
    appendWithHighlight(description.substring(lastIndex), searchQuery)
}

@Composable
fun SignatureResultDisplay(entry: SignatureResultEntry) {
    val isNull = entry.deal == "Null"
    val appTheme = LocalAppTheme.current
    
    Box(
        modifier = Modifier
            .size(if (isNull) 24.dp else 28.dp)
            .clip(CircleShape)
            .background(if (entry.isFollowUp) Color(0xFFFFEB3B) else Color.Transparent)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isNull) {
            NullSymbol(size = 20.dp)
        } else {
            Text(
                text = entry.deal,
                fontWeight = FontWeight.Bold,
                fontSize = if (appTheme == AppTheme.MOONSTONE) 20.sp else 14.sp,
                color = if (entry.isFollowUp) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// --- Faction UI ---

fun getFactionColor(faction: Faction) = when (faction) {
    Faction.COMMONWEALTH -> Color(0xFFFBC02D); Faction.DOMINION -> Color(0xFF1976D2); Faction.LESHAVULT -> Color(0xFF388E3C); Faction.SHADES -> Color(0xFF424242)
}

fun getFactionIcon(faction: Faction) = when (faction) {
    Faction.COMMONWEALTH -> Icons.Default.WbSunny; Faction.DOMINION -> Icons.Default.Brightness2; Faction.LESHAVULT -> Icons.Default.Nature; Faction.SHADES -> Icons.Default.Warning
}

@Composable
fun FactionSymbol(faction: Faction, modifier: Modifier = Modifier, tint: Color? = null) {
    val context = LocalContext.current
    val resId = remember(faction) {
        val resName = when(faction) {
            Faction.COMMONWEALTH -> "commonwealth"
            Faction.DOMINION -> "dominion"
            Faction.LESHAVULT -> "leshavult"
            Faction.SHADES -> "shades"
        }
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    }
    if (resId != 0) Image(
        painter = painterResource(id = resId), 
        contentDescription = faction.name, 
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
    else Icon(imageVector = getFactionIcon(faction), contentDescription = faction.name, modifier = modifier, tint = tint ?: Color.Unspecified)
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
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).onGloballyPositioned { onPositioned(it) }, horizontalArrangement = Arrangement.SpaceBetween) {
        Faction.entries.forEach { faction ->
            val isSelected = selectedFactions.contains(faction)
            FactionCircle(
                faction = faction,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        onFactionsChange(if (singleSelect) setOf(faction) else if (isSelected) selectedFactions - faction else selectedFactions + faction)
                    },
                isSelected = isSelected,
                showBorder = true
            )
        }
    }
}

// --- Character Components ---

@Composable
fun CommonStatBox(label: String, value: String, modifier: Modifier = Modifier, horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally, showDivider: Boolean = false) {
    Column(horizontalAlignment = horizontalAlignment, modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (showDivider) HorizontalDivider(modifier = Modifier.width(40.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else if (horizontalAlignment == Alignment.End) TextAlign.End else TextAlign.Start)
    }
}

@Composable
fun CommonAbilityItem(name: String, description: String, searchQuery: String = "", oncePerTurn: Boolean = false, oncePerGame: Boolean = false, reloadable: Boolean = false, isUsed: Boolean = false, onUsedChange: ((Boolean) -> Unit)? = null, isEditable: Boolean = true) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            val title = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { appendWithHighlight(name, searchQuery); append(": ") }
                if (oncePerTurn) withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)) { append(" - Once per turn") }
                if (oncePerGame) withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)) { append(if (reloadable) " - Once per game, unless reloaded" else " - Once per game") }
            }
            Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (oncePerGame && onUsedChange != null) {
                Box(modifier = Modifier.padding(start = 8.dp).size(16.dp).clip(CircleShape).background(if (isUsed) Color.Gray else Color.Transparent).border(1.2.dp, if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape).then(if (isEditable) Modifier.clickable { onUsedChange(!isUsed) } else Modifier), contentAlignment = Alignment.Center) {
                    if (isUsed) Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White)
                }
            }
        }
        Text(text = parseAbilityDescription(description, searchQuery), style = MaterialTheme.typography.bodySmall, inlineContent = getMoonstoneInlineContent())
    }
}

@Composable
fun CharacterFront(character: Character, searchQuery: String, onFlip: () -> Unit, onFlipPositioned: (LayoutCoordinates) -> Unit = {}) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    Column {
        if (isMoonstone) MoonstoneHeader(character, searchQuery, onFlip, onFlipPositioned)
        else Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                CommonStatBox("Melee", character.melee.toString(), showDivider = true)
                CommonStatBox("Range", "${character.meleeRange}\"", showDivider = true)
                CommonStatBox("Arcane", character.arcane.toString(), showDivider = true)
                CommonStatBox("Evade", character.evade, showDivider = true)
            }
            IconButton(onClick = onFlip, modifier = Modifier.onGloballyPositioned { onFlipPositioned(it) }) { Icon(Icons.Default.Refresh, contentDescription = "Flip", tint = MaterialTheme.colorScheme.primary) }
        }
        if (isMoonstone) MoonstoneStats(character)
        Spacer(modifier = Modifier.height(16.dp))
        character.passiveAbilities.forEach { CommonAbilityItem(it.name, it.description, searchQuery) }
        if (character.activeAbilities.isNotEmpty()) { AbilityTypeSeparator(); character.activeAbilities.forEach { CommonAbilityItem("${it.name} (${it.cost}) ${it.range}", it.description, searchQuery, it.oncePerTurn, it.oncePerGame) } }
        if (character.arcaneAbilities.isNotEmpty()) { AbilityTypeSeparator(); character.arcaneAbilities.forEach { CommonAbilityItem("${it.name} (${it.cost}) ${it.range}", it.description, searchQuery, it.oncePerTurn, it.oncePerGame, it.reloadable) } }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = buildAnnotatedString { append(if (isMoonstone) "Signature Move on a " else "Signature Move: "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendWithHighlight(if (isMoonstone) character.signatureMove.upgradeFrom else character.signatureMove.name, searchQuery) }; append(".") }, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.clickable { onFlip() }.fillMaxWidth(), textAlign = if (isMoonstone) TextAlign.Start else TextAlign.Center, color = if (isMoonstone) Color.Unspecified else MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(16.dp))
        HealthTracker(character.health, character.health, character.energyTrack, {}, isEditable = false)
        Text(text = "Base: ${character.baseSize}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
}

@Composable
private fun MoonstoneHeader(character: Character, searchQuery: String, onFlip: () -> Unit, onFlipPositioned: (LayoutCoordinates) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = buildAnnotatedString { appendWithHighlight(character.name, searchQuery); append(",") }, style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp), color = MaterialTheme.colorScheme.primary)
            Text(text = character.tags.joinToString(", "), style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            character.factions.firstOrNull()?.let { FactionSymbol(faction = it, modifier = Modifier.size(48.dp).padding(end = 8.dp)) }
            IconButton(onClick = onFlip, modifier = Modifier.onGloballyPositioned { onFlipPositioned(it) }) { Icon(Icons.Default.Refresh, contentDescription = "Flip", tint = MaterialTheme.colorScheme.secondary) }
        }
    }
}

@Composable
private fun MoonstoneStats(character: Character) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text("Melee", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text("Range", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)) }
            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text(character.melee.toString(), style = MaterialTheme.typography.headlineMedium); Text("${character.meleeRange}\"", style = MaterialTheme.typography.headlineMedium) }
        }
        Canvas(modifier = Modifier.height(40.dp).width(30.dp)) { drawLine(color = Color(0xFF2C1810), start = Offset(0f, size.height), end = Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx()) }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text("Arcane", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text("Evade", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)) }
            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Text(character.arcane.toString(), style = MaterialTheme.typography.headlineMedium); Text(character.evade, style = MaterialTheme.typography.headlineMedium) }
        }
    }
}

@Composable
fun CharacterBack(character: Character, searchQuery: String, onFlip: () -> Unit, onFlipPositioned: (LayoutCoordinates) -> Unit = {}) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(text = highlightText(character.signatureMove.name, searchQuery), style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp) else MaterialTheme.typography.titleLarge, fontWeight = if (isMoonstone) null else FontWeight.Bold, color = if (isMoonstone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onFlip, modifier = Modifier.onGloballyPositioned { onFlipPositioned(it) }) { Icon(Icons.Default.Refresh, contentDescription = "Flip", tint = if (isMoonstone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary) }
        }
        Text(text = buildAnnotatedString { append("Upgrade for "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(character.signatureMove.upgradeFrom) } }, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = if (isMoonstone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(12.dp))
        character.signatureMove.damageType?.let { Text(text = "Damage Type:", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text(text = it, style = if (isMoonstone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp)) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Opponent plays:", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)); Text("deal", style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)) }
        Column(modifier = Modifier.fillMaxWidth()) {
            character.signatureMove.results.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.opponentPlay, style = if (isMoonstone) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyMedium)
                    SignatureResultDisplay(entry)
                }
            }
        }
        if (character.signatureMove.passiveEffect != null || character.signatureMove.endStepEffect != null) {
            Spacer(modifier = Modifier.height(16.dp))
            character.signatureMove.passiveEffect?.let { Text(text = parseAbilityDescription(it, searchQuery), style = MaterialTheme.typography.bodyMedium, inlineContent = getMoonstoneInlineContent()) }
            character.signatureMove.endStepEffect?.let { Text(text = buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("End Step Effect: ") }; append(parseAbilityDescription(it, searchQuery)) }, style = MaterialTheme.typography.bodyMedium, inlineContent = getMoonstoneInlineContent()) }
        }
    }
}

@Composable
fun CommonCharacterCard(character: Character, searchQuery: String, isExpanded: Boolean, onExpandClick: () -> Unit, modifier: Modifier = Modifier, cardTargetName: String = "CharacterCard", onPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }, forceFlipped: Boolean? = null, selectionControl: @Composable (RowScope.() -> Unit)? = null) {
    var isFlippedState by remember { mutableStateOf(false) }; val isFlipped = forceFlipped ?: isFlippedState
    val context = LocalContext.current; val appTheme = LocalAppTheme.current
    val imageRes = remember(character.imageName) { if (character.imageName != null) context.resources.getIdentifier(character.imageName.substringBeforeLast("."), "drawable", context.packageName) else 0 }
    Card(modifier = modifier.fillMaxWidth().animateContentSize().onGloballyPositioned { onPositioned(cardTargetName, it) }, shape = RoundedCornerShape(if (appTheme == AppTheme.MOONSTONE) 0.dp else 12.dp), elevation = CardDefaults.cardElevation(defaultElevation = if (appTheme == AppTheme.MOONSTONE) 2.dp else 4.dp), colors = CardDefaults.cardColors(containerColor = if (appTheme == AppTheme.MOONSTONE) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { onExpandClick() }.padding(if (selectionControl != null) 8.dp else 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (selectionControl != null) { selectionControl(); Spacer(modifier = Modifier.width(4.dp)) }
                Box(modifier = Modifier.size(if (selectionControl != null) 40.dp else 56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (imageRes != 0) Image(painter = painterResource(id = imageRes), contentDescription = character.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text(text = character.name.take(1), style = if (selectionControl != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(if (selectionControl != null) 12.dp else 16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = highlightText(character.name, searchQuery), style = if (selectionControl != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = character.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
            }
            if (isExpanded) {
                if (appTheme != AppTheme.MOONSTONE) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (appTheme == AppTheme.MOONSTONE && imageRes != 0) Image(painter = painterResource(id = imageRes), contentDescription = null, modifier = Modifier.matchParentSize().alpha(0.25f), contentScale = ContentScale.Crop)
                    Box(modifier = Modifier.padding(16.dp)) {
                        if (!isFlipped) CharacterFront(character = character, searchQuery = searchQuery, onFlip = { isFlippedState = true }, onFlipPositioned = { onPositioned("FlipButton", it) })
                        else CharacterBack(character = character, searchQuery = searchQuery, onFlip = { isFlippedState = false }, onFlipPositioned = { onPositioned("FlipButton", it) })
                    }
                }
            }
        }
    }
}

// --- Shared Internal Helpers ---

@Composable
fun AbilityTypeSeparator() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        HorizontalDivider(modifier = Modifier.fillMaxWidth(0.6f), thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = if (LocalAppTheme.current == AppTheme.MOONSTONE) 0.3f else 0.1f))
    }
}

@Composable
fun MitigationIcon(resId: Int, value: String) {
    if (resId != 0) {
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(id = resId), contentDescription = null, modifier = Modifier.fillMaxSize())
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color.Red.copy(alpha = 0.5f),
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
    val appTheme = LocalAppTheme.current
    val isMoonstone = appTheme == AppTheme.MOONSTONE

    if (isMoonstone) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            if (isOffense) {
                val piercing = character.piercingDamageBuff.toIntOrNull() ?: 0
                if (piercing != 0) {
                    Image(painter = painterResource(id = R.drawable.piercing), contentDescription = "Piercing", modifier = Modifier.size(16.dp))
                    Text(text = piercing.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                }

                val impact = character.impactDamageBuff.toIntOrNull() ?: 0
                if (impact != 0) {
                    Image(painter = painterResource(id = R.drawable.impact), contentDescription = "Impact", modifier = Modifier.size(16.dp))
                    Text(text = impact.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                }

                val slicing = character.slicingDamageBuff.toIntOrNull() ?: 0
                if (slicing != 0) {
                    Image(painter = painterResource(id = R.drawable.slicing), contentDescription = "Slicing", modifier = Modifier.size(16.dp))
                    Text(text = slicing.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                }

                if (character.dealsMagicalDamage) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Magical", modifier = Modifier.size(14.dp), tint = Color(0xFF00B0FF))
                }
            } else {
                val allMitigation = character.allDamageMitigation.toIntOrNull() ?: 0
                if (allMitigation >= 1) {
                    Image(painter = painterResource(id = R.drawable.alldamagemitigation), contentDescription = "All Mitigation", modifier = Modifier.size(16.dp))
                    Text(text = allMitigation.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
                    
                    val piercing = character.piercingDamageMitigation.toIntOrNull() ?: 0
                    if (piercing != 0) MitigationIcon(R.drawable.piercing, piercing.toString())

                    val impact = character.impactDamageMitigation.toIntOrNull() ?: 0
                    if (impact != 0) MitigationIcon(R.drawable.impact, impact.toString())

                    val slicing = character.slicingDamageMitigation.toIntOrNull() ?: 0
                    if (slicing != 0) MitigationIcon(R.drawable.slicing, slicing.toString())

                    val magical = character.magicalDamageMitigation.toIntOrNull() ?: 0
                    if (magical != 0) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Magical Mitigation", modifier = Modifier.size(14.dp), tint = Color(0xFF00B0FF))
                        Text(text = magical.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        val modifiers = mutableListOf<@Composable () -> Unit>()
        fun addMod(prefix: String, value: String, offense: Boolean) {
            if (value == "Null") modifiers.add { Row(verticalAlignment = Alignment.CenterVertically) { Text(prefix, fontSize = 11.sp, fontWeight = FontWeight.Bold); NullSymbol(size = 12.dp, modifier = Modifier.padding(horizontal = 1.dp)) } }
            else if (value.toIntOrNull() != 0) modifiers.add { Text("$prefix${if (offense) "+" else "-"}$value", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        if (isOffense) { addMod("I", character.impactDamageBuff, true); addMod("S", character.slicingDamageBuff, true); addMod("P", character.piercingDamageBuff, true) }
        else { if (character.allDamageMitigation != "0") addMod("ALL", character.allDamageMitigation, false) else { addMod("I", character.impactDamageMitigation, false); addMod("S", character.slicingDamageMitigation, false); addMod("P", character.piercingDamageMitigation, false) }; addMod("M", character.magicalDamageMitigation, false) }
        
        if (modifiers.isNotEmpty() || (isOffense && character.dealsMagicalDamage)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
                Icon(imageVector = if (isOffense) Icons.Default.Hardware else Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
                if (isOffense && character.dealsMagicalDamage) Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00B0FF))
                modifiers.forEachIndexed { i, m -> m(); if (i < modifiers.size - 1) Text(", ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        } else Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
fun HealthTracker(totalHealth: Int, currentHealth: Int, energyTrack: List<Int>, onHealthChange: (Int) -> Unit, isEditable: Boolean = true, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start), modifier = modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..totalHealth) {
            val isLost = i > currentHealth; val isEnergy = energyTrack.contains(i)
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(when { isLost -> Color.Transparent; isEnergy -> if (isEditable) Color(0xFF2196F3) else Color(0xFF90CAF9); else -> if (isEditable) Color(0xFF4CAF50) else Color(0xFFA5D6A7) }).border(1.dp, if (isEnergy) Color(0xFF1565C0) else Color.DarkGray, CircleShape).then(if (isEditable) Modifier.clickable { onHealthChange(if (i <= currentHealth) i - 1 else i) } else Modifier), contentAlignment = Alignment.Center) {
                if (isLost) Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Red)
            }
        }
    }
}

@Composable
fun CharacterFilterHeader(searchQuery: String, onSearchQueryChange: (String) -> Unit, selectedFactions: Set<Faction>, onFactionsChange: (Set<Faction>) -> Unit, selectedTags: Set<String>, onTagsChange: (Set<String>) -> Unit, availableTags: List<String>, modifier: Modifier = Modifier, isFactionFixed: Boolean = false, showCollapseAll: Boolean = false, onCollapseAll: () -> Unit = {}, onClearAll: () -> Unit = {}, onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }) {
    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned("SearchField", it) }, placeholder = { Text("Search name or abilities...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") } }, singleLine = true, shape = RoundedCornerShape(12.dp))
        if (!isFactionFixed) { Spacer(modifier = Modifier.height(16.dp)); Text("Factions:", style = MaterialTheme.typography.labelMedium); FactionSelector(selectedFactions = selectedFactions, onFactionsChange = onFactionsChange, onPositioned = { onTargetPositioned("FactionFilter", it) }) }
        Spacer(modifier = Modifier.height(8.dp))
        if (availableTags.isNotEmpty()) {
            Text("Tags:", style = MaterialTheme.typography.labelMedium)
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).onGloballyPositioned { onTargetPositioned("TagFilter", it) }, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                items(availableTags) { tag ->
                    val isSelected = selectedTags.contains(tag)
                    FilterChip(selected = isSelected, onClick = { onTagsChange(if (isSelected) selectedTags - tag else selectedTags + tag) }, label = { Text(tag) }, leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (showCollapseAll) TextButton(onClick = onCollapseAll) { Icon(Icons.Default.UnfoldLess, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Collapse All") }
            if (searchQuery.isNotEmpty() || selectedFactions.isNotEmpty() || selectedTags.isNotEmpty()) TextButton(onClick = onClearAll) { Text("Clear All") }
        }
    }
}
