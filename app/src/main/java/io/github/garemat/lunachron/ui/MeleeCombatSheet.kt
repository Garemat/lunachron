package io.github.garemat.lunachron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

// ─── Data ────────────────────────────────────────────────────────────────────

private data class MeleeMatchup(
    val deal: Int?,
    val suffer: Int?,
    val dealFollowup: Boolean = false,
    val sufferFollowup: Boolean = false,
)

private data class MeleeCard(
    val name: String,
    val damageType: String,
    val matchups: Map<String, MeleeMatchup>,
)

private val MELEE_CARDS = listOf(
    MeleeCard("Falling Swing", "Impact or Slicing", mapOf(
        "High Guard"    to MeleeMatchup(null, null),
        "Falling Swing" to MeleeMatchup(0, 0),
        "Thrust"        to MeleeMatchup(0, 2),
        "Sweeping Cut"  to MeleeMatchup(3, 2),
        "Rising Attack" to MeleeMatchup(3, 1),
        "Low Guard"     to MeleeMatchup(2, null),
    )),
    MeleeCard("Thrust", "Piercing", mapOf(
        "High Guard"    to MeleeMatchup(0, null),
        "Falling Swing" to MeleeMatchup(2, 0),
        "Thrust"        to MeleeMatchup(3, 3),
        "Sweeping Cut"  to MeleeMatchup(null, null, sufferFollowup = true),
        "Rising Attack" to MeleeMatchup(2, 1),
        "Low Guard"     to MeleeMatchup(1, null),
    )),
    MeleeCard("Rising Attack", "Impact, Slicing or Piercing", mapOf(
        "High Guard"    to MeleeMatchup(2, null),
        "Falling Swing" to MeleeMatchup(1, 3),
        "Thrust"        to MeleeMatchup(1, 2),
        "Sweeping Cut"  to MeleeMatchup(2, 2),
        "Rising Attack" to MeleeMatchup(1, 1),
        "Low Guard"     to MeleeMatchup(null, null),
    )),
    MeleeCard("Sweeping Cut", "Slicing", mapOf(
        "High Guard"    to MeleeMatchup(null, null),
        "Falling Swing" to MeleeMatchup(2, 3),
        "Thrust"        to MeleeMatchup(0, null, dealFollowup = true),
        "Sweeping Cut"  to MeleeMatchup(0, 0),
        "Rising Attack" to MeleeMatchup(2, 2),
        "Low Guard"     to MeleeMatchup(null, null),
    )),
    MeleeCard("Low Guard", "—", mapOf(
        "High Guard"    to MeleeMatchup(null, null),
        "Falling Swing" to MeleeMatchup(null, 2),
        "Thrust"        to MeleeMatchup(null, 1),
        "Sweeping Cut"  to MeleeMatchup(null, null),
        "Rising Attack" to MeleeMatchup(null, null, dealFollowup = true),
        "Low Guard"     to MeleeMatchup(null, null),
    )),
    MeleeCard("High Guard", "—", mapOf(
        "High Guard"    to MeleeMatchup(null, null),
        "Falling Swing" to MeleeMatchup(null, null, dealFollowup = true),
        "Thrust"        to MeleeMatchup(null, 0),
        "Sweeping Cut"  to MeleeMatchup(null, null),
        "Rising Attack" to MeleeMatchup(null, 2),
        "Low Guard"     to MeleeMatchup(null, null),
    )),
)

private val OPP_ORDER = listOf("High Guard", "Falling Swing", "Thrust", "Sweeping Cut", "Rising Attack", "Low Guard")
private val CARD_TYPE_MAP = MELEE_CARDS.associate { it.name to it.damageType }

// ─── Sheet ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeleeCombatSheet(onDismiss: () -> Unit) {
    val theme = LocalAppThemeProperties.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val selectedCard = MELEE_CARDS.getOrNull(selectedIndex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Title
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Melee Combat",
                    style = theme.titleStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Choose your card to see matchup outcomes",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Card selection chips
            Text(
                text = "Your Card",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                itemsIndexed(MELEE_CARDS) { i, card ->
                    val isSelected = i == selectedIndex
                    Surface(
                        onClick = { selectedIndex = i },
                        shape = theme.cardShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (isSelected) 0.dp else 0.dp,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = card.damageType,
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Selected card banner
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .padding(start = 0.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = selectedCard?.name ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (selectedCard != null) "Damage type: ${selectedCard.damageType}"
                               else "Select a card above",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

            // Column headers
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 3dp left border + 10dp spacer = 13dp offset to align with row content
                Spacer(Modifier.width(13.dp))
                Text(
                    text = "Opponent Plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Deal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = "Suffer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Matchup rows
            if (selectedCard == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Select your card above to see matchups",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                }
            } else {
                Column {
                    OPP_ORDER.forEachIndexed { i, oppName ->
                        val matchup = selectedCard.matchups[oppName] ?: return@forEachIndexed
                        val isSelf = oppName == selectedCard.name
                        val dealVal = matchup.deal ?: 0
                        val sufferVal = matchup.suffer ?: 0
                        val isFavorable = dealVal > sufferVal
                        val isUnfavorable = sufferVal > dealVal

                        val rowBg = when {
                            isFavorable -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                            isUnfavorable -> MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
                            else -> Color.Transparent
                        }
                        val borderColor = when {
                            isFavorable -> MaterialTheme.colorScheme.primary
                            isUnfavorable -> MaterialTheme.colorScheme.error
                            else -> Color.Transparent
                        }

                        if (i > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isSelf) 0.45f else 1f)
                                .background(rowBg)
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left border indicator
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(borderColor),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = oppName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = CARD_TYPE_MAP[oppName] ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MeleeDamageCell(
                                value = matchup.deal,
                                isFollowup = matchup.dealFollowup,
                                followupColor = theme.followUpHighlightColor,
                                baseColor = MaterialTheme.colorScheme.primary,
                            )
                            MeleeDamageCell(
                                value = matchup.suffer,
                                isFollowup = matchup.sufferFollowup,
                                followupColor = theme.followUpHighlightColor,
                                baseColor = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Legend
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeleeLegendDot(color = MaterialTheme.colorScheme.primary, label = "Favourable")
                MeleeLegendDot(color = MaterialTheme.colorScheme.error, label = "Unfavourable")
                MeleeLegendDot(color = theme.followUpHighlightColor, label = "Follow-up")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "⊘ = blocked",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ─── Internal composables ─────────────────────────────────────────────────────

@Composable
private fun MeleeDamageCell(
    value: Int?,
    isFollowup: Boolean,
    followupColor: Color,
    baseColor: Color,
) {
    Box(
        modifier = Modifier.width(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFollowup) followupColor else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (value == null) "⊘" else value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = when {
                    isFollowup -> Color.Black
                    value == null -> baseColor.copy(alpha = 0.35f)
                    else -> baseColor
                },
            )
        }
    }
}

@Composable
private fun MeleeLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
