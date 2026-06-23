@file:OptIn(ExperimentalLayoutApi::class)

package io.github.garemat.lunachron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.garemat.lunachron.CampaignRound
import io.github.garemat.lunachron.Character
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.Faction
import io.github.garemat.lunachron.CampaignCard
import io.github.garemat.lunachron.OnlineCampaignDetail
import io.github.garemat.lunachron.OnlineCampaignMember
import io.github.garemat.lunachron.OnlineMachinationChoice
import io.github.garemat.lunachron.OnlineMachinationEntry
import io.github.garemat.lunachron.OnlineMatchResult
import io.github.garemat.lunachron.OnlinePlayerStat
import io.github.garemat.lunachron.Troupe
import io.github.garemat.lunachron.UpgradeCard
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

// ── Campaign phase detection ──────────────────────────────────────────────────

private enum class CampaignPhase {
    RECRUITING,         // OPEN
    TROUPE_SELECTION,   // LOCKED + not all ready
    SCHEDULE_PENDING,   // LOCKED + all ready + no schedule
    ACTIVE              // LOCKED + schedule published
}

private fun detectPhase(campaign: OnlineCampaignDetail): CampaignPhase {
    if (campaign.status != "LOCKED") return CampaignPhase.RECRUITING
    if (campaign.schedule != null) return CampaignPhase.ACTIVE
    val approved = campaign.members.filter { it.status == "APPROVED" }
    val allReady = approved.isNotEmpty() && approved.all { it.isReady }
    return if (allReady) CampaignPhase.SCHEDULE_PENDING else CampaignPhase.TROUPE_SELECTION
}

private enum class ActiveTab { RANKINGS, SCHEDULE, MACHINATIONS }

// ── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineCampaignDetailScreen(
    campaignId: String,
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit,
    /** Decode a base64 troupe share code into a Troupe object (from ViewModel). */
    onDecodeTroupe: (String) -> Troupe?,
    /** Encode a Troupe to its share code for uploading. */
    onEncodeTroupe: (Troupe) -> String
) {
    val theme = LocalAppThemeProperties.current

    LaunchedEffect(campaignId) { onEvent(CharacterEvent.LoadOnlineCampaign(campaignId)) }
    DisposableEffect(Unit) { onDispose { onEvent(CharacterEvent.ClearSelectedOnlineCampaign) } }

    val campaign = state.selectedOnlineCampaign
    val isHost = campaign?.currentUserRole == "HOST"
    val approvedMembers = campaign?.members?.filter { it.status == "APPROVED" } ?: emptyList()
    val phase = campaign?.let { detectPhase(it) } ?: CampaignPhase.RECRUITING

    if (state.showCampaignAdminPanel && campaign != null) {
        AdminPanelSheet(
            campaign = campaign,
            state = state,
            onEvent = onEvent,
            onDismiss = { onEvent(CharacterEvent.HideCampaignAdminPanel) },
            onDecodeTroupe = onDecodeTroupe,
            onEncodeTroupe = onEncodeTroupe
        )
    }

    if (state.showCampaignDeleteDialog && campaign != null) {
        DeleteCampaignDialog(
            campaignName = campaign.name,
            isDeleting = state.isDeletingCampaign,
            onConfirm = { onEvent(CharacterEvent.DeleteOnlineCampaign(campaign.id)) },
            onDismiss = { onEvent(CharacterEvent.HideCampaignDeleteDialog) }
        )
    }

    // Navigate back after successful deletion
    LaunchedEffect(state.onlineCampaignDeleted) {
        if (state.onlineCampaignDeleted) {
            onEvent(CharacterEvent.DismissCampaignDeleted)
            onNavigateBack()
        }
    }

    // Default round count when members first load
    LaunchedEffect(approvedMembers.size) {
        if (state.onlineScheduleRoundCount == 0 && approvedMembers.size >= 2) {
            val n = approvedMembers.size
            onEvent(CharacterEvent.SetOnlineScheduleRoundCount(if (n % 2 == 0) n - 1 else n))
        }
    }

    if (state.onlineCampaignError != null) {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissOnlineCampaignError) },
            title = { Text("Error", style = theme.headerStyle) },
            text = { Text(state.onlineCampaignError, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { onEvent(CharacterEvent.DismissOnlineCampaignError) }) { Text("OK") }
            }
        )
    }

    if (state.matchResultSubmitted) {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissMatchResultSubmitted) },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Result Submitted") },
            text = { Text("Your result has been submitted. Waiting for your opponent to verify it.") },
            confirmButton = {
                TextButton(onClick = { onEvent(CharacterEvent.DismissMatchResultSubmitted) }) { Text("OK") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoadingCampaignDetail && campaign == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                campaign == null -> {
                    Text("Campaign not found.", modifier = Modifier.align(Alignment.Center),
                        style = theme.headerStyle, color = MaterialTheme.colorScheme.outline)
                }
                phase == CampaignPhase.ACTIVE -> {
                    ActiveCampaignHome(
                        campaign = campaign,
                        state = state,
                        isHost = isHost,
                        onEvent = onEvent,
                        onDecodeTroupe = onDecodeTroupe,
                        onEncodeTroupe = onEncodeTroupe
                    )
                }
                else -> {
                    PreGamePhaseContent(
                        campaign = campaign,
                        state = state,
                        campaignId = campaignId,
                        phase = phase,
                        isHost = isHost,
                        approvedMembers = approvedMembers,
                        onEvent = onEvent,
                        onDecodeTroupe = onDecodeTroupe,
                        onEncodeTroupe = onEncodeTroupe
                    )
                }
            }
        }
}

// ── Pre-game phases (Recruiting, Troupe Selection, Schedule Pending) ──────────

@Composable
private fun PreGamePhaseContent(
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    campaignId: String,
    phase: CampaignPhase,
    isHost: Boolean,
    approvedMembers: List<OnlineCampaignMember>,
    onEvent: (CharacterEvent) -> Unit,
    onDecodeTroupe: (String) -> Troupe?,
    onEncodeTroupe: (Troupe) -> String
) {
    val theme = LocalAppThemeProperties.current
    val pending = campaign.members.filter { it.status == "PENDING" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        // Campaign info
        item { CampaignInfoCard(campaign = campaign) }

        // Lock / Unlock (host, OPEN or LOCKED pre-schedule)
        if (isHost) {
            item {
                Spacer(Modifier.height(4.dp))
                LockUnlockSection(
                    campaign = campaign,
                    isLoading = state.isLockingCampaign,
                    pendingCount = pending.size,
                    onLock = { onEvent(CharacterEvent.LockOnlineCampaign(campaignId)) },
                    onUnlock = { onEvent(CharacterEvent.UnlockOnlineCampaign(campaignId)) }
                )
            }
        }

        // Pending member requests (host, OPEN)
        if (isHost && phase == CampaignPhase.RECRUITING && pending.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Pending Requests", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            items(pending) { member ->
                PendingMemberCard(member = member, campaignId = campaignId,
                    isProcessing = state.isApprovingMember, onEvent = onEvent)
            }
        }

        // Troupe selection section (when LOCKED)
        if (phase == CampaignPhase.TROUPE_SELECTION || phase == CampaignPhase.SCHEDULE_PENDING) {
            item {
                Spacer(Modifier.height(4.dp))
                TroupeSelectionSection(
                    campaign = campaign,
                    state = state,
                    campaignId = campaignId,
                    approvedMembers = approvedMembers,
                    onEvent = onEvent,
                    onDecodeTroupe = onDecodeTroupe,
                    onEncodeTroupe = onEncodeTroupe
                )
            }
        }

        // Schedule generation (host, all ready, no schedule)
        if (phase == CampaignPhase.SCHEDULE_PENDING) {
            item {
                Spacer(Modifier.height(4.dp))
                if (isHost) {
                    ScheduleGenerationSection(
                        state = state,
                        campaignId = campaignId,
                        approvedMembers = approvedMembers,
                        onEvent = onEvent
                    )
                } else {
                    WaitingCard(text = "The Chamberlain is preparing the schedule.")
                }
            }
        }
    }
}

// ── Troupe selection section ──────────────────────────────────────────────────

@Composable
private fun TroupeSelectionSection(
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    campaignId: String,
    approvedMembers: List<OnlineCampaignMember>,
    onEvent: (CharacterEvent) -> Unit,
    onDecodeTroupe: (String) -> Troupe?,
    onEncodeTroupe: (Troupe) -> String
) {
    val theme = LocalAppThemeProperties.current
    val myDeviceId = state.backendDeviceId // current device's ID in state
    val myMember = approvedMembers.find { it.deviceId == myDeviceId }
    val allReady = approvedMembers.isNotEmpty() && approvedMembers.all { it.isReady }

    // Troupe picker dialog
    var showTroupePicker by remember { mutableStateOf(false) }
    // Member detail dialog (tap to see troupe)
    var viewingMember by remember { mutableStateOf<OnlineCampaignMember?>(null) }

    if (showTroupePicker) {
        TroupePickerDialog(
            troupes = state.troupes,
            onDismiss = { showTroupePicker = false },
            onSelect = { troupe ->
                showTroupePicker = false
                val encoded = onEncodeTroupe(troupe)
                onEvent(CharacterEvent.UploadOnlineTroupe(campaignId, encoded))
            }
        )
    }

    viewingMember?.let { member ->
        val troupe = member.troupeData?.let { onDecodeTroupe(it) }
        MemberTroupeDialog(
            member = member,
            troupe = troupe,
            characters = state.characters,
            onDismiss = { viewingMember = null }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Troupe Selection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val readyCount = approvedMembers.count { it.isReady }
                    Text("$readyCount / ${approvedMembers.size} ready", style = MaterialTheme.typography.bodySmall,
                        color = if (allReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // My troupe + ready controls
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showTroupePicker = true },
                    enabled = !state.isUploadingTroupe,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isUploadingTroupe) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (myMember?.troupeData != null) "Change Troupe" else "Select Troupe",
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = { onEvent(CharacterEvent.SetOnlineCampaignReady(campaignId, !(myMember?.isReady ?: false))) },
                    enabled = myMember?.troupeData != null && !state.isSettingReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (myMember?.isReady == true) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (state.isSettingReady) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (myMember?.isReady == true) "Unready" else "Ready!")
                    }
                }
            }

            HorizontalDivider()

            // Member list with troupe status
            approvedMembers.forEach { member ->
                val troupe = member.troupeData?.let { runCatching { onDecodeTroupe(it) }.getOrNull() }
                MemberTroupeRow(
                    member = member,
                    troupe = troupe,
                    characters = state.characters,
                    onClick = { viewingMember = member }
                )
            }
        }
    }
}

@Composable
private fun MemberTroupeRow(
    member: OnlineCampaignMember,
    troupe: Troupe?,
    characters: List<Character>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ready indicator
        Icon(
            if (member.isReady) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (member.isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (member.isLocal) {
                    Text("Local", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ).padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            if (troupe != null) {
                Text(troupe.troupeName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("No troupe set", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
        // Character portrait row (up to 4)
        if (troupe != null) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                troupe.characterIds.take(4).forEach { charId ->
                    val char = characters.find { it.id == charId }
                    if (char != null) {
                        CharacterPortrait(character = char, size = 28.dp,
                            modifier = Modifier.clip(CircleShape))
                    } else {
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TroupePickerDialog(
    troupes: List<io.github.garemat.lunachron.Troupe>,
    onDismiss: () -> Unit,
    onSelect: (io.github.garemat.lunachron.Troupe) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Your Troupe") },
        text = {
            if (troupes.isEmpty()) {
                Text("No troupes found. Create a troupe in the Troupes section first.")
            } else {
                LazyColumn {
                    items(troupes) { troupe ->
                        TextButton(onClick = { onSelect(troupe) }, modifier = Modifier.fillMaxWidth()) {
                            Text(troupe.troupeName, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MemberTroupeDialog(
    member: OnlineCampaignMember,
    troupe: Troupe?,
    characters: List<Character>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(member.username, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (troupe == null) {
                Text("No troupe has been shared yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(troupe.troupeName, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(troupe.faction.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    troupe.characterIds.forEach { charId ->
                        val char = characters.find { it.id == charId }
                        if (char != null) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CharacterPortrait(character = char, size = 40.dp)
                                Text(char.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ── Active campaign home (tabs) ───────────────────────────────────────────────

@Composable
private fun ActiveCampaignHome(
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    isHost: Boolean,
    onEvent: (CharacterEvent) -> Unit,
    onDecodeTroupe: (String) -> Troupe?,
    onEncodeTroupe: (Troupe) -> String
) {
    val theme = LocalAppThemeProperties.current
    var activeTab by remember { mutableStateOf(ActiveTab.RANKINGS) }

    val inMachinationsPhase = campaign.phase == "MACHINATIONS"

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab row
        TabRow(selectedTabIndex = activeTab.ordinal, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            ActiveTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(when (tab) {
                            ActiveTab.RANKINGS    -> "Rankings"
                            ActiveTab.SCHEDULE    -> "Schedule"
                            ActiveTab.MACHINATIONS -> if (inMachinationsPhase) "Machinations" else "Submit"
                        }, style = MaterialTheme.typography.labelMedium)
                    },
                    icon = {
                        Icon(when (tab) {
                            ActiveTab.RANKINGS    -> Icons.Default.EmojiEvents
                            ActiveTab.SCHEDULE    -> Icons.Default.CalendarMonth
                            ActiveTab.MACHINATIONS -> if (inMachinationsPhase) Icons.Default.Star else Icons.Default.Send
                        }, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }

        when (activeTab) {
            ActiveTab.RANKINGS    -> OnlineRankingsTab(
                campaign = campaign, state = state, isHost = isHost, onEvent = onEvent,
                onDecodeTroupe = onDecodeTroupe)
            ActiveTab.SCHEDULE    -> OnlineScheduleTabContent(
                campaign = campaign,
                showCurrentRoundScores = inMachinationsPhase,
                isHost = isHost,
                onEvent = onEvent
            )
            ActiveTab.MACHINATIONS -> if (inMachinationsPhase) {
                OnlineMachinationsTab(campaign = campaign, state = state, onEvent = onEvent, onDecodeTroupe = onDecodeTroupe)
            } else {
                OnlineResultsTab(
                    campaign = campaign, state = state, onEvent = onEvent,
                    onDecodeTroupe = onDecodeTroupe, onEncodeTroupe = onEncodeTroupe)
            }
        }
    }
}

// ── Rankings tab ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineRankingsTab(
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    isHost: Boolean,
    onEvent: (CharacterEvent) -> Unit,
    onDecodeTroupe: (String) -> Troupe?
) {
    val theme = LocalAppThemeProperties.current
    val approved = campaign.members.filter { it.status == "APPROVED" }
        .sortedByDescending { it.powerPoints }

    var sheetMember by remember { mutableStateOf<OnlineCampaignMember?>(null) }
    val sheetMemberValue = sheetMember
    if (sheetMemberValue != null) {
        ModalBottomSheet(onDismissRequest = { sheetMember = null }) {
            TroupeDetailSheet(
                member = sheetMemberValue,
                campaign = campaign,
                state = state,
                onDecodeTroupe = onDecodeTroupe,
                theme = theme
            )
        }
    }

    if (approved.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No ranking data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val n = approved.size
    val tierSize = (n / 3).coerceAtLeast(1)
    val topBoundary = approved[tierSize - 1].powerPoints
    val actualTop = approved.count { it.powerPoints >= topBoundary }
    val remaining = approved.drop(actualTop)
    val bottomIdx = (remaining.size - tierSize).coerceAtLeast(0)
    val bottomBoundary = remaining.getOrNull(bottomIdx)?.powerPoints ?: Int.MIN_VALUE
    val prevBoundary = remaining.getOrNull(bottomIdx - 1)?.powerPoints
    val tiedAtBottom = prevBoundary == bottomBoundary
    fun tier(pp: Int) = when {
        pp >= topBoundary -> "Top"
        tiedAtBottom && pp == bottomBoundary -> "Middle"
        pp <= bottomBoundary -> "Bottom"
        else -> "Middle"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        // Header
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(52.dp))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                listOf("W" to theme.positiveColor, "D" to MaterialTheme.colorScheme.onSurfaceVariant, "L" to MaterialTheme.colorScheme.error).forEach { (lbl, clr) ->
                    Box(Modifier.width(32.dp), Alignment.Center) {
                        Text(lbl, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = clr)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(48.dp), Alignment.CenterEnd) {
                    Text("PP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Tier sections
        val tiers = listOf(
            Triple("Top Tier", theme.rankingGoldColor, Icons.Default.KeyboardArrowUp),
            Triple("Middle Tier", theme.rankingSilverColor, Icons.Default.Remove),
            Triple("Bottom Tier", theme.rankingBronzeColor, Icons.Default.KeyboardArrowDown)
        )
        val tierNames = listOf("Top", "Middle", "Bottom")
        val positions = approved.mapIndexed { i, m -> m.deviceId to i + 1 }.toMap()

        tierNames.zip(tiers).forEach { (tierName, tierInfo) ->
            val tierMembers = approved.filter { tier(it.powerPoints) == tierName }
            if (tierMembers.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(tierInfo.third, contentDescription = null, tint = tierInfo.second, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tierInfo.first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = tierInfo.second)
                    }
                }
                itemsIndexed(tierMembers) { idx, member ->
                    OnlineRankingCard(
                        member = member,
                        position = positions[member.deviceId] ?: 0,
                        rankColor = tierInfo.second,
                        rowIndex = idx,
                        theme = theme,
                        onClick = { sheetMember = member }
                    )
                }
            }
        }

    }
}

@Composable
private fun OnlineRankingCard(
    member: OnlineCampaignMember,
    position: Int,
    rankColor: Color,
    rowIndex: Int,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onClick: () -> Unit = {}
) {
    val bgAlpha = if (rowIndex % 2 == 0) 0.07f else 0.13f
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = rankColor.copy(alpha = bgAlpha))
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), Alignment.Center) {
                if (position == 1) Icon(Icons.Default.EmojiEvents, "1st", tint = rankColor, modifier = Modifier.size(30.dp))
                else Text("#$position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = rankColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${member.victoryPoints} VP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val totalMp = member.matchPoints + member.mpAdjustment + member.inGameMp
                    if (totalMp != 0) {
                        Text("${if (totalMp > 0) "+" else ""}$totalMp MP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(32.dp), Alignment.Center) { Text("${member.wins}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = theme.positiveColor) }
            Box(Modifier.width(32.dp), Alignment.Center) { Text("${member.draws}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(Modifier.width(32.dp), Alignment.Center) { Text("${member.losses}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(48.dp), Alignment.CenterEnd) {
                Text("${member.powerPoints}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ── Troupe detail bottom sheet ────────────────────────────────────────────────

@Composable
private fun TroupeDetailSheet(
    member: OnlineCampaignMember,
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    onDecodeTroupe: (String) -> Troupe?,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    // For round 1, use the member's initial troupe_data; for later rounds, prefer confirmed snapshot.
    val currentRound = campaign.currentRound
    val confirmedData = campaign.roundTroupes.find { it.deviceId == member.deviceId }?.troupeData
    val troupeDataToShow = if (currentRound == 1 || member.isLocal) member.troupeData else (confirmedData ?: member.troupeData)

    val currentTroupe = troupeDataToShow?.let { onDecodeTroupe(it) }
    val previousTroupe = campaign.previousRoundTroupes.find { it.deviceId == member.deviceId }
        ?.troupeData?.let { onDecodeTroupe(it) }
    val previousCharIds = previousTroupe?.characterIds?.toSet() ?: emptySet()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            member.username,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        when {
            troupeDataToShow == null -> Text(
                "No troupe data available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            !member.isLocal && confirmedData == null && currentRound > 1 -> Text(
                "Troupe not yet confirmed for Round $currentRound. Showing last known troupe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            currentTroupe == null -> Text(
                "Could not decode troupe data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            else -> {
                Text(
                    currentTroupe.troupeName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                currentTroupe.characterIds.forEachIndexed { index, charId ->
                    val character = state.characters.find { it.id == charId }
                    val isNew = charId !in previousCharIds && previousCharIds.isNotEmpty()
                    val upgradeIds = currentTroupe.equippedUpgrades[charId] ?: emptyList()
                    val upgrades = upgradeIds.mapNotNull { uid -> state.upgradeCards.find { it.id == uid } }

                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (character != null) {
                            CharacterPortrait(character = character, size = 48.dp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("?", style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    character?.name ?: "Character #$charId",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isNew) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text("New R$currentRound",
                                                style = MaterialTheme.typography.labelSmall)
                                        },
                                        modifier = Modifier.height(22.dp)
                                    )
                                }
                            }
                            upgrades.forEach { upgrade ->
                                Text(
                                    upgrade.name,
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
}

// ── Schedule tab ──────────────────────────────────────────────────────────────

@Composable
private fun OnlineScheduleTabContent(
    campaign: OnlineCampaignDetail,
    showCurrentRoundScores: Boolean = false,
    isHost: Boolean = false,
    onEvent: ((CharacterEvent) -> Unit)? = null
) {
    val theme = LocalAppThemeProperties.current
    val schedule = campaign.schedule
    if (schedule == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Schedule not yet published.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sortedRounds = schedule.entries
        .mapNotNull { (key, games) -> key.removePrefix("round").toIntOrNull()?.let { it to games } }
        .sortedBy { it.first }

    var expandedRounds by remember { mutableStateOf(setOf(campaign.currentRound)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        items(sortedRounds) { (roundNum, games) ->
            val isExpanded = roundNum in expandedRounds
            val isCurrent = roundNum == campaign.currentRound
            val sortedGames = games.entries.sortedBy { it.key }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { expandedRounds = if (isExpanded) expandedRounds - roundNum else expandedRounds + roundNum }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Round $roundNum${if (isCurrent) " — Current" else ""}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text("${sortedGames.size} game(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isExpanded) {
                        HorizontalDivider()
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            sortedGames.forEach { (gameKey, playerIds) ->
                                val gameNum = gameKey.removePrefix("game").toIntOrNull() ?: 0
                                val p1Id = playerIds.getOrNull(0)
                                val p2Id = playerIds.getOrNull(1)
                                val p1Name = campaign.members.find { it.deviceId == p1Id }?.username ?: "?"
                                val p2Name = campaign.members.find { it.deviceId == p2Id }?.username ?: "?"
                                val roundComplete = roundNum < campaign.currentRound ||
                                    (roundNum == campaign.currentRound && (showCurrentRoundScores || isHost))
                                val result = if (roundComplete) campaign.matchResults.firstOrNull {
                                    it.roundNumber == roundNum && it.gameNumber == gameNum
                                } else null
                                GameResultRow(
                                    p1Name = p1Name,
                                    p2Name = p2Name,
                                    result = result,
                                    p1Id = p1Id,
                                    p2Id = p2Id,
                                    isHost = isHost,
                                    campaignId = campaign.id,
                                    onEvent = onEvent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameResultRow(
    p1Name: String,
    p2Name: String,
    result: OnlineMatchResult?,
    p1Id: String?,
    p2Id: String?,
    isHost: Boolean = false,
    campaignId: String? = null,
    onEvent: ((CharacterEvent) -> Unit)? = null
) {
    if (result == null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(p1Name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text("vs", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp))
            Text(p2Name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        return
    }

    val p1Stats = result.resultData?.playerStats?.find { it.deviceId == p1Id }
    val p2Stats = result.resultData?.playerStats?.find { it.deviceId == p2Id }
    val p1Vp = (p1Stats?.moonstones ?: 0) + (p1Stats?.campaignCardVp ?: 0)
    val p2Vp = (p2Stats?.moonstones ?: 0) + (p2Stats?.campaignCardVp ?: 0)
    val p1Won = result.winnerId == p1Id
    val p2Won = result.winnerId == p2Id
    val isDraw = result.winnerId == null

    val statusColor = when (result.verifyStatus) {
        "VERIFIED" -> MaterialTheme.colorScheme.primary
        "DISPUTED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = when (result.verifyStatus) {
                "DISPUTED" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                "VERIFIED" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Score row: P1 name | VP — VP | P2 name
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p1Name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (p1Won) FontWeight.Bold else FontWeight.Normal,
                    color = if (p1Won) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$p1Vp — $p2Vp",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    p2Name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (p2Won) FontWeight.Bold else FontWeight.Normal,
                    color = if (p2Won) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            // Status row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    when (result.verifyStatus) {
                        "VERIFIED" -> Icons.Default.CheckCircle
                        "DISPUTED" -> Icons.Default.Warning
                        else -> Icons.Default.HourglassTop
                    },
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = statusColor
                )
                Text(
                    when {
                        result.verifyStatus == "VERIFIED" && isDraw  -> "Draw · Verified"
                        result.verifyStatus == "VERIFIED" && p1Won   -> "$p1Name won · Verified"
                        result.verifyStatus == "VERIFIED" && p2Won   -> "$p2Name won · Verified"
                        result.verifyStatus == "VERIFIED"            -> "Verified"
                        result.verifyStatus == "DISPUTED"            -> "Disputed · Contact Chamberlain"
                        else                                         -> "Pending verification"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            if (isHost && result.verifyStatus == "PENDING" && campaignId != null && onEvent != null) {
                TextButton(
                    onClick = { onEvent(CharacterEvent.OverrideMatchResult(campaignId, result.id)) },
                    modifier = Modifier.align(Alignment.End).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Force verify", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Record Results tab ────────────────────────────────────────────────────────

@Composable
private fun OnlineResultsTab(
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onDecodeTroupe: (String) -> Troupe?,
    onEncodeTroupe: (Troupe) -> String
) {
    val theme = LocalAppThemeProperties.current
    val myDeviceId = state.backendDeviceId
    val currentRound = campaign.currentRound
    val roundKey = "round$currentRound"
    val games = campaign.schedule?.get(roundKey) ?: emptyMap()

    // Find this player's game in the current round
    val myGameEntry = games.entries.firstOrNull { (_, players) -> myDeviceId in players }
    val myGameNumber = myGameEntry?.key?.removePrefix("game")?.toIntOrNull() ?: 0
    val myGamePlayers = myGameEntry?.value ?: emptyList()

    val opponentId = myGamePlayers.firstOrNull { it != myDeviceId }
    val opponentMember = campaign.members.find { it.deviceId == opponentId }
    val myMember = campaign.members.find { it.deviceId == myDeviceId }

    // Troupe confirmation state for this round (rounds > 1 only)
    val myConfirmed = campaign.roundTroupes.any { it.deviceId == myDeviceId }
    val opponentConfirmed = opponentId != null && campaign.roundTroupes.any { it.deviceId == opponentId }
    val opponentIsLocal = opponentMember?.isLocal == true
    val myTroupeData = myMember?.troupeData

    // Local players don't use the app — auto-confirm on their behalf when they're the opponent
    LaunchedEffect(campaign.id, currentRound, myConfirmed, opponentIsLocal) {
        if (currentRound > 1 && myGameEntry != null && opponentIsLocal && !myConfirmed && myTroupeData != null) {
            onEvent(CharacterEvent.ConfirmRoundTroupe(
                campaignId = campaign.id,
                roundNumber = currentRound,
                troupeData = myTroupeData
            ))
        }
    }

    // Find any existing match result for this game
    val existingResult = campaign.matchResults.firstOrNull {
        it.roundNumber == currentRound && it.gameNumber == myGameNumber
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
        when {
            myGameEntry == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Pause, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("No game scheduled for you in Round $currentRound.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("You have a bye this round.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            existingResult == null -> {
                // No result yet — show submission form
                RecordResultCard(
                    campaignId = campaign.id,
                    roundNumber = currentRound,
                    gameNumber = myGameNumber,
                    myMember = myMember,
                    opponentMember = opponentMember,
                    myDeviceId = myDeviceId,
                    campaignCards = state.campaignCards,
                    isSubmitting = state.isSubmittingMatchResult,
                    onDecodeTroupe = onDecodeTroupe,
                    onEvent = onEvent
                )
            }
            existingResult.verifyStatus == "VERIFIED" -> {
                ResultStatusCard(
                    roundNumber = currentRound,
                    gameNumber = myGameNumber,
                    result = existingResult,
                    myMember = myMember,
                    opponentMember = opponentMember,
                    myDeviceId = myDeviceId,
                    campaignCards = state.campaignCards
                )
            }
            existingResult.verifyStatus == "DISPUTED" -> {
                DisputedResultCard(
                    roundNumber = currentRound,
                    gameNumber = myGameNumber,
                    result = existingResult,
                    myMember = myMember,
                    opponentMember = opponentMember,
                    myDeviceId = myDeviceId,
                    campaignCards = state.campaignCards
                )
            }
            existingResult.submittedBy == myDeviceId -> {
                // I submitted — waiting for opponent
                WaitingVerificationCard(
                    roundNumber = currentRound,
                    gameNumber = myGameNumber,
                    result = existingResult,
                    myMember = myMember,
                    opponentMember = opponentMember,
                    myDeviceId = myDeviceId,
                    campaignCards = state.campaignCards,
                    onRefresh = { onEvent(CharacterEvent.LoadOnlineCampaign(campaign.id)) }
                )
            }
            else -> {
                // Opponent submitted — I need to verify
                VerifyResultCard(
                    campaignId = campaign.id,
                    roundNumber = currentRound,
                    gameNumber = myGameNumber,
                    result = existingResult,
                    myMember = myMember,
                    opponentMember = opponentMember,
                    myDeviceId = myDeviceId,
                    campaignCards = state.campaignCards,
                    isVerifying = state.isVerifyingMatchResult,
                    onEvent = onEvent
                )
            }
        }
        } // closes Box

        // Troupe confirmation (rounds 2+ with a scheduled game, not when opponent is local)
        if (currentRound > 1 && myGameEntry != null && !opponentIsLocal) {
            val troupeDataToConfirm = myMember?.troupeData
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = theme.cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Round $currentRound Troupe",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        myConfirmed && opponentConfirmed -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Both confirmed — view ${opponentMember?.username ?: "opponent"}'s troupe in Rankings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        myConfirmed -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Confirmed — waiting for ${opponentMember?.username ?: "opponent"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            Text(
                                "Confirm your troupe so your opponent can view it before the match.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (troupeDataToConfirm != null) {
                                        onEvent(CharacterEvent.ConfirmRoundTroupe(
                                            campaignId = campaign.id,
                                            roundNumber = currentRound,
                                            troupeData = troupeDataToConfirm
                                        ))
                                    }
                                },
                                enabled = troupeDataToConfirm != null && !state.isConfirmingRoundTroupe,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isConfirmingRoundTroupe) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Confirm Troupe")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Previous round machination results — collapsible, shows once round has advanced
        if (campaign.previousRoundMachinations.isNotEmpty()) {
            PreviousRoundMachinationsSection(
                previousRound = currentRound - 1,
                machinations = campaign.previousRoundMachinations,
                members = campaign.members.filter { it.status == "APPROVED" }
            )
        }
    } // closes Column
}

// OnlineMachinationsTab is in MachinationsPhaseUI.kt

private val MachinationSupportGreen = Color(0xFF2B9260)

@Composable
private fun PreviousRoundMachinationsSection(
    previousRound: Int,
    machinations: List<OnlineMachinationEntry>,
    members: List<OnlineCampaignMember>
) {
    val theme = LocalAppThemeProperties.current
    var expanded by remember { mutableStateOf(false) }

    data class PlayerResult(
        val username: String,
        val supporters: List<String>,
        val drawCount: Int
    )

    val results = remember(machinations, members) {
        val supportersOf = mutableMapOf<String, MutableList<String>>()
        val sabotageCountOf = mutableMapOf<String, Int>()
        for (entry in machinations) {
            for (choice in entry.choices) {
                if (choice.type == "SUPPORT") {
                    supportersOf.getOrPut(choice.targetDeviceId) { mutableListOf() }.add(entry.username)
                } else {
                    sabotageCountOf[choice.targetDeviceId] = (sabotageCountOf[choice.targetDeviceId] ?: 0) + 1
                }
            }
        }
        members.map { m ->
            val sup = supportersOf[m.deviceId] ?: emptyList()
            val sabCount = sabotageCountOf[m.deviceId] ?: 0
            PlayerResult(m.username, sup, (2 + sup.size - sabCount).coerceIn(1, 3))
        }.sortedByDescending { it.drawCount }
    }

    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Star, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Round $previousRound Machinations",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                results.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "${r.drawCount}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                r.username,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (r.supporters.isNotEmpty()) {
                                Text(
                                    "Supported by: ${r.supporters.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MachinationSupportGreen
                                )
                            }
                            if (r.supporters.isEmpty()) {
                                Text(
                                    "No operations",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Text(
                            "${r.drawCount} card${if (r.drawCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Stone counter + winner selection — shown when no result has been submitted yet. */
@Composable
private fun RecordResultCard(
    campaignId: String,
    roundNumber: Int,
    gameNumber: Int,
    myMember: OnlineCampaignMember?,
    opponentMember: OnlineCampaignMember?,
    myDeviceId: String,
    campaignCards: List<io.github.garemat.lunachron.CampaignCard>,
    isSubmitting: Boolean,
    onDecodeTroupe: (String) -> Troupe?,
    onEvent: (CharacterEvent) -> Unit
) {
    val theme = LocalAppThemeProperties.current

    // Stone counts
    var myStones by remember { mutableIntStateOf(0) }
    var opponentStones by remember { mutableIntStateOf(0) }
    // Campaign card VP modifiers
    var myCardVp by remember { mutableIntStateOf(0) }
    var opponentCardVp by remember { mutableIntStateOf(0) }
    // Campaign card MP modifiers (in-game grants/deductions)
    var myCardMp by remember { mutableIntStateOf(0) }
    var opponentCardMp by remember { mutableIntStateOf(0) }
    // Campaign cards used (set of share codes)
    var myUsedCardCodes by remember { mutableStateOf(setOf<String>()) }
    var opponentUsedCardCodes by remember { mutableStateOf(setOf<String>()) }

    // Decode troupes to get campaign card lists
    val myTroupe = remember(myMember?.troupeData) {
        myMember?.troupeData?.let { runCatching { onDecodeTroupe(it) }.getOrNull() }
    }
    val opponentTroupe = remember(opponentMember?.troupeData) {
        opponentMember?.troupeData?.let { runCatching { onDecodeTroupe(it) }.getOrNull() }
    }

    // Resolve CampaignCard objects from troupe card IDs
    val myCampaignCards = remember(myTroupe, campaignCards) {
        myTroupe?.campaignCards?.mapNotNull { tcc -> campaignCards.find { it.id == tcc.cardId } } ?: emptyList()
    }
    val opponentCampaignCards = remember(opponentTroupe, campaignCards) {
        opponentTroupe?.campaignCards?.mapNotNull { tcc -> campaignCards.find { it.id == tcc.cardId } } ?: emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Round $roundNumber · Game $gameNumber",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)

                    // ── Moonstones ────────────────────────────────────────────
                    Text("Moonstones gathered", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerResultSide(myMember?.username ?: "You", myStones, { myStones = it }, Modifier.weight(1f))
                        Box(Modifier.align(Alignment.CenterVertically)) {
                            Text("vs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PlayerResultSide(opponentMember?.username ?: "Opponent", opponentStones, { opponentStones = it }, Modifier.weight(1f))
                    }

                    HorizontalDivider()

                    // ── Campaign cards ────────────────────────────────────────
                    Text("Campaign cards used", style = MaterialTheme.typography.labelMedium)

                    // My cards
                    if (myCampaignCards.isNotEmpty()) {
                        Text(myMember?.username ?: "You",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            myCampaignCards.forEach { card ->
                                FilterChip(
                                    selected = card.shareCode in myUsedCardCodes,
                                    onClick = {
                                        myUsedCardCodes = if (card.shareCode in myUsedCardCodes)
                                            myUsedCardCodes - card.shareCode
                                        else
                                            myUsedCardCodes + card.shareCode
                                    },
                                    label = { Text(card.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    // Opponent cards
                    if (opponentCampaignCards.isNotEmpty()) {
                        Text(opponentMember?.username ?: "Opponent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            opponentCampaignCards.forEach { card ->
                                FilterChip(
                                    selected = card.shareCode in opponentUsedCardCodes,
                                    onClick = {
                                        opponentUsedCardCodes = if (card.shareCode in opponentUsedCardCodes)
                                            opponentUsedCardCodes - card.shareCode
                                        else
                                            opponentUsedCardCodes + card.shareCode
                                    },
                                    label = { Text(card.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    if (myCampaignCards.isEmpty() && opponentCampaignCards.isEmpty()) {
                        Text("No campaign cards in either troupe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider()

                    // ── Campaign card VP modifier ─────────────────────────────
                    Text("Campaign card VP modifier", style = MaterialTheme.typography.labelMedium)
                    Text("Adjust VP for campaign cards that grant or deduct victory points.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VpModifierSide(myMember?.username ?: "You", myCardVp, { myCardVp = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        VpModifierSide(opponentMember?.username ?: "Opponent", opponentCardVp, { opponentCardVp = it }, Modifier.weight(1f))
                    }

                    // ── Campaign card MP modifier ─────────────────────────────
                    Text("Campaign card MP modifier", style = MaterialTheme.typography.labelMedium)
                    Text("Adjust MP for campaign cards that grant or deduct mission points during the game.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VpModifierSide(myMember?.username ?: "You", myCardMp, { myCardMp = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        VpModifierSide(opponentMember?.username ?: "Opponent", opponentCardMp, { opponentCardMp = it }, Modifier.weight(1f))
                    }

                    HorizontalDivider()

                    // ── Winner preview (auto-calculated from VP) ──────────────
                    val myTotalVp = myStones + myCardVp
                    val opponentTotalVp = opponentStones + opponentCardVp
                    val projectedWinner = when {
                        myTotalVp > opponentTotalVp -> myMember?.username ?: "You"
                        opponentTotalVp > myTotalVp -> opponentMember?.username ?: "Opponent"
                        else -> null // draw
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (projectedWinner != null) "Winner: $projectedWinner ($myTotalVp — $opponentTotalVp VP)"
                            else "Draw ($myTotalVp — $opponentTotalVp VP)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            val winnerId = when {
                                myTotalVp > opponentTotalVp -> myDeviceId
                                opponentTotalVp > myTotalVp -> opponentMember?.deviceId
                                else -> null
                            }
                            val playerStats = listOf(
                                OnlinePlayerStat(myDeviceId, myMember?.username ?: "",
                                    myStones, myCardVp, myCardMp, myUsedCardCodes.toList()),
                                OnlinePlayerStat(opponentMember?.deviceId ?: "", opponentMember?.username ?: "",
                                    opponentStones, opponentCardVp, opponentCardMp, opponentUsedCardCodes.toList())
                            )
                            onEvent(CharacterEvent.SubmitOnlineMatchResult(campaignId, roundNumber, gameNumber, playerStats, winnerId))
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Submit Result")
                        }
                    }

                    Text("Your opponent will need to verify the result.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VpModifierSide(
    name: String,
    vp: Int,
    onVpChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onVpChange(vp - 1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = if (vp > 0) "+$vp" else "$vp",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    vp > 0 -> MaterialTheme.colorScheme.primary
                    vp < 0 -> MaterialTheme.colorScheme.error
                    else   -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = { onVpChange(vp + 1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Shown to the submitter while they wait for the opponent to verify. */
@Composable
private fun WaitingVerificationCard(
    roundNumber: Int,
    gameNumber: Int,
    result: OnlineMatchResult,
    myMember: OnlineCampaignMember?,
    opponentMember: OnlineCampaignMember?,
    myDeviceId: String,
    campaignCards: List<CampaignCard>,
    onRefresh: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val myStats = result.resultData?.playerStats?.find { it.deviceId == myDeviceId }
    val oppStats = result.resultData?.playerStats?.find { it.deviceId != myDeviceId }
    val winnerLabel = when (result.winnerId) {
        myDeviceId -> myMember?.username ?: "You"
        null       -> "Draw"
        else       -> opponentMember?.username ?: "Opponent"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.HourglassTop, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Result Submitted", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Round $roundNumber · Game $gameNumber",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider()

                    // Summary of submitted result
                    ResultSummaryRow(
                        myName = myMember?.username ?: "You",
                        opponentName = opponentMember?.username ?: "Opponent",
                        myStones = myStats?.moonstones ?: 0,
                        opponentStones = oppStats?.moonstones ?: 0,
                        winnerLabel = winnerLabel,
                        myCardVp = myStats?.campaignCardVp ?: 0,
                        opponentCardVp = oppStats?.campaignCardVp ?: 0
                    )

                    val allStats = result.resultData?.playerStats ?: emptyList()
                    if (allStats.any { it.campaignCardCodes.isNotEmpty() }) {
                        HorizontalDivider()
                        UsedCampaignCardsSection(allStats, myDeviceId, myMember, opponentMember, campaignCards)
                    }

                    HorizontalDivider()

                    Text("Waiting for ${opponentMember?.username ?: "your opponent"} to verify…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check for Update")
                    }
                }
            }
        }
    }
}

/** Shown to the opponent — lets them approve or contest the submitted result. */
@Composable
private fun VerifyResultCard(
    campaignId: String,
    roundNumber: Int,
    gameNumber: Int,
    result: OnlineMatchResult,
    myMember: OnlineCampaignMember?,
    opponentMember: OnlineCampaignMember?,
    myDeviceId: String,
    campaignCards: List<CampaignCard>,
    isVerifying: Boolean,
    onEvent: (CharacterEvent) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val myStats = result.resultData?.playerStats?.find { it.deviceId == myDeviceId }
    val oppStats = result.resultData?.playerStats?.find { it.deviceId != myDeviceId }
    val winnerLabel = when (result.winnerId) {
        myDeviceId -> myMember?.username ?: "You"
        null       -> "Draw"
        else       -> opponentMember?.username ?: "Opponent"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Gavel, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Verify Result", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text("${opponentMember?.username ?: "Your opponent"} has submitted the result for Round $roundNumber · Game $gameNumber.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)

                    HorizontalDivider()

                    ResultSummaryRow(
                        myName = myMember?.username ?: "You",
                        opponentName = opponentMember?.username ?: "Opponent",
                        myStones = myStats?.moonstones ?: 0,
                        opponentStones = oppStats?.moonstones ?: 0,
                        winnerLabel = winnerLabel,
                        myCardVp = myStats?.campaignCardVp ?: 0,
                        opponentCardVp = oppStats?.campaignCardVp ?: 0
                    )

                    val allStats = result.resultData?.playerStats ?: emptyList()
                    if (allStats.any { it.campaignCardCodes.isNotEmpty() }) {
                        HorizontalDivider()
                        UsedCampaignCardsSection(allStats, myDeviceId, myMember, opponentMember, campaignCards)
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onEvent(CharacterEvent.VerifyMatchResult(campaignId, result.id, agree = false)) },
                            enabled = !isVerifying,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Contest")
                            }
                        }
                        Button(
                            onClick = { onEvent(CharacterEvent.VerifyMatchResult(campaignId, result.id, agree = true)) },
                            enabled = !isVerifying,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Approve")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shown when the result has been verified. */
@Composable
private fun ResultStatusCard(
    roundNumber: Int,
    gameNumber: Int,
    result: OnlineMatchResult,
    myMember: OnlineCampaignMember?,
    opponentMember: OnlineCampaignMember?,
    myDeviceId: String,
    campaignCards: List<CampaignCard>
) {
    val theme = LocalAppThemeProperties.current
    val myStats = result.resultData?.playerStats?.find { it.deviceId == myDeviceId }
    val oppStats = result.resultData?.playerStats?.find { it.deviceId != myDeviceId }
    val winnerLabel = when (result.winnerId) {
        myDeviceId -> myMember?.username ?: "You"
        null       -> "Draw"
        else       -> opponentMember?.username ?: "Opponent"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Result Confirmed", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Round $roundNumber · Game $gameNumber",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider()

                    ResultSummaryRow(
                        myName = myMember?.username ?: "You",
                        opponentName = opponentMember?.username ?: "Opponent",
                        myStones = myStats?.moonstones ?: 0,
                        opponentStones = oppStats?.moonstones ?: 0,
                        winnerLabel = winnerLabel,
                        myCardVp = myStats?.campaignCardVp ?: 0,
                        opponentCardVp = oppStats?.campaignCardVp ?: 0
                    )

                    val allStats = result.resultData?.playerStats ?: emptyList()
                    if (allStats.any { it.campaignCardCodes.isNotEmpty() }) {
                        HorizontalDivider()
                        UsedCampaignCardsSection(allStats, myDeviceId, myMember, opponentMember, campaignCards)
                    }
                }
            }
        }
    }
}

/** Shown when the result has been disputed — host must resolve. */
@Composable
private fun DisputedResultCard(
    roundNumber: Int,
    gameNumber: Int,
    result: OnlineMatchResult,
    myMember: OnlineCampaignMember?,
    opponentMember: OnlineCampaignMember?,
    myDeviceId: String,
    campaignCards: List<CampaignCard>
) {
    val theme = LocalAppThemeProperties.current
    val myStats = result.resultData?.playerStats?.find { it.deviceId == myDeviceId }
    val oppStats = result.resultData?.playerStats?.find { it.deviceId != myDeviceId }
    val winnerLabel = when (result.winnerId) {
        myDeviceId -> myMember?.username ?: "You"
        null       -> "Draw"
        else       -> opponentMember?.username ?: "Opponent"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Result Disputed", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Text("Round $roundNumber · Game $gameNumber",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("The submitted result has been contested. Please contact your Chamberlain to resolve this.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)

                    HorizontalDivider()

                    ResultSummaryRow(
                        myName = myMember?.username ?: "You",
                        opponentName = opponentMember?.username ?: "Opponent",
                        myStones = myStats?.moonstones ?: 0,
                        opponentStones = oppStats?.moonstones ?: 0,
                        winnerLabel = winnerLabel,
                        myCardVp = myStats?.campaignCardVp ?: 0,
                        opponentCardVp = oppStats?.campaignCardVp ?: 0
                    )

                    val allStats = result.resultData?.playerStats ?: emptyList()
                    if (allStats.any { it.campaignCardCodes.isNotEmpty() }) {
                        HorizontalDivider()
                        UsedCampaignCardsSection(allStats, myDeviceId, myMember, opponentMember, campaignCards)
                    }
                }
            }
        }
    }
}

/** Reusable two-column stone count + winner summary row. */
@Composable
private fun ResultSummaryRow(
    myName: String,
    opponentName: String,
    myStones: Int,
    opponentStones: Int,
    winnerLabel: String,
    myCardVp: Int = 0,
    opponentCardVp: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(myName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$myStones \uD83C\uDF19", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (myCardVp != 0) {
                    Text(
                        text = if (myCardVp > 0) "+$myCardVp VP (cards)" else "$myCardVp VP (cards)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (myCardVp > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(opponentName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$opponentStones \uD83C\uDF19", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (opponentCardVp != 0) {
                    Text(
                        text = if (opponentCardVp > 0) "+$opponentCardVp VP (cards)" else "$opponentCardVp VP (cards)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (opponentCardVp > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("Winner: $winnerLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

/** Shows campaign cards used by both players in a submitted result, with tap-to-details. */
@Composable
private fun UsedCampaignCardsSection(
    allStats: List<OnlinePlayerStat>,
    myDeviceId: String,
    myMember: OnlineCampaignMember?,
    opponentMember: OnlineCampaignMember?,
    campaignCards: List<CampaignCard>
) {
    var selectedCard by remember { mutableStateOf<CampaignCard?>(null) }
    selectedCard?.let { card ->
        AlertDialog(
            onDismissRequest = { selectedCard = null },
            title = { Text(card.name, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.timing, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(card.description, style = MaterialTheme.typography.bodySmall)
                    card.extraDescription?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCard = null }) { Text("Close") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Campaign cards used", style = MaterialTheme.typography.labelMedium)
        allStats.forEach { stat ->
            if (stat.campaignCardCodes.isEmpty()) return@forEach
            val playerName = if (stat.deviceId == myDeviceId)
                myMember?.username ?: "You"
            else
                opponentMember?.username ?: "Opponent"
            Text(playerName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stat.campaignCardCodes.forEach { code ->
                    val card = campaignCards.find { it.shareCode == code }
                    AssistChip(
                        onClick = { card?.let { selectedCard = it } },
                        label = { Text(card?.name ?: code, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerResultSide(
    name: String,
    stones: Int,
    onStonesChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Moonstones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (stones > 0) onStonesChange(stones - 1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp))
            }
            Text("$stones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = { onStonesChange(stones + 1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Schedule generation section (host, all ready, no schedule) ────────────────

@Composable
private fun ScheduleGenerationSection(
    state: CharacterState,
    campaignId: String,
    approvedMembers: List<OnlineCampaignMember>,
    onEvent: (CharacterEvent) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val roundCount = state.onlineScheduleRoundCount
    val maxRounds = approvedMembers.size.coerceAtLeast(1) * 2
    val pendingSchedule = state.pendingOnlineSchedule

    Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Text("Generate Schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rounds:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (roundCount > 1) onEvent(CharacterEvent.SetOnlineScheduleRoundCount(roundCount - 1)) }, enabled = roundCount > 1) {
                    Icon(Icons.Default.Remove, null)
                }
                Text("$roundCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = { if (roundCount < maxRounds) onEvent(CharacterEvent.SetOnlineScheduleRoundCount(roundCount + 1)) }, enabled = roundCount < maxRounds) {
                    Icon(Icons.Default.Add, null)
                }
            }
            OutlinedButton(onClick = { onEvent(CharacterEvent.GenerateOnlineSchedule(campaignId, roundCount)) },
                modifier = Modifier.fillMaxWidth(), enabled = approvedMembers.size >= 2 && roundCount > 0) {
                Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (pendingSchedule != null) "Regenerate" else "Auto-Generate Schedule")
            }
            if (pendingSchedule != null) {
                HorizontalDivider()
                Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SchedulePreview(
                    rounds = pendingSchedule,
                    members = approvedMembers,
                    onSwapBye = { roundNumber, newByePlayerId ->
                        onEvent(CharacterEvent.SwapScheduleBye(roundNumber, newByePlayerId))
                    }
                )
                if (state.pendingScheduleError != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(state.pendingScheduleError, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Button(
                    onClick = { onEvent(CharacterEvent.PublishOnlineSchedule(campaignId)) },
                    enabled = !state.isPublishingSchedule && state.pendingScheduleError == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isPublishingSchedule) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Default.Publish, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Publish Schedule") }
                }
            }
        }
    }
}

@Composable
private fun SchedulePreview(
    rounds: List<CampaignRound>,
    members: List<OnlineCampaignMember>,
    onSwapBye: ((roundNumber: Int, newByePlayerId: String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rounds.forEach { round ->
            Text("Round ${round.roundNumber}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            round.games.forEach { game ->
                val p1 = members.find { it.deviceId == game.playerIds.getOrNull(0) }?.username ?: "?"
                val p2 = members.find { it.deviceId == game.playerIds.getOrNull(1) }?.username ?: "?"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(p1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                    Text(p2, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
            if (round.skipPlayerIds.isNotEmpty()) {
                val byeNames = round.skipPlayerIds.mapNotNull { id -> members.find { it.deviceId == id }?.username }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pause, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Bye: ${byeNames.joinToString(", ")}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (onSwapBye != null) {
                        var dropdownOpen by remember { mutableStateOf(false) }
                        val playingIds = round.games.flatMap { it.playerIds }
                        Box {
                            IconButton(onClick = { dropdownOpen = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.SwapHoriz, "Move bye", Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                                Text("Give bye to…", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                playingIds.forEach { playerId ->
                                    val name = members.find { it.deviceId == playerId }?.username ?: playerId
                                    DropdownMenuItem(
                                        text = { Text(name, style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            dropdownOpen = false
                                            onSwapBye(round.roundNumber, playerId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

// ── Admin panel (host only) ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPanelSheet(
    campaign: OnlineCampaignDetail,
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onDismiss: () -> Unit,
    onDecodeTroupe: (String) -> Troupe?,
    onEncodeTroupe: (Troupe) -> String
) {
    val theme = LocalAppThemeProperties.current
    var localPlayerName by remember { mutableStateOf("") }
    val localMembers = campaign.members.filter { it.isLocal && it.status == "APPROVED" }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.navigationBarsPadding()
        ) {
            // Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("Chamberlain Admin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            item { HorizontalDivider() }

            // Add local player
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Local Player", style = MaterialTheme.typography.labelMedium)
                    Text("Add a player who doesn't have the app. They appear in the schedule and member list.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = localPlayerName,
                            onValueChange = { localPlayerName = it },
                            label = { Text("Player name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmed = localPlayerName.trim()
                                if (trimmed.isNotEmpty()) {
                                    onEvent(CharacterEvent.AddLocalCampaignMember(campaign.id, trimmed))
                                    localPlayerName = ""
                                }
                            },
                            enabled = localPlayerName.isNotBlank() && !state.isAddingLocalMember
                        ) {
                            if (state.isAddingLocalMember) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Add")
                            }
                        }
                    }
                }
            }

            // Existing local players (troupe + ready management — pre-game phases)
            if (localMembers.isNotEmpty() && campaign.schedule == null) {
                item { HorizontalDivider() }
                item { Text("Manage Local Players", style = MaterialTheme.typography.labelMedium) }
                items(localMembers) { member ->
                    LocalPlayerAdminRow(
                        member = member,
                        campaignId = campaign.id,
                        troupes = state.troupes,
                        state = state,
                        onEvent = onEvent,
                        onDecodeTroupe = onDecodeTroupe,
                        onEncodeTroupe = onEncodeTroupe,
                        theme = theme
                    )
                }
            }

            // Active phase — result submission for local player games
            if (campaign.schedule != null && localMembers.isNotEmpty()) {
                val localMemberIds = localMembers.map { it.deviceId }.toSet()
                val roundKey = "round${campaign.currentRound}"
                val roundGames = campaign.schedule[roundKey] ?: emptyMap()
                val localGames = roundGames.entries
                    .filter { (_, players) -> players.any { it in localMemberIds } }
                    .sortedBy { it.key }

                if (localGames.isNotEmpty()) {
                    item { HorizontalDivider() }
                    item {
                        Text("Round ${campaign.currentRound} — Local Player Games",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    items(localGames) { (gameKey, playerIds) ->
                        val gameNum = gameKey.removePrefix("game").toIntOrNull() ?: 0
                        val localPlayerId  = playerIds.firstOrNull { it in localMemberIds } ?: ""
                        val opponentId     = playerIds.firstOrNull { it != localPlayerId } ?: ""
                        val localMember    = localMembers.find { it.deviceId == localPlayerId }
                        val opponentMember = campaign.members.find { it.deviceId == opponentId }
                        val existingResult = campaign.matchResults.firstOrNull {
                            it.roundNumber == campaign.currentRound && it.gameNumber == gameNum
                        }
                        AdminLocalGameCard(
                            campaignId      = campaign.id,
                            roundNumber     = campaign.currentRound,
                            gameNumber      = gameNum,
                            localMember     = localMember,
                            opponentMember  = opponentMember,
                            localPlayerId   = localPlayerId,
                            opponentId      = opponentId,
                            existingResult  = existingResult,
                            campaignCards   = state.campaignCards,
                            isSubmitting    = state.isSubmittingMatchResult,
                            onDecodeTroupe  = onDecodeTroupe,
                            onEvent         = onEvent,
                            theme           = theme
                        )
                    }
                }
            }

            // Machinations overview — host-only tally of all submitted choices
            if (campaign.phase == "MACHINATIONS") {
                item { HorizontalDivider() }
                item {
                    MachinationsOverviewSection(
                        members = campaign.members.filter { it.status == "APPROVED" },
                        machinations = campaign.machinations
                    )
                }
            }

            // Machinations phase — host submits on behalf of local players
            if (campaign.phase == "MACHINATIONS" && localMembers.isNotEmpty()) {
                item { HorizontalDivider() }
                item {
                    Text("Round ${campaign.currentRound} — Local Player Machinations",
                        style = MaterialTheme.typography.labelMedium)
                }
                items(localMembers) { localMember ->
                    val submitted = campaign.machinations.find { it.deviceId == localMember.deviceId }
                    val nextRoundKey2 = "round${campaign.currentRound + 1}"
                    val nextRoundGames2 = campaign.schedule?.get(nextRoundKey2) ?: emptyMap()
                    val localNextOpp = nextRoundGames2.values
                        .firstOrNull { localMember.deviceId in it }
                        ?.firstOrNull { it != localMember.deviceId }
                    val localHasByeBonus = campaign.schedule?.containsKey(nextRoundKey2) == true && localNextOpp == null
                    val localHasMpBonus = !localHasByeBonus && localMember.bonusMp > 0
                    val otherApproved = campaign.members.filter {
                        it.status == "APPROVED" && it.deviceId != localMember.deviceId && it.deviceId != localNextOpp
                    }
                    AdminLocalMachinationCard(
                        member        = localMember,
                        campaignId    = campaign.id,
                        otherMembers  = otherApproved,
                        submitted     = submitted,
                        has3rdSlot    = localHasByeBonus || localHasMpBonus,
                        isSubmitting  = state.isSubmittingMachination,
                        onEvent       = onEvent,
                        theme         = theme
                    )
                }
            }

            // ── Adjust Player Points (Wizard Chamberlain correction) ──────
            val approvedMembers = campaign.members.filter { it.status == "APPROVED" }
            if (approvedMembers.isNotEmpty()) {
                item { HorizontalDivider() }
                item {
                    AdjustPointsSection(
                        campaignId = campaign.id,
                        members = approvedMembers,
                        isAdjusting = state.isAdjustingPoints,
                        onEvent = onEvent,
                        theme = theme
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustPointsSection(
    campaignId: String,
    members: List<OnlineCampaignMember>,
    isAdjusting: Boolean,
    onEvent: (CharacterEvent) -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    var selectedMember by remember(members) { mutableStateOf(members.first()) }
    var dropdownOpen   by remember { mutableStateOf(false) }
    var mpDelta        by remember { mutableIntStateOf(0) }
    var vpDelta        by remember { mutableIntStateOf(0) }
    var note           by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Adjust Player Points", style = MaterialTheme.typography.labelMedium)
        Text("Correct a player's VP or MP totals (e.g. campaign card effect or scoring error).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Player picker
        ExposedDropdownMenuBox(
            expanded = dropdownOpen,
            onExpandedChange = { dropdownOpen = it }
        ) {
            OutlinedTextField(
                value = selectedMember.username,
                onValueChange = {},
                readOnly = true,
                label = { Text("Player") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = dropdownOpen,
                onDismissRequest = { dropdownOpen = false }
            ) {
                members.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.username) },
                        onClick = { selectedMember = m; dropdownOpen = false }
                    )
                }
            }
        }

        // MP delta
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MP adjustment", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { mpDelta-- }) { Icon(Icons.Default.Remove, null) }
            Text(if (mpDelta >= 0) "+$mpDelta" else "$mpDelta",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { mpDelta++ }) { Icon(Icons.Default.Add, null) }
        }

        // VP delta
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("VP adjustment", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { vpDelta-- }) { Icon(Icons.Default.Remove, null) }
            Text(if (vpDelta >= 0) "+$vpDelta" else "$vpDelta",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { vpDelta++ }) { Icon(Icons.Default.Add, null) }
        }

        // Optional note
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onEvent(CharacterEvent.AdjustPlayerPoints(campaignId, selectedMember.deviceId, mpDelta, vpDelta, note.trim()))
                mpDelta = 0; vpDelta = 0; note = ""
            },
            enabled = !isAdjusting && (mpDelta != 0 || vpDelta != 0),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isAdjusting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun LocalPlayerAdminRow(
    member: OnlineCampaignMember,
    campaignId: String,
    troupes: List<Troupe>,
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onDecodeTroupe: (String) -> Troupe?,
    onEncodeTroupe: (Troupe) -> String,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    var showTroupePicker by remember { mutableStateOf(false) }

    if (showTroupePicker) {
        TroupePickerDialog(
            troupes = troupes,
            onDismiss = { showTroupePicker = false },
            onSelect = { troupe ->
                showTroupePicker = false
                val encoded = onEncodeTroupe(troupe)
                onEvent(CharacterEvent.UploadLocalMemberTroupe(campaignId, member.deviceId, encoded))
            }
        )
    }

    val troupe = member.troupeData?.let { runCatching { onDecodeTroupe(it) }.getOrNull() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PersonOff, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(member.username, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(
                    if (member.isReady) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    null, Modifier.size(16.dp),
                    tint = if (member.isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                troupe?.troupeName ?: "No troupe selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showTroupePicker = true },
                    enabled = !state.isUploadingTroupe,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isUploadingTroupe) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (member.troupeData != null) "Change Troupe" else "Set Troupe",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = { onEvent(CharacterEvent.SetLocalMemberReady(campaignId, member.deviceId, !member.isReady)) },
                    enabled = member.troupeData != null && !state.isSettingReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (member.isReady) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isSettingReady) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (member.isReady) "Unready" else "Ready",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Compact result-entry card shown in the admin panel for each local player game. */
@Composable
private fun AdminLocalGameCard(
    campaignId: String,
    roundNumber: Int,
    gameNumber: Int,
    localMember: OnlineCampaignMember?,
    opponentMember: OnlineCampaignMember?,
    localPlayerId: String,
    opponentId: String,
    existingResult: OnlineMatchResult?,
    campaignCards: List<CampaignCard>,
    isSubmitting: Boolean,
    onDecodeTroupe: (String) -> Troupe?,
    onEvent: (CharacterEvent) -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    // If the result is already verified, just show a summary
    if (existingResult?.verifyStatus == "VERIFIED") {
        val localStats = existingResult.resultData?.playerStats?.find { it.deviceId == localPlayerId }
        val oppStats   = existingResult.resultData?.playerStats?.find { it.deviceId == opponentId }
        val localVp    = (localStats?.moonstones ?: 0) + (localStats?.campaignCardVp ?: 0)
        val oppVp      = (oppStats?.moonstones ?: 0) + (oppStats?.campaignCardVp ?: 0)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Game $gameNumber · Result recorded",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("${localMember?.username ?: "Local"} $localVp — $oppVp ${opponentMember?.username ?: "Opponent"}",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
        return
    }

    // Entry form
    var localStones       by remember { mutableIntStateOf(0) }
    var opponentStones    by remember { mutableIntStateOf(0) }
    var localCardVp       by remember { mutableIntStateOf(0) }
    var opponentCardVp    by remember { mutableIntStateOf(0) }
    var localCardMp       by remember { mutableIntStateOf(0) }
    var opponentCardMp    by remember { mutableIntStateOf(0) }
    var localUsedCards    by remember { mutableStateOf(setOf<String>()) }
    var opponentUsedCards by remember { mutableStateOf(setOf<String>()) }

    val localTroupe    = remember(localMember?.troupeData)    { localMember?.troupeData?.let    { runCatching { onDecodeTroupe(it) }.getOrNull() } }
    val opponentTroupe = remember(opponentMember?.troupeData) { opponentMember?.troupeData?.let { runCatching { onDecodeTroupe(it) }.getOrNull() } }
    val localCards    = remember(localTroupe, campaignCards)    { localTroupe?.campaignCards?.mapNotNull    { tc -> campaignCards.find { it.id == tc.cardId } } ?: emptyList() }
    val opponentCards = remember(opponentTroupe, campaignCards) { opponentTroupe?.campaignCards?.mapNotNull { tc -> campaignCards.find { it.id == tc.cardId } } ?: emptyList() }

    val localVp    = localStones + localCardVp
    val oppVp      = opponentStones + opponentCardVp
    val winnerId   = when {
        localVp > oppVp -> localPlayerId
        oppVp > localVp -> opponentId.ifEmpty { null }
        else            -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Game $gameNumber · ${localMember?.username ?: "Local"} vs ${opponentMember?.username ?: "Opponent"}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            // Moonstone steppers
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerResultSide(localMember?.username ?: "Local", localStones,    { localStones = it },    Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterVertically)) {
                    Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlayerResultSide(opponentMember?.username ?: "Opponent", opponentStones, { opponentStones = it }, Modifier.weight(1f))
            }

            // Campaign card chips
            if (localCards.isNotEmpty() || opponentCards.isNotEmpty()) {
                HorizontalDivider()
                Text("Campaign cards used", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (localCards.isNotEmpty()) {
                    Text(localMember?.username ?: "Local", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        localCards.forEach { card ->
                            FilterChip(
                                selected = card.shareCode in localUsedCards,
                                onClick  = { localUsedCards = if (card.shareCode in localUsedCards) localUsedCards - card.shareCode else localUsedCards + card.shareCode },
                                label    = { Text(card.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                if (opponentCards.isNotEmpty()) {
                    Text(opponentMember?.username ?: "Opponent", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        opponentCards.forEach { card ->
                            FilterChip(
                                selected = card.shareCode in opponentUsedCards,
                                onClick  = { opponentUsedCards = if (card.shareCode in opponentUsedCards) opponentUsedCards - card.shareCode else opponentUsedCards + card.shareCode },
                                label    = { Text(card.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // VP modifiers
            HorizontalDivider()
            Text("Campaign card VP modifier", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VpModifierSide(localMember?.username ?: "Local", localCardVp,    { localCardVp = it },    Modifier.weight(1f))
                VpModifierSide(opponentMember?.username ?: "Opponent", opponentCardVp, { opponentCardVp = it }, Modifier.weight(1f))
            }

            // MP modifiers
            Text("Campaign card MP modifier", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VpModifierSide(localMember?.username ?: "Local", localCardMp,    { localCardMp = it },    Modifier.weight(1f))
                VpModifierSide(opponentMember?.username ?: "Opponent", opponentCardMp, { opponentCardMp = it }, Modifier.weight(1f))
            }

            // Winner preview
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.EmojiEvents, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        localVp > oppVp -> "${localMember?.username ?: "Local"} wins ($localVp — $oppVp VP)"
                        oppVp > localVp -> "${opponentMember?.username ?: "Opponent"} wins ($localVp — $oppVp VP)"
                        else            -> "Draw ($localVp — $oppVp VP)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = {
                    val stats = listOf(
                        OnlinePlayerStat(localPlayerId, localMember?.username ?: "",
                            localStones, localCardVp, localCardMp, localUsedCards.toList()),
                        OnlinePlayerStat(opponentId, opponentMember?.username ?: "",
                            opponentStones, opponentCardVp, opponentCardMp, opponentUsedCards.toList())
                    )
                    onEvent(CharacterEvent.SubmitOnlineMatchResult(campaignId, roundNumber, gameNumber, stats, winnerId))
                },
                enabled = !isSubmitting && localPlayerId.isNotEmpty() && opponentId.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Send, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Submit", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MachinationsOverviewSection(
    members: List<OnlineCampaignMember>,
    machinations: List<OnlineMachinationEntry>
) {
    data class Tally(val supports: Int = 0, val sabotages: Int = 0)

    val submittedIds = machinations.map { it.deviceId }.toSet()
    val tally = buildMap<String, Tally> {
        for (entry in machinations) {
            for (choice in entry.choices) {
                val cur = getOrDefault(choice.targetDeviceId, Tally())
                put(choice.targetDeviceId, if (choice.type == "SUPPORT")
                    cur.copy(supports = cur.supports + 1)
                else
                    cur.copy(sabotages = cur.sabotages + 1))
            }
        }
    }

    val notSubmitted = members.filter { it.deviceId !in submittedIds }
    val sorted = members.sortedByDescending {
        (tally[it.deviceId]?.supports ?: 0) - (tally[it.deviceId]?.sabotages ?: 0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Machinations Overview", style = MaterialTheme.typography.labelMedium)

        if (notSubmitted.isNotEmpty()) {
            Text(
                "Awaiting: ${notSubmitted.joinToString { it.username }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("", modifier = Modifier.weight(1f))
            Text("✓",  modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("+",  modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            Text("−",  modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            Text("Net", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }

        HorizontalDivider(thickness = 0.5.dp)

        for (member in sorted) {
            val t = tally.getOrDefault(member.deviceId, Tally())
            val net = t.supports - t.sabotages
            val netColor = when {
                net > 0 -> Color(0xFF4CAF50)
                net < 0 -> MaterialTheme.colorScheme.error
                else    -> MaterialTheme.colorScheme.onSurface
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.username,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    if (member.deviceId in submittedIds) {
                        Icon(Icons.Default.CheckCircle, null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50))
                    }
                }
                Text("${t.supports}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                Text("${t.sabotages}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                Text(
                    if (net >= 0) "+$net" else "$net",
                    modifier = Modifier.width(36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = netColor,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** Admin panel card: host submits SUPPORT/SABOTAGE machinations for a local player. */
@Composable
private fun AdminLocalMachinationCard(
    member: OnlineCampaignMember,
    campaignId: String,
    otherMembers: List<OnlineCampaignMember>,
    submitted: OnlineMachinationEntry?,
    has3rdSlot: Boolean = false,
    isSubmitting: Boolean,
    onEvent: (CharacterEvent) -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    var choice1 by remember(submitted) { mutableStateOf(submitted?.choices?.getOrNull(0)) }
    var choice2 by remember(submitted) { mutableStateOf(submitted?.choices?.getOrNull(1)) }
    var choice3 by remember(submitted) { mutableStateOf(submitted?.choices?.getOrNull(2)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (submitted != null)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (submitted != null) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Text(member.username,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            // Machination 1
            val row1Members = otherMembers.filter { it.deviceId != member.deviceId }
            Text("Machination 1", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FilterChip(selected = choice1 == null, onClick = { choice1 = null; choice2 = null },
                    label = { Text("None", style = MaterialTheme.typography.labelSmall) })
                row1Members.forEach { m ->
                    FilterChip(
                        selected = choice1?.targetDeviceId == m.deviceId,
                        onClick = { choice1 = OnlineMachinationChoice(m.deviceId, choice1?.type ?: "SUPPORT") },
                        label = { Text(m.username, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (choice1 != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = choice1?.type == "SUPPORT",
                        onClick = { choice1 = choice1?.copy(type = "SUPPORT") },
                        leadingIcon = if (choice1?.type == "SUPPORT") {
                            { Icon(Icons.Default.Favorite, null, Modifier.size(14.dp)) }
                        } else null,
                        label = { Text("Support", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = choice1?.type == "SABOTAGE",
                        onClick = { choice1 = choice1?.copy(type = "SABOTAGE") },
                        leadingIcon = if (choice1?.type == "SABOTAGE") {
                            { Icon(Icons.Default.Dangerous, null, Modifier.size(14.dp)) }
                        } else null,
                        label = { Text("Sabotage", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                // Machination 2
                HorizontalDivider()
                val row2Targets = otherMembers.filter {
                    it.deviceId != member.deviceId &&
                    (choice2 == null || choice1?.type != choice2?.type || it.deviceId != choice1?.targetDeviceId)
                }
                Text("Machination 2", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterChip(selected = choice2 == null, onClick = { choice2 = null },
                        label = { Text("None", style = MaterialTheme.typography.labelSmall) })
                    row2Targets.forEach { m ->
                        FilterChip(
                            selected = choice2?.targetDeviceId == m.deviceId,
                            onClick = { choice2 = OnlineMachinationChoice(m.deviceId, choice2?.type ?: "SUPPORT") },
                            label = { Text(m.username, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                if (choice2 != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = choice2?.type == "SUPPORT",
                            onClick = { choice2 = choice2?.copy(type = "SUPPORT") },
                            leadingIcon = if (choice2?.type == "SUPPORT") {
                                { Icon(Icons.Default.Favorite, null, Modifier.size(14.dp)) }
                            } else null,
                            label = { Text("Support", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = choice2?.type == "SABOTAGE",
                            onClick = { choice2 = choice2?.copy(type = "SABOTAGE") },
                            leadingIcon = if (choice2?.type == "SABOTAGE") {
                                { Icon(Icons.Default.Dangerous, null, Modifier.size(14.dp)) }
                            } else null,
                            label = { Text("Sabotage", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    // Machination 3 — only for bye/bonus-MP players
                    if (has3rdSlot) {
                        HorizontalDivider()
                        val row3Targets = otherMembers.filter {
                            choice3 == null ||
                            choice3?.type != choice1?.type || it.deviceId != choice1?.targetDeviceId ||
                            choice3?.type != choice2?.type || it.deviceId != choice2?.targetDeviceId
                        }
                        Text("Machination 3", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            FilterChip(selected = choice3 == null, onClick = { choice3 = null },
                                label = { Text("None", style = MaterialTheme.typography.labelSmall) })
                            row3Targets.forEach { m ->
                                FilterChip(
                                    selected = choice3?.targetDeviceId == m.deviceId,
                                    onClick = { choice3 = OnlineMachinationChoice(m.deviceId, choice3?.type ?: "SUPPORT") },
                                    label = { Text(m.username, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        if (choice3 != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = choice3?.type == "SUPPORT",
                                    onClick = { choice3 = choice3?.copy(type = "SUPPORT") },
                                    leadingIcon = if (choice3?.type == "SUPPORT") {
                                        { Icon(Icons.Default.Favorite, null, Modifier.size(14.dp)) }
                                    } else null,
                                    label = { Text("Support", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = choice3?.type == "SABOTAGE",
                                    onClick = { choice3 = choice3?.copy(type = "SABOTAGE") },
                                    leadingIcon = if (choice3?.type == "SABOTAGE") {
                                        { Icon(Icons.Default.Dangerous, null, Modifier.size(14.dp)) }
                                    } else null,
                                    label = { Text("Sabotage", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onEvent(CharacterEvent.SubmitLocalMachination(
                        campaignId, member.deviceId, listOfNotNull(choice1, choice2, if (has3rdSlot) choice3 else null)))
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(if (submitted != null) Icons.Default.Refresh else Icons.Default.Send,
                        null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (submitted != null) "Update" else "Submit Machinations",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LockUnlockSection(
    campaign: OnlineCampaignDetail,
    isLoading: Boolean,
    pendingCount: Int,
    onLock: () -> Unit,
    onUnlock: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    val isLocked = campaign.status == "LOCKED"
    Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = if (isLocked) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null,
                    tint = if (isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(if (isLocked) "Campaign Locked" else "Open to new members",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (isLocked) "Unlock to allow more members to join (new join code)."
                        else if (pendingCount > 0) "$pendingCount pending request(s). Lock when everyone has joined."
                        else "Lock when everyone has joined.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = if (isLocked) onUnlock else onLock, enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = if (isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (isLocked) "Unlock Campaign" else "Lock Campaign")
            }
        }
    }
}

@Composable
private fun WaitingCard(text: String) {
    val theme = LocalAppThemeProperties.current
    Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.outline)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CampaignInfoCard(campaign: OnlineCampaignDetail) {
    val theme = LocalAppThemeProperties.current
    Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!campaign.description.isNullOrBlank()) {
                Text(campaign.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${campaign.members.size} members", style = MaterialTheme.typography.bodySmall)
                Text(if (campaign.currentUserRole == "HOST") "You are the Chamberlain" else "Member",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (campaign.settings != null) {
                Spacer(Modifier.height(8.dp))
                val s = campaign.settings
                Text(buildString {
                    append("Starting: ${s.startingCharacters} chars")
                    append(" · Char every ${s.characterGrowthEvery}r · Upgrade every ${s.upgradeGrowthEvery}r")
                    if (!s.attacksEnabled) append(" · Attacks off")
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (campaign.currentUserRole == "HOST" && campaign.joinCode != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Join code: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(campaign.joinCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DeleteCampaignDialog(
    campaignName: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val confirmed = typed.trim().equals(campaignName, ignoreCase = false)

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete Campaign") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This will permanently delete \"$campaignName\" and all its members, results, and history. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Type the campaign name to confirm:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(campaignName, color = MaterialTheme.colorScheme.outline) },
                    singleLine = true,
                    isError = typed.isNotEmpty() && !confirmed,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDeleting
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmed && !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        }
    )
}

@Composable
private fun PendingMemberCard(
    member: OnlineCampaignMember,
    campaignId: String,
    isProcessing: Boolean,
    onEvent: (CharacterEvent) -> Unit
) {
    val theme = LocalAppThemeProperties.current
    Card(modifier = Modifier.fillMaxWidth(), shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(member.username, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEvent(CharacterEvent.ApproveMember(campaignId, member.id)) }, enabled = !isProcessing) {
                Icon(Icons.Default.Check, "Approve", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onEvent(CharacterEvent.RejectMember(campaignId, member.id)) }, enabled = !isProcessing) {
                Icon(Icons.Default.Close, "Reject", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
