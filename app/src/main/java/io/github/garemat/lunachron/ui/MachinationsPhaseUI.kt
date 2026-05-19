package io.github.garemat.lunachron.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.garemat.lunachron.Character
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.OnlineCampaignDetail
import io.github.garemat.lunachron.OnlineCampaignMember
import io.github.garemat.lunachron.OnlineMachinationAttack
import io.github.garemat.lunachron.OnlineMachinationChoice
import io.github.garemat.lunachron.Troupe
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

// ── Local UI state ────────────────────────────────────────────────────────────

private data class MachSlotState(
    val selectedType: String? = null,
    val targetDeviceId: String = "",
    val abstained: Boolean = false,
    val isOpen: Boolean = false
) {
    val isDecided: Boolean get() = abstained || (selectedType != null && targetDeviceId.isNotEmpty())
    fun toChoice(): OnlineMachinationChoice? =
        if (selectedType != null && targetDeviceId.isNotEmpty())
            OnlineMachinationChoice(targetDeviceId, selectedType)
        else null
}

private data class AtkState(
    val skipping: Boolean = true,
    val isOpen: Boolean = false,
    val attackType: String = "ASSAULT",
    val targetDeviceId: String = "",
    val targetCharId: Int = -1
) {
    val isReady: Boolean get() = skipping || (targetDeviceId.isNotEmpty() && targetCharId != -1)
    fun toAttack(): OnlineMachinationAttack? =
        if (!skipping && targetDeviceId.isNotEmpty() && targetCharId != -1)
            OnlineMachinationAttack(targetDeviceId, targetCharId, attackType)
        else null
}

// ── Colors ────────────────────────────────────────────────────────────────────

private val SupportGreen = Color(0xFF2B9260)
private val AttackAmber = Color(0xFFC47A28)
private val RankGold = Color(0xFFC8A94E)

// ── Main tab ──────────────────────────────────────────────────────────────────

@Composable
internal fun OnlineMachinationsTab(
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onDecodeTroupe: (String) -> Troupe?
) {
    val myDeviceId = state.backendDeviceId
    val approvedMembers = campaign.members.filter { it.status == "APPROVED" }
    val mySubmission = campaign.machinations.find { it.deviceId == myDeviceId }
    val attacksEnabled = campaign.settings?.attacksEnabled == true

    val rankedMembers = remember(approvedMembers) {
        approvedMembers.sortedWith(
            compareByDescending<OnlineCampaignMember> { it.matchPoints }
                .thenByDescending { it.victoryPoints }
        )
    }
    val rankOf = remember(rankedMembers) {
        rankedMembers.mapIndexed { i, m -> m.deviceId to i + 1 }.toMap()
    }

    val nextRoundKey = "round${campaign.currentRound + 1}"
    val nextRoundGames = campaign.schedule?.get(nextRoundKey) ?: emptyMap()
    val myNextOpponentId = nextRoundGames.values
        .firstOrNull { myDeviceId in it }
        ?.firstOrNull { it != myDeviceId }

    // Bonus 3rd slot for players with a bye in the upcoming round
    val hasNextRoundSchedule = campaign.schedule?.containsKey(nextRoundKey) == true
    val hasByeBonus = hasNextRoundSchedule && myNextOpponentId == null

    val eligibleTargets = approvedMembers.filter {
        it.deviceId != myDeviceId && it.deviceId != myNextOpponentId
    }

    var slot1 by remember(mySubmission) {
        val c = mySubmission?.choices?.getOrNull(0)
        mutableStateOf(
            if (c != null) MachSlotState(selectedType = c.type, targetDeviceId = c.targetDeviceId)
            else MachSlotState()
        )
    }
    var slot2 by remember(mySubmission) {
        val c = mySubmission?.choices?.getOrNull(1)
        mutableStateOf(
            if (c != null) MachSlotState(selectedType = c.type, targetDeviceId = c.targetDeviceId)
            else MachSlotState()
        )
    }
    var slot3 by remember(mySubmission) {
        val c = mySubmission?.choices?.getOrNull(2)
        mutableStateOf(
            if (c != null) MachSlotState(selectedType = c.type, targetDeviceId = c.targetDeviceId)
            else MachSlotState()
        )
    }
    var atk by remember(mySubmission) {
        val a = mySubmission?.attack
        mutableStateOf(
            if (a != null) AtkState(
                skipping = false,
                attackType = a.type,
                targetDeviceId = a.targetDeviceId,
                targetCharId = a.targetCharId
            ) else AtkState()
        )
    }

    // Clear downstream slots when an earlier slot's target creates a conflict
    LaunchedEffect(slot1.selectedType, slot1.targetDeviceId) {
        if (!slot2.abstained && slot2.selectedType != null && slot2.targetDeviceId.isNotEmpty() &&
            slot2.selectedType == slot1.selectedType &&
            slot2.targetDeviceId == slot1.targetDeviceId &&
            slot1.targetDeviceId.isNotEmpty()
        ) {
            slot2 = slot2.copy(targetDeviceId = "", isOpen = true)
        }
    }
    LaunchedEffect(slot1.selectedType, slot1.targetDeviceId, slot2.selectedType, slot2.targetDeviceId) {
        if (hasByeBonus && !slot3.abstained && slot3.selectedType != null && slot3.targetDeviceId.isNotEmpty()) {
            val conflictsWithSlot1 = slot3.selectedType == slot1.selectedType && slot3.targetDeviceId == slot1.targetDeviceId && slot1.targetDeviceId.isNotEmpty()
            val conflictsWithSlot2 = slot3.selectedType == slot2.selectedType && slot3.targetDeviceId == slot2.targetDeviceId && slot2.targetDeviceId.isNotEmpty()
            if (conflictsWithSlot1 || conflictsWithSlot2) {
                slot3 = slot3.copy(targetDeviceId = "", isOpen = true)
            }
        }
    }

    val atkTargetMember = approvedMembers.find { it.deviceId == atk.targetDeviceId }
    val atkTargetTroupe = remember(atkTargetMember?.troupeData) {
        atkTargetMember?.troupeData?.let { runCatching { onDecodeTroupe(it) }.getOrNull() }
    }
    val atkTargetChars = remember(atkTargetTroupe, state.characters) {
        atkTargetTroupe?.characterIds?.mapNotNull { cid -> state.characters.find { it.id == cid } }
            ?: emptyList()
    }

    val submittedCount = campaign.machinations.size
    val totalCount = approvedMembers.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = RankGold)
                Text(
                    "Round ${campaign.currentRound} — Machinations",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (hasByeBonus) {
                    Text(
                        "Bye +1 slot",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "$submittedCount / $totalCount submitted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (myDeviceId.isNotEmpty() && eligibleTargets.isNotEmpty()) {
            // Opponent exclusion notice
            if (myNextOpponentId != null) {
                item {
                    val opponentName =
                        approvedMembers.find { it.deviceId == myNextOpponentId }?.username
                    if (opponentName != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Info, null, Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$opponentName is your round ${campaign.currentRound + 1} opponent — they cannot be targeted.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Machinations section label
            item {
                Text(
                    "MACHINATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Slot I
            item {
                MachinationSlotCard(
                    label = "Operation I",
                    slot = slot1,
                    targets = eligibleTargets,
                    rankOf = rankOf,
                    totalPlayers = approvedMembers.size,
                    onUpdate = { slot1 = it },
                    onClear = { slot1 = MachSlotState() }
                )
            }

            // Slot II — locked until slot I decided
            item {
                val slot2Targets = if (slot1.selectedType != null && slot1.targetDeviceId.isNotEmpty()
                    && slot2.selectedType == slot1.selectedType
                ) {
                    eligibleTargets.filter { it.deviceId != slot1.targetDeviceId }
                } else {
                    eligibleTargets
                }
                MachinationSlotCard(
                    label = "Operation II",
                    slot = slot2,
                    targets = slot2Targets,
                    rankOf = rankOf,
                    totalPlayers = approvedMembers.size,
                    locked = !slot1.isDecided,
                    onUpdate = { slot2 = it },
                    onClear = { slot2 = MachSlotState() }
                )
            }

            // Slot III — bonus for bye players, unlocked after slot II is decided
            if (hasByeBonus) {
                item {
                    val slot3Targets = run {
                        var targets = eligibleTargets
                        if (slot3.selectedType != null && slot1.targetDeviceId.isNotEmpty() && slot3.selectedType == slot1.selectedType)
                            targets = targets.filter { it.deviceId != slot1.targetDeviceId }
                        if (slot3.selectedType != null && slot2.targetDeviceId.isNotEmpty() && slot3.selectedType == slot2.selectedType)
                            targets = targets.filter { it.deviceId != slot2.targetDeviceId }
                        targets
                    }
                    MachinationSlotCard(
                        label = "Operation III",
                        slot = slot3,
                        targets = slot3Targets,
                        rankOf = rankOf,
                        totalPlayers = approvedMembers.size,
                        locked = !slot2.isDecided,
                        onUpdate = { slot3 = it },
                        onClear = { slot3 = MachSlotState() }
                    )
                }
            }

            // Attack section
            if (attacksEnabled && eligibleTargets.isNotEmpty()) {
                item {
                    Text(
                        "ATTACK",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                item {
                    AttackSectionCard(
                        atk = atk,
                        targets = eligibleTargets,
                        rankOf = rankOf,
                        atkTargetChars = atkTargetChars,
                        onUpdate = { atk = it }
                    )
                }
            }

            // Submit
            item {
                val canSubmit = !state.isSubmittingMachination && atk.isReady
                Button(
                    onClick = {
                        onEvent(
                            CharacterEvent.SubmitMachination(
                                campaign.id,
                                listOfNotNull(slot1.toChoice(), slot2.toChoice(), if (hasByeBonus) slot3.toChoice() else null),
                                atk.toAttack()
                            )
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSubmittingMachination) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            if (mySubmission != null) Icons.Default.Refresh
                            else Icons.AutoMirrored.Filled.Send,
                            null, Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (mySubmission != null) "Update Submission" else "Submit Machinations",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        } else if (myDeviceId.isNotEmpty()) {
            item {
                Text(
                    "No valid machination targets this round.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Submission status
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Submission Status",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(approvedMembers) { member ->
            val submission = campaign.machinations.find { it.deviceId == member.deviceId }
            val isMe = member.deviceId == myDeviceId
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (submission != null) Icons.Default.CheckCircle
                    else Icons.Default.RadioButtonUnchecked,
                    null, Modifier.size(18.dp),
                    tint = if (submission != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
                Text(
                    member.username,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (isMe) Text(
                    "You", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (member.isLocal) Text(
                    "Local", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    if (submission != null) "Ready" else "Waiting…",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (submission != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ── Machination slot ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachinationSlotCard(
    label: String,
    slot: MachSlotState,
    targets: List<OnlineCampaignMember>,
    rankOf: Map<String, Int>,
    totalPlayers: Int,
    locked: Boolean = false,
    onUpdate: (MachSlotState) -> Unit,
    onClear: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val accentColor = when {
        locked -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        slot.selectedType == "SUPPORT" && slot.targetDeviceId.isNotEmpty() -> SupportGreen
        slot.selectedType == "SABOTAGE" && slot.targetDeviceId.isNotEmpty() ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        border = BorderStroke(
            if (!locked && slot.isDecided && !slot.abstained) 1.5.dp else 1.dp,
            accentColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                locked -> MaterialTheme.colorScheme.surface
                slot.abstained -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                slot.selectedType == "SUPPORT" && slot.targetDeviceId.isNotEmpty() ->
                    SupportGreen.copy(alpha = 0.08f)
                slot.selectedType == "SABOTAGE" && slot.targetDeviceId.isNotEmpty() ->
                    MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (locked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                when {
                    locked -> Icon(
                        Icons.Default.Lock, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    slot.isDecided && slot.isOpen -> {
                        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close, "Clear", Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    slot.isDecided && !slot.isOpen -> {
                        IconButton(
                            onClick = { onUpdate(slot.copy(isOpen = true)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Edit, "Edit", Modifier.size(14.dp))
                        }
                        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close, "Clear", Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    !slot.isOpen -> TextButton(
                        onClick = { onUpdate(slot.copy(isOpen = true)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Plan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Body
            when {
                locked -> Text(
                    "Complete Operation I first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                slot.isDecided && !slot.isOpen -> {
                    if (slot.abstained) {
                        Text(
                            "Abstaining from this operation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val target = targets.find { it.deviceId == slot.targetDeviceId }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val typeColor =
                                if (slot.selectedType == "SUPPORT") SupportGreen
                                else MaterialTheme.colorScheme.error
                            val typeIcon =
                                if (slot.selectedType == "SUPPORT") Icons.Default.Favorite
                                else Icons.Default.Dangerous
                            Icon(typeIcon, null, Modifier.size(14.dp), tint = typeColor)
                            Text(
                                slot.selectedType?.lowercase()
                                    ?.replaceFirstChar { it.uppercase() } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = typeColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "→ ${target?.username ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val rank = rankOf[slot.targetDeviceId]
                            if (rank != null) {
                                Text(
                                    "#$rank",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RankGold
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Expanded form
                    // Type selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MachTypeButton(
                            label = "Support",
                            icon = Icons.Default.Favorite,
                            color = SupportGreen,
                            selected = slot.selectedType == "SUPPORT",
                            onClick = {
                                onUpdate(
                                    slot.copy(
                                        selectedType = "SUPPORT",
                                        targetDeviceId = "",
                                        abstained = false
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MachTypeButton(
                            label = "Sabotage",
                            icon = Icons.Default.Dangerous,
                            color = MaterialTheme.colorScheme.error,
                            selected = slot.selectedType == "SABOTAGE",
                            onClick = {
                                onUpdate(
                                    slot.copy(
                                        selectedType = "SABOTAGE",
                                        targetDeviceId = "",
                                        abstained = false
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Player grid — shown after type is selected
                    AnimatedVisibility(visible = slot.selectedType != null) {
                        MachPlayerGrid(
                            members = targets,
                            rankOf = rankOf,
                            totalPlayers = totalPlayers,
                            selectedDeviceId = slot.targetDeviceId,
                            selectedType = slot.selectedType,
                            onSelect = { id ->
                                val newId = if (slot.targetDeviceId == id) "" else id
                                onUpdate(
                                    slot.copy(
                                        targetDeviceId = newId,
                                        abstained = false,
                                        isOpen = newId.isEmpty()
                                    )
                                )
                            }
                        )
                    }

                    // Abstain link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                onUpdate(
                                    MachSlotState(
                                        abstained = true,
                                        isOpen = false
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Abstain from this operation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Type button ───────────────────────────────────────────────────────────────

@Composable
private fun MachTypeButton(
    label: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) color.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ── Player grid ───────────────────────────────────────────────────────────────

@Composable
private fun MachPlayerGrid(
    members: List<OnlineCampaignMember>,
    rankOf: Map<String, Int>,
    totalPlayers: Int,
    selectedDeviceId: String,
    selectedType: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        members.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { member ->
                    MachPlayerCard(
                        member = member,
                        rank = rankOf[member.deviceId] ?: 0,
                        totalPlayers = totalPlayers,
                        selectedType = selectedType,
                        isSelected = member.deviceId == selectedDeviceId,
                        onClick = { onSelect(member.deviceId) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachPlayerCard(
    member: OnlineCampaignMember,
    rank: Int,
    totalPlayers: Int,
    selectedType: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeProperties.current
    val borderColor = when {
        isSelected && selectedType == "SUPPORT" -> SupportGreen
        isSelected && selectedType == "SABOTAGE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = theme.cardShape,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected && selectedType == "SUPPORT" -> SupportGreen.copy(alpha = 0.12f)
                isSelected && selectedType == "SABOTAGE" ->
                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RankBadge(rank)
                Text(
                    member.username,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "${member.wins}W ${member.draws}D ${member.losses}L",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectedType != null) {
                MachinationImpactHint(rank, totalPlayers, selectedType)
            }
        }
    }
}

// ── Rank badge ────────────────────────────────────────────────────────────────

@Composable
private fun RankBadge(rank: Int) {
    val bgColor = when (rank) {
        1 -> RankGold
        2 -> Color(0xFF9E9E9E)
        3 -> Color(0xFF8D6E63)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val fgColor = when (rank) {
        1 -> Color(0xFF3A2800)
        2, 3 -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = bgColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

// ── Impact hint ───────────────────────────────────────────────────────────────

@Composable
private fun MachinationImpactHint(rank: Int, totalPlayers: Int, type: String) {
    val tierSize = maxOf(1, totalPlayers / 3)
    val isTop = rank <= tierSize
    val isBottom = rank > totalPlayers - tierSize

    // (winMp, loseMp) — the MP delta for each outcome
    val (winMp, loseMp) = when (type) {
        "SUPPORT" -> when {
            isTop    -> 1 to -2
            isBottom -> 1 to 0
            else     -> 1 to -1   // mid
        }
        "SABOTAGE" -> when {
            isTop    -> 0 to 1
            isBottom -> -1 to 0
            else     -> -1 to 1   // mid
        }
        else -> 0 to 0
    }

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        MpOutcomeRow(label = "Win", mp = winMp)
        MpOutcomeRow(label = "Lose", mp = loseMp)
    }
}

@Composable
private fun MpOutcomeRow(label: String, mp: Int) {
    val valueColor = when {
        mp > 0 -> SupportGreen
        mp < 0 -> MaterialTheme.colorScheme.error
        else   -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val valueText = when {
        mp > 0 -> "+$mp MP"
        mp < 0 -> "$mp MP"
        else   -> "no change"
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (mp != 0) FontWeight.Bold else FontWeight.Normal,
            color = valueColor
        )
    }
}

// ── Attack section ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttackSectionCard(
    atk: AtkState,
    targets: List<OnlineCampaignMember>,
    rankOf: Map<String, Int>,
    atkTargetChars: List<Character>,
    onUpdate: (AtkState) -> Unit
) {
    val theme = LocalAppThemeProperties.current

    if (atk.skipping && !atk.isOpen) {
        // Compact skip row
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = AttackAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "AP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AttackAmber,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Text(
                    "Skipping attack phase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onUpdate(atk.copy(skipping = false, isOpen = true)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Plan attack", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp))
                }
            }
        }
    } else {
        // Full attack planner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape,
            border = BorderStroke(1.5.dp, AttackAmber.copy(alpha = 0.6f)),
            colors = CardDefaults.cardColors(containerColor = AttackAmber.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.GppBad, null, Modifier.size(16.dp), tint = AttackAmber)
                    Text(
                        "Plan Attack",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AttackAmber,
                        modifier = Modifier.weight(1f)
                    )
                    if (!atk.skipping && atk.isReady) {
                        IconButton(
                            onClick = { onUpdate(atk.copy(isOpen = false)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ExpandLess, null, Modifier.size(14.dp))
                        }
                    }
                }

                // Step 1: Attack type
                Text(
                    "1 · Choose attack type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AttackTypeButton(
                        label = "Assault",
                        apCost = 1,
                        selected = atk.attackType == "ASSAULT",
                        onClick = { onUpdate(atk.copy(attackType = "ASSAULT")) },
                        modifier = Modifier.weight(1f)
                    )
                    AttackTypeButton(
                        label = "Abduction",
                        apCost = 2,
                        selected = atk.attackType == "ABDUCTION",
                        onClick = {
                            onUpdate(
                                atk.copy(
                                    attackType = "ABDUCTION",
                                    targetDeviceId = "",
                                    targetCharId = -1
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Step 2: Target player
                Text(
                    "2 · Choose target player",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    targets.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { member ->
                                AttackPlayerCard(
                                    member = member,
                                    rank = rankOf[member.deviceId] ?: 0,
                                    isSelected = atk.targetDeviceId == member.deviceId,
                                    onClick = {
                                        val newId =
                                            if (atk.targetDeviceId == member.deviceId) ""
                                            else member.deviceId
                                        onUpdate(atk.copy(targetDeviceId = newId, targetCharId = -1))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // Step 3: Target character
                AnimatedVisibility(visible = atk.targetDeviceId.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "3 · Choose target character",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (atkTargetChars.isNotEmpty()) {
                            atkTargetChars.chunked(2).forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    row.forEach { char ->
                                        AttackCharCard(
                                            char = char,
                                            isSelected = atk.targetCharId == char.id,
                                            onClick = {
                                                val newId =
                                                    if (atk.targetCharId == char.id) -1 else char.id
                                                onUpdate(atk.copy(targetCharId = newId))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else {
                            Text(
                                "Target has no troupe data — character selection unavailable.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Cancel link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { onUpdate(AtkState()) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Cancel — skip attack phase",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttackTypeButton(
    label: String,
    apCost: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AttackAmber.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (selected) AttackAmber else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) AttackAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Surface(
                color = if (selected) AttackAmber.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "$apCost AP",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) AttackAmber
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttackPlayerCard(
    member: OnlineCampaignMember,
    rank: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeProperties.current
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = theme.cardShape,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) AttackAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AttackAmber.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RankBadge(rank)
            Text(
                member.username,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttackCharCard(
    char: Character,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeProperties.current
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = theme.cardShape,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) AttackAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AttackAmber.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            char.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
    }
}
