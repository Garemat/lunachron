package io.github.garemat.lunachron.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun CampaignDetailsScreen(
    campaignId: Int,
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (Int) -> Unit
) {
    val campaign = state.campaigns.find { it.id == campaignId } ?: return
    val theme = LocalAppThemeProperties.current

    // Restore machination draft after app restart
    LaunchedEffect(campaign.id) {
        if (campaign.machinationPhaseActive && !viewModel.isCampaignMachinating) {
            viewModel.currentCampaignSubScreen = CampaignSubScreen.GAMES
            viewModel.loadMachinationDraft(campaign)
        }
    }

    BackHandler(enabled = viewModel.currentCampaignSubScreen != null) {
        viewModel.currentCampaignSubScreen = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (viewModel.currentCampaignSubScreen == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(theme.screenPadding),
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
            ) {
                SetupOptionCard(
                    title = "Rankings",
                    description = "View current player standings",
                    icon = Icons.Default.EmojiEvents,
                    onClick = { viewModel.currentCampaignSubScreen = CampaignSubScreen.RANKINGS }
                )

                val isCampaignComplete = campaign.totalRounds > 0 && campaign.currentRound > campaign.totalRounds
                val currentRound = campaign.rounds.find { it.roundNumber == campaign.currentRound }
                val nextRoundHeader = campaign.rounds.find { it.roundNumber == campaign.currentRound + 1 }
                val allGamesPlayed = currentRound?.games?.isNotEmpty() == true && currentRound.games.all { it.isPlayed }
                val nextRoundMachinationsDoneHeader = nextRoundHeader?.machinations?.isNotEmpty() == true
                val inEndPhase = campaign.machinationPhaseActive || (allGamesPlayed && !nextRoundMachinationsDoneHeader)
                val gamesCardTitle = when {
                    isCampaignComplete -> "Campaign Complete"
                    inEndPhase -> "Round End Phase"
                    else -> "Record Results"
                }
                val gamesCardDescription = when {
                    isCampaignComplete -> "All ${campaign.totalRounds} rounds finished"
                    inEndPhase -> "MP distribution & machinations for Round ${campaign.currentRound + 1}"
                    else -> "Round ${campaign.currentRound} of ${campaign.totalRounds}"
                }
                SetupOptionCard(
                    title = gamesCardTitle,
                    description = gamesCardDescription,
                    icon = if (inEndPhase) Icons.Default.Favorite else Icons.Default.PlayArrow,
                    onClick = { if (!isCampaignComplete) viewModel.currentCampaignSubScreen = CampaignSubScreen.GAMES }
                )

                SetupOptionCard(
                    title = "Schedule",
                    description = "Full campaign schedule — rounds 1 to ${campaign.totalRounds}",
                    icon = Icons.Default.CalendarMonth,
                    onClick = { viewModel.currentCampaignSubScreen = CampaignSubScreen.SCHEDULE }
                )

                SetupOptionCard(
                    title = "Edit",
                    description = "Update campaign details and players",
                    icon = Icons.Default.Edit,
                    onClick = {
                        viewModel.editCampaign(campaignId)
                        onNavigateToSettings(campaignId)
                    }
                )

                SetupOptionCard(
                    title = "History",
                    description = "Completed rounds with full detail",
                    icon = Icons.Default.History,
                    onClick = { viewModel.currentCampaignSubScreen = CampaignSubScreen.HISTORY }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                when (viewModel.currentCampaignSubScreen) {
                    CampaignSubScreen.RANKINGS -> RankingsTab(campaign, state, theme)
                    CampaignSubScreen.GAMES -> GamesTab(campaign, state, viewModel, theme)
                    CampaignSubScreen.SCHEDULE -> ScheduleTab(campaign, theme)
                    CampaignSubScreen.HISTORY -> HistoryTab(campaign, state, theme)
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun RankingsTab(campaign: Campaign, state: CharacterState, theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties) {
    // power = VP + MP, used for tier calculation and sort order
    data class PlayerEntry(val player: CampaignPlayer, val vp: Int, val power: Int)

    val players = campaign.players.map { cp ->
        val vp = state.troupes.find { it.id == cp.troupeId }?.victoryPoints ?: 0
        PlayerEntry(cp, vp, vp + cp.machinationPoints)
    }.sortedByDescending { it.power }

    if (players.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No players in this campaign", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val n = players.size
    // tierSize = n/3 rounded down, minimum 1
    val tierSize = (n / 3).coerceAtLeast(1)

    // Top tier: the top tierSize players, expanded to include any ties at the boundary
    val topBoundaryPower = players[tierSize - 1].power
    val actualTopCount = players.count { it.power >= topBoundaryPower }

    // Bottom tier: from remaining players, the bottom tierSize — but ties at the
    // bottom boundary go to the higher tier (middle), so strictly exclude tied players
    val remaining = players.drop(actualTopCount)
    val bottomIdx = (remaining.size - tierSize).coerceAtLeast(0)
    val bottomBoundaryPower = remaining.getOrNull(bottomIdx)?.power ?: Int.MIN_VALUE
    val playerAboveBottomPower = remaining.getOrNull(bottomIdx - 1)?.power
    val tiedAtBottom = playerAboveBottomPower == bottomBoundaryPower

    fun getTier(power: Int) = when {
        power >= topBoundaryPower -> "Top"
        tiedAtBottom && power == bottomBoundaryPower -> "Middle"
        power <= bottomBoundaryPower -> "Bottom"
        else -> "Middle"
    }

    val topPlayers = players.filter { getTier(it.power) == "Top" }
    val middlePlayers = players.filter { getTier(it.power) == "Middle" }
    val bottomPlayers = players.filter { getTier(it.power) == "Bottom" }
    val positionOf = players.mapIndexed { i, e -> e.player.id to i + 1 }.toMap()

    LazyColumn(contentPadding = PaddingValues(theme.screenPadding), verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(52.dp))
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                listOf("W" to theme.positiveColor, "D" to MaterialTheme.colorScheme.onSurfaceVariant, "L" to MaterialTheme.colorScheme.error).forEach { (label, color) ->
                    Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("PP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (topPlayers.isNotEmpty()) {
            item { TierSectionHeader("Top Tier", theme.rankingGoldColor, Icons.Default.KeyboardArrowUp) }
            itemsIndexed(topPlayers) { idx, entry ->
                RankingPlayerCard(entry.player, entry.vp, positionOf[entry.player.id] ?: 0, theme.rankingGoldColor, theme.rankingGoldColor, idx, campaign, theme)
            }
        }
        if (middlePlayers.isNotEmpty()) {
            item { TierSectionHeader("Middle Tier", theme.rankingSilverColor, Icons.Default.Remove) }
            itemsIndexed(middlePlayers) { idx, entry ->
                RankingPlayerCard(entry.player, entry.vp, positionOf[entry.player.id] ?: 0, MaterialTheme.colorScheme.onSurfaceVariant, theme.rankingSilverColor, idx, campaign, theme)
            }
        }
        if (bottomPlayers.isNotEmpty()) {
            item { TierSectionHeader("Bottom Tier", theme.rankingBronzeColor, Icons.Default.KeyboardArrowDown) }
            itemsIndexed(bottomPlayers) { idx, entry ->
                RankingPlayerCard(entry.player, entry.vp, positionOf[entry.player.id] ?: 0, theme.rankingBronzeColor, theme.rankingBronzeColor, idx, campaign, theme)
            }
        }
    }
}

@Composable
private fun TierSectionHeader(label: String, color: Color, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun RankingPlayerCard(
    player: CampaignPlayer,
    vp: Int,
    position: Int,
    rankColor: Color,
    tierColor: Color,
    rowIndex: Int,
    campaign: Campaign,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    val bgAlpha = if (rowIndex % 2 == 0) 0.07f else 0.13f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = tierColor.copy(alpha = bgAlpha))
    ) {
        val allGames = campaign.rounds.flatMap { it.games }.filter { player.id in it.playerIds && it.isPlayed }
        val wins = allGames.count { it.winnerId == player.id }
        val draws = allGames.count { it.winnerId == null }
        val losses = allGames.count { it.winnerId != null && it.winnerId != player.id }
        val pp = vp + player.machinationPoints

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (position == 1) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = "1st", tint = rankColor, modifier = Modifier.size(30.dp))
                } else {
                    Text("#$position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = rankColor)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    player.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${player.machinationPoints} MP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$vp VP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                Text("$wins", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = theme.positiveColor)
            }
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                Text("$draws", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                Text("$losses", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
                Text("$pp", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesTab(
    campaign: Campaign,
    state: CharacterState,
    viewModel: CharacterViewModel,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    val currentRound = campaign.rounds.find { it.roundNumber == campaign.currentRound }
    val nextRound = campaign.rounds.find { it.roundNumber == campaign.currentRound + 1 }
    val games = currentRound?.games ?: emptyList()
    val skipPlayers = currentRound?.skipPlayerIds ?: emptyList()
    val isMachinating = viewModel.isCampaignMachinating

    // Machinations for THIS round (submitted at end of previous round) — drives MP distribution
    val hasMachinationsThisRound = currentRound?.machinations?.isNotEmpty() == true
    // Machinations for NEXT round (submitted at end of this round) — drives card draw summary
    val nextRoundMachinationsDone = nextRound?.machinations?.isNotEmpty() == true

    // Machinations for this round are stored in currentRound and evaluated with currentRound results
    fun saveMachinationsAndAttacks(machinations: List<CampaignMachination>, attacks: List<CampaignAttack>) {
        val newRounds = campaign.rounds.toMutableList()
        val nextRoundNumber = campaign.currentRound + 1
        val nextIdx = newRounds.indexOfFirst { it.roundNumber == nextRoundNumber }
        val existing = newRounds.getOrNull(nextIdx)
        val updatedRound = (existing ?: CampaignRound(nextRoundNumber, emptyList())).copy(
            machinations = machinations, attacks = attacks
        )
        if (nextIdx != -1) newRounds[nextIdx] = updatedRound else newRounds.add(updatedRound)
        val apCosts = attacks.groupBy { it.sourcePlayerId }.mapValues { (_, atks) -> atks.sumOf { it.type.cost } }
        val newPlayers = campaign.players.map { p ->
            val cost = apCosts[p.id] ?: 0
            if (cost > 0) p.copy(attackPoints = (p.attackPoints - cost).coerceAtLeast(0)) else p
        }
        viewModel.updateCampaign(campaign.copy(
            rounds = newRounds,
            players = newPlayers,
            machinationPhaseActive = false,
            machinationDraft = null
        ))
    }

    val allGamesPlayed = games.isNotEmpty() && games.all { it.isPlayed }
    val isLastRound = campaign.totalRounds > 0 && campaign.currentRound >= campaign.totalRounds

    // flowStep controls which sub-screen is shown in the end-of-round flow:
    // 0 = record results list
    // 1 = MP distribution (only when hasMachinationsThisRound)
    // 2 = round rankings (only when hasMachinationsThisRound)
    // 3 = card draw summary
    var flowStep by remember(campaign.currentRound) { mutableStateOf(0) }
    var editingGameIndices by remember(campaign.currentRound) { mutableStateOf(emptySet<Int>()) }

    // Pre-compute MP deltas from this round's machinations + this round's results.
    // These are shown in MP distribution and rankings before machinations for the next round.
    val pendingMpDeltas = remember(hasMachinationsThisRound, campaign.currentRound) {
        if (hasMachinationsThisRound) viewModel.computePendingMpDeltas(campaign) else emptyMap()
    }

    if (isMachinating) {
        MachinationPhasePanel(
            campaign = campaign,
            state = state,
            currentRound = currentRound,
            nextRound = nextRound,
            viewModel = viewModel,
            theme = theme,
            onConfirm = { machinations, attacks ->
                saveMachinationsAndAttacks(machinations, attacks)
                viewModel.clearMachinationDraft()
                flowStep = 3
            },
            onSaveDraft = {
                viewModel.saveMachinationDraft(campaign)
                viewModel.currentCampaignSubScreen = null
            }
        )
    } else if (allGamesPlayed && hasMachinationsThisRound && flowStep == 1) {
        MPDistributionScreen(
            campaign = campaign,
            pendingMpDeltas = pendingMpDeltas,
            savedAdjustments = currentRound?.manualMpAdjustments ?: emptyMap(),
            theme = theme,
            onBack = { flowStep = 0 },
            onConfirm = { adjustments ->
                viewModel.saveManualMpAdjustments(campaign, adjustments)
                flowStep = 2
            }
        )
    } else if (allGamesPlayed && hasMachinationsThisRound && flowStep == 2) {
        RoundRankingsScreen(
            campaign = campaign,
            state = state,
            pendingMpDeltas = pendingMpDeltas,
            manualAdjustments = currentRound?.manualMpAdjustments ?: emptyMap(),
            theme = theme,
            onBack = { flowStep = 1 },
            onContinue = {
                viewModel.initMachinationDraft(campaign)
                viewModel.isCampaignMachinating = true
            }
        )
    } else if (allGamesPlayed && nextRoundMachinationsDone && flowStep == 3) {
        CampaignCardDrawScreen(
            campaign = campaign,
            nextRound = nextRound,
            isLastRound = isLastRound,
            theme = theme,
            onBack = { flowStep = 0 },
            onProgress = { viewModel.progressCampaignRound(campaign) }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing), modifier = Modifier.weight(1f)) {
                itemsIndexed(games) { idx, game ->
                    val isEditing = !game.isPlayed || idx in editingGameIndices
                    GameMatchCard(
                        game = game,
                        campaign = campaign,
                        state = state,
                        isEditing = isEditing,
                        theme = theme,
                        onRecordResultWithVP = { vpMap ->
                            viewModel.recordCampaignGameResultWithVP(campaign, game, vpMap)
                            editingGameIndices = editingGameIndices - idx
                        },
                        onEditClick = { editingGameIndices = editingGameIndices + idx },
                        onDrop = { droppedId, joinThreePlayer ->
                            val vpMap = buildMap<String, Int> {
                                put(droppedId, 0)
                                game.playerIds.filter { it != droppedId }.forEach { rid ->
                                    put(rid, if (joinThreePlayer) 1 else 6)
                                }
                            }
                            val dropped = if (joinThreePlayer) listOf(droppedId) else game.playerIds
                            viewModel.recordCampaignGameResultWithVP(campaign, game, vpMap, dropped)
                            editingGameIndices = editingGameIndices - idx
                        }
                    )
                }

                if (skipPlayers.isNotEmpty()) {
                    item {
                        Text("Bye this round:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = theme.cardShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                skipPlayers.mapNotNull { pid -> campaign.players.find { it.id == pid }?.name }.joinToString(", "),
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (allGamesPlayed) {
                when {
                    // Round has prior machinations to review → start with MP distribution
                    hasMachinationsThisRound && !nextRoundMachinationsDone -> Button(
                        onClick = { flowStep = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Round Summary")
                    }
                    // Round 1 (no prior machinations) → go straight to machinations
                    !hasMachinationsThisRound && !nextRoundMachinationsDone -> Button(
                        onClick = {
                            viewModel.initMachinationDraft(campaign)
                            viewModel.isCampaignMachinating = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Proceed to Machinations")
                    }
                    // Machinations for next round already submitted → re-enter end-of-round flow
                    nextRoundMachinationsDone -> OutlinedButton(
                        onClick = { flowStep = if (hasMachinationsThisRound) 1 else 3 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasMachinationsThisRound) "MP Distribution & Card Draw" else "View Card Draw Summary")
                    }
                }
            }
        }
    }
}

@Composable
private fun MPDistributionScreen(
    campaign: Campaign,
    pendingMpDeltas: Map<String, Int>,
    savedAdjustments: Map<String, Int>,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onBack: () -> Unit,
    onConfirm: (Map<String, Int>) -> Unit
) {
    val adjustments = remember(savedAdjustments) {
        mutableStateMapOf<String, Int>().also { map ->
            campaign.players.forEach { p -> map[p.id] = savedAdjustments[p.id] ?: 0 }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back to results", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("MP Distribution", style = theme.headerStyle, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Review machination points and apply any campaign card bonuses or penalties",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
        ) {
            items(campaign.players) { player ->
                val earned = pendingMpDeltas[player.id] ?: 0
                val adj = adjustments[player.id] ?: 0
                val total = earned + adj
                ThemedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(theme.cardContentPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    player.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val earnedSign = if (earned >= 0) "+" else ""
                                Text(
                                    "From machinations: $earnedSign$earned MP",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Total PP display
                            val totalSign = if (total >= 0) "+" else ""
                            Text(
                                "$totalSign$total MP",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    total > 0 -> theme.positiveColor
                                    total < 0 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Campaign card adjustment:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { adjustments[player.id] = adj - 1 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    "${if (adj >= 0) "+" else ""}$adj",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.widthIn(min = 40.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { adjustments[player.id] = adj + 1 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))
        Button(
            onClick = { onConfirm(adjustments.toMap()) },
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape
        ) {
            Text("Confirm & View Rankings")
        }
    }
}

@Composable
private fun RoundRankingsScreen(
    campaign: Campaign,
    state: CharacterState,
    pendingMpDeltas: Map<String, Int>,
    manualAdjustments: Map<String, Int>,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    data class RankedPlayer(
        val player: CampaignPlayer,
        val vp: Int,
        val oldPp: Int,
        val newPp: Int,
        val oldRank: Int,
        val newRank: Int
    )

    val ranked = remember(campaign.players, pendingMpDeltas, manualAdjustments) {
        val troupes = state.troupes
        val entries = campaign.players.map { p ->
            val vp = troupes.find { it.id == p.troupeId }?.victoryPoints ?: 0
            val oldPp = vp + p.machinationPoints
            val totalMpGain = (pendingMpDeltas[p.id] ?: 0) + (manualAdjustments[p.id] ?: 0)
            val newPp = oldPp + totalMpGain
            Triple(p, vp to oldPp, newPp)
        }
        val oldOrder = entries.sortedByDescending { it.second.second }
        val newOrder = entries.sortedByDescending { it.third }
        entries.map { (p, vpOld, newPp) ->
            val (vp, oldPp) = vpOld
            val oldRank = oldOrder.indexOfFirst { it.first.id == p.id } + 1
            val newRank = newOrder.indexOfFirst { it.first.id == p.id } + 1
            RankedPlayer(p, vp, oldPp, newPp, oldRank, newRank)
        }.sortedBy { it.newRank }
    }

    // Tier boundaries based on new PP
    val n = ranked.size
    val tierSize = (n / 3).coerceAtLeast(1)
    val sortedByNew = ranked.sortedByDescending { it.newPp }
    val topBoundary = sortedByNew.getOrNull(tierSize - 1)?.newPp ?: 0
    val actualTop = sortedByNew.count { it.newPp >= topBoundary }
    val remaining = sortedByNew.drop(actualTop)
    val botIdx = (remaining.size - tierSize).coerceAtLeast(0)
    val botBoundary = remaining.getOrNull(botIdx)?.newPp ?: Int.MIN_VALUE
    val tiedAtBot = remaining.getOrNull(botIdx - 1)?.newPp == botBoundary

    fun newTier(pp: Int) = when {
        pp >= topBoundary -> "Top"
        tiedAtBot && pp == botBoundary -> "Middle"
        pp <= botBoundary -> "Bottom"
        else -> "Middle"
    }

    Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back to MP distribution", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Round Rankings", style = theme.headerStyle, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Standings after Round ${campaign.currentRound} machinations",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
        ) {
            itemsIndexed(ranked) { idx, entry ->
                val tierColor = when (newTier(entry.newPp)) {
                    "Top" -> theme.rankingGoldColor
                    "Bottom" -> theme.rankingBronzeColor
                    else -> theme.rankingSilverColor
                }
                val rankDelta = entry.oldRank - entry.newRank  // positive = moved up
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = tierColor.copy(alpha = if (idx % 2 == 0) 0.07f else 0.13f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // New rank
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            if (entry.newRank == 1) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = "1st", tint = tierColor, modifier = Modifier.size(30.dp))
                            } else {
                                Text("#${entry.newRank}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = tierColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Position change indicator
                        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            when {
                                rankDelta > 0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up $rankDelta", tint = theme.positiveColor, modifier = Modifier.size(16.dp))
                                    Text("$rankDelta", style = MaterialTheme.typography.labelSmall, color = theme.positiveColor, fontWeight = FontWeight.Bold)
                                }
                                rankDelta < 0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${-rankDelta}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down ${-rankDelta}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                                else -> Icon(Icons.Default.Remove, contentDescription = "No change", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.player.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val mpGain = (pendingMpDeltas[entry.player.id] ?: 0) + (manualAdjustments[entry.player.id] ?: 0)
                            val sign = if (mpGain >= 0) "+" else ""
                            Text(
                                "${entry.vp} VP · ${entry.player.machinationPoints} MP (${sign}${mpGain})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${entry.newPp}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape
        ) {
            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Proceed to Machinations")
        }
    }
}

@Composable
private fun CampaignCardDrawScreen(
    campaign: Campaign,
    nextRound: CampaignRound?,
    isLastRound: Boolean,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onBack: () -> Unit,
    onProgress: () -> Unit
) {
    val machinations = nextRound?.machinations ?: emptyList()
    val playerRows = campaign.players.chunked(2)

    Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back to results", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Round ${campaign.currentRound} Complete",
            style = theme.headerStyle,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Campaign cards available in Round ${campaign.currentRound + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)) {
            items(playerRows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardDrawPlayerCard(
                        player = row[0],
                        machinations = machinations,
                        allPlayers = campaign.players,
                        isLeft = true,
                        theme = theme,
                        modifier = Modifier.weight(1f)
                    )
                    val rightPlayer = row.getOrNull(1)
                    if (rightPlayer != null) {
                        CardDrawPlayerCard(
                            player = rightPlayer,
                            machinations = machinations,
                            allPlayers = campaign.players,
                            isLeft = false,
                            theme = theme,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))
        Text(
            "Screenshot this screen to share card draws, then proceed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(theme.verticalSpacing))
        Button(
            onClick = onProgress,
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape
        ) {
            Text(if (isLastRound) "Complete Campaign" else "Progress to Round ${campaign.currentRound + 1}")
        }
    }
}

@Composable
private fun CardDrawPlayerCard(
    player: CampaignPlayer,
    machinations: List<CampaignMachination>,
    allPlayers: List<CampaignPlayer>,
    isLeft: Boolean,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    modifier: Modifier = Modifier
) {
    val supports = machinations.filter { it.targetPlayerId == player.id && it.type == MachinationType.SUPPORT }
    val sabotages = machinations.filter { it.targetPlayerId == player.id && it.type == MachinationType.SABOTAGE }
    val rawCards = 2 + supports.size - sabotages.size
    val cardDraw = rawCards.coerceIn(1, 3)
    val bonusMp = (rawCards - 3).coerceAtLeast(0)

    ThemedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header: card count badge in the appropriate corner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLeft) {
                    CardCountBadge(cardDraw)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CardCountBadge(cardDraw)
                }
            }

            // Support received
            supports.forEach { mach ->
                val sourceName = allPlayers.find { it.id == mach.sourcePlayerId }?.name ?: mach.sourcePlayerId
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(sourceName, style = MaterialTheme.typography.bodySmall)
                }
            }


            // Bonus MP from support over the card cap
            if (bonusMp > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = theme.moonstoneColor)
                    Text(
                        "+$bonusMp bonus MP",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.moonstoneColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CardCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMatchCard(
    game: CampaignGame,
    campaign: Campaign,
    state: CharacterState,
    isEditing: Boolean,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onRecordResultWithVP: (Map<String, Int>) -> Unit,
    onEditClick: () -> Unit,
    onDrop: (droppedPlayerId: String, joinThreePlayer: Boolean) -> Unit
) {
    val players = game.playerIds.mapNotNull { pid -> campaign.players.find { it.id == pid } }
    val troupes = players.map { p -> state.troupes.find { it.id == p.troupeId } }

    // VP entry state — initialised from any previously recorded values
    val vpState = remember(game.playerIds, game.isPlayed) {
        mutableStateMapOf<String, Int>().also { map ->
            players.forEach { p -> map[p.id] = game.playerVictoryPoints[p.id] ?: 0 }
        }
    }

    // Build PlayerStat list so we can reuse QuadrantLayout + ScoreCircle from StatsScreen
    val playerStats = players.mapIndexed { i, player ->
        val troupe = troupes.getOrNull(i)
        PlayerStat(
            playerName = player.name,
            troupeName = troupe?.troupeName ?: "",
            faction = troupe?.faction ?: Faction.COMMONWEALTH,
            totalStones = game.playerVictoryPoints[player.id] ?: 0,
            characterStats = emptyList()
        )
    }
    val winnerIndex = if (game.isPlayed && game.winnerId != null)
        players.indexOfFirst { it.id == game.winnerId }.takeIf { it != -1 }
    else null

    // Drop bottom sheet state
    var showDropSheet by remember { mutableStateOf(false) }
    var dropSelectedPlayer by remember { mutableStateOf<String?>(null) }
    val dropSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Stats-style banner card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation)
        ) {
            Box(modifier = Modifier.height(160.dp).fillMaxWidth()) {
                // Faction background quadrants — reuses PlayerQuadrant/QuadrantLayout from StatsScreen
                QuadrantLayout(playerStats, winnerIndex)

                // Dim loser halves when result is recorded and there's a winner
                if (!isEditing && winnerIndex != null) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        players.forEachIndexed { i, _ ->
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                                    .then(
                                        if (i != winnerIndex)
                                            Modifier.background(Color.Black.copy(alpha = 0.4f))
                                        else Modifier
                                    )
                            )
                        }
                    }
                }

                // Center: large VP steppers when editing, ScoreCircle when recorded
                if (isEditing) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        players.forEachIndexed { i, player ->
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                val vp = vpState[player.id] ?: 0
                                CampaignVPStepper(
                                    value = vp,
                                    onDecrement = { if (vp > 0) vpState[player.id] = vp - 1 },
                                    onIncrement = { vpState[player.id] = vp + 1 }
                                )
                            }
                        }
                    }
                } else {
                    ScoreCircle(playerStats, Modifier.align(Alignment.Center))
                }

                // Player name + troupe overlay in top corners
                Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    players.getOrNull(0)?.let { p ->
                        CampaignPlayerLabel(
                            playerName = p.name,
                            troupeName = troupes.getOrNull(0)?.troupeName,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.align(Alignment.TopStart)
                                .fillMaxWidth(0.42f)
                                .padding(start = 34.dp)
                        )
                    }
                    players.getOrNull(1)?.let { p ->
                        CampaignPlayerLabel(
                            playerName = p.name,
                            troupeName = troupes.getOrNull(1)?.troupeName,
                            textAlign = TextAlign.End,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .fillMaxWidth(0.42f)
                                .padding(end = 34.dp)
                        )
                    }
                }

                // Campaign card count badges — top-left for left player, top-right for right
                players.getOrNull(0)?.let { p ->
                    CardCountBadge(count = p.campaignCardDraw, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                players.getOrNull(1)?.let { p ->
                    CardCountBadge(count = p.campaignCardDraw, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
        }

        // Action buttons below the banner
        if (isEditing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDropSheet = true },
                    modifier = Modifier.weight(1f),
                    shape = theme.cardShape
                ) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Drop player")
                }
                Button(
                    onClick = { onRecordResultWithVP(vpState.toMap()) },
                    modifier = Modifier.weight(1f),
                    shape = theme.cardShape
                ) {
                    Text("Record Result")
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // Drop bottom sheet
    if (showDropSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDropSheet = false; dropSelectedPlayer = null },
            sheetState = dropSheetState
        ) {
            Column(
                modifier = Modifier
                    .padding(theme.screenPadding)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
            ) {
                if (dropSelectedPlayer == null) {
                    Text("Who was unable to play?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "They automatically lose with 0 VP. Machinations targeting them will not be applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    players.forEach { player ->
                        OutlinedButton(
                            onClick = { dropSelectedPlayer = player.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = theme.cardShape
                        ) {
                            Text(player.name)
                        }
                    }
                    TextButton(onClick = { showDropSheet = false }) {
                        Text("Cancel")
                    }
                } else {
                    val droppedName = campaign.players.find { it.id == dropSelectedPlayer }?.name ?: "?"
                    val remainingNames = players.filter { it.id != dropSelectedPlayer }.map { it.name }.joinToString(", ")
                    Text("$droppedName cannot play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$droppedName loses with 0 VP. What happens to $remainingNames?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            onDrop(dropSelectedPlayer!!, false)
                            showDropSheet = false; dropSelectedPlayer = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Skip game — auto-win (6 VP)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Machinations targeting $remainingNames are not applied",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            onDrop(dropSelectedPlayer!!, true)
                            showDropSheet = false; dropSelectedPlayer = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Join a 3-player game (+1 VP)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "All participants in the new game receive +1 VP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { dropSelectedPlayer = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignPlayerLabel(
    playerName: String,
    troupeName: String?,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    val displayName = if (!troupeName.isNullOrBlank()) "$playerName – $troupeName" else playerName
    Text(
        text = displayName,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun CampaignVPStepper(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrement, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease VP", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            text = "$value",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp,
            modifier = Modifier.widthIn(min = 48.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase VP", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulingPanel(
    campaign: Campaign,
    initialPlayersPerGame: Int,
    initialAssignments: Map<String, Int>,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onConfirm: (List<CampaignGame>, List<String>) -> Unit,
    onCancel: (() -> Unit)?
) {
    var playersPerGame by remember { mutableIntStateOf(initialPlayersPerGame) }
    val assignments = remember { mutableStateMapOf<String, Int>().also { it.putAll(initialAssignments) } }
    var selectedPlayerId by remember { mutableStateOf<String?>(null) }

    val numSkipping by remember { derivedStateOf { assignments.values.count { it == 0 } } }
    val numGames by remember { derivedStateOf { maxOf(1, (campaign.players.size - numSkipping) / playersPerGame) } }
    val unassigned = campaign.players.filter { assignments[it.id] == -1 }
    val allAssigned = assignments.values.all { it != -1 }

    // Return players to unassigned if their game slot no longer exists after numGames shrinks
    LaunchedEffect(numGames) {
        assignments.entries.filter { it.value > numGames }.forEach { (key, _) -> assignments[key] = -1 }
    }

    fun autoPair() {
        assignments.keys.forEach { assignments[it] = -1 }
        val shuffled = campaign.players.shuffled()
        shuffled.take(numGames * playersPerGame).forEachIndexed { idx, p ->
            assignments[p.id] = idx / playersPerGame + 1
        }
        shuffled.drop(numGames * playersPerGame).forEach { p -> assignments[p.id] = 0 }
        selectedPlayerId = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
        // Players-per-game selector + Auto-pair
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Per game:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                listOf(2, 3, 4).forEach { num ->
                    FilterChip(
                        selected = playersPerGame == num,
                        onClick = {
                            playersPerGame = num
                            assignments.keys.forEach { assignments[it] = -1 }
                            selectedPlayerId = null
                        },
                        label = { Text(num.toString()) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
            TextButton(onClick = { autoPair() }) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Auto")
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        // Unassigned pool
        if (unassigned.isNotEmpty()) {
            Text(
                "Unassigned — tap a player, then tap a game slot",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                unassigned.forEach { player ->
                    val isSelected = selectedPlayerId == player.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPlayerId = if (isSelected) null else player.id },
                        label = { Text(player.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(theme.verticalSpacing))
        }

        // Game slots + skip
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
            modifier = Modifier.weight(1f)
        ) {
            items((1..numGames).toList()) { gameIdx ->
                val assignedPlayers = campaign.players.filter { assignments[it.id] == gameIdx }
                val isFull = assignedPlayers.size >= playersPerGame
                val isDropTarget = selectedPlayerId != null && !isFull

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = if (isDropTarget) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    onClick = {
                        val sel = selectedPlayerId
                        if (sel != null && !isFull) {
                            assignments[sel] = gameIdx
                            selectedPlayerId = null
                        }
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Game $gameIdx", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${assignedPlayers.size}/$playersPerGame",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFull) theme.positiveColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (assignedPlayers.isEmpty()) {
                            Text(
                                "Tap a player above then tap here to assign",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                assignedPlayers.forEach { player ->
                                    InputChip(
                                        selected = false,
                                        onClick = { assignments[player.id] = -1; if (selectedPlayerId == player.id) selectedPlayerId = null },
                                        label = { Text(player.name) },
                                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Skip slot
            item {
                val skippingPlayers = campaign.players.filter { assignments[it.id] == 0 }
                val isDropTarget = selectedPlayerId != null
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    border = if (isDropTarget) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null,
                    onClick = {
                        val sel = selectedPlayerId
                        if (sel != null) { assignments[sel] = 0; selectedPlayerId = null }
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Skip Round",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (skippingPlayers.isEmpty()) {
                            Text(
                                "Tap a player above then tap here to mark as skipping",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                skippingPlayers.forEach { player ->
                                    InputChip(
                                        selected = false,
                                        onClick = { assignments[player.id] = -1; if (selectedPlayerId == player.id) selectedPlayerId = null },
                                        label = { Text(player.name) },
                                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = theme.cardShape) {
                    Text("Cancel")
                }
            }
            Button(
                onClick = {
                    val gamesList = (1..numGames).mapNotNull { gIdx ->
                        val pids = assignments.filter { it.value == gIdx }.keys.toList()
                        if (pids.isNotEmpty()) CampaignGame(pids) else null
                    }
                    val skips = assignments.filter { it.value == 0 }.keys.toList()
                    onConfirm(gamesList, skips)
                },
                enabled = allAssigned,
                modifier = if (onCancel != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                shape = theme.cardShape
            ) {
                Text("Confirm Schedule")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MachinationsTab(
    campaign: Campaign,
    state: CharacterState,
    viewModel: CharacterViewModel,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    var isAdding by remember { mutableStateOf(false) }
    val currentRoundMachinations = campaign.rounds.find { it.roundNumber == campaign.currentRound }?.machinations ?: emptyList()

    fun saveMachinations(updated: List<CampaignMachination>) {
        val newRounds = campaign.rounds.toMutableList()
        val roundIdx = newRounds.indexOfFirst { it.roundNumber == campaign.currentRound }
        if (roundIdx != -1) {
            newRounds[roundIdx] = newRounds[roundIdx].copy(machinations = updated)
        } else {
            newRounds.add(CampaignRound(campaign.currentRound, emptyList(), updated))
        }
        viewModel.updateCampaign(campaign.copy(rounds = newRounds))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
            if (isAdding) {
                AddMachinationPanel(
                    campaign = campaign,
                    theme = theme,
                    onConfirm = { sourceId, targetId, type ->
                        saveMachinations(currentRoundMachinations + CampaignMachination(sourceId, targetId, type))
                        isAdding = false
                    },
                    onCancel = { isAdding = false }
                )
            } else if (currentRoundMachinations.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No machinations this round",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                    modifier = Modifier.weight(1f).padding(bottom = 80.dp)
                ) {
                    items(currentRoundMachinations) { mach ->
                        MachinationCard(
                            mach = mach,
                            campaign = campaign,
                            theme = theme,
                            onDelete = { saveMachinations(currentRoundMachinations.filter { it != mach }) }
                        )
                    }
                }
            }
        }

        if (!isAdding) {
            FloatingActionButton(
                onClick = { isAdding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(theme.screenPadding),
                shape = theme.navItemShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add machination")
            }
        }
    }
}

@Composable
private fun MachinationCard(
    mach: CampaignMachination,
    campaign: Campaign,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onDelete: () -> Unit
) {
    val source = campaign.players.find { it.id == mach.sourcePlayerId }
    val target = campaign.players.find { it.id == mach.targetPlayerId }
    val isSupport = mach.type == MachinationType.SUPPORT
    val accentColor = if (isSupport) theme.positiveColor else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isSupport) Icons.Default.Favorite else Icons.Default.Dangerous,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(source?.name ?: "Unknown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (isSupport) "supports" else "sabotages",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
                Text(target?.name ?: "Unknown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove machination",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMachinationPanel(
    campaign: Campaign,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onConfirm: (String, String, MachinationType) -> Unit,
    onCancel: () -> Unit
) {
    var sourceId by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MachinationType.SUPPORT) }

    val canConfirm = sourceId.isNotEmpty() && targetId.isNotEmpty() && sourceId != targetId

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)) {
            Text("New Machination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text("Who are you?", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                campaign.players.forEach { player ->
                    FilterChip(
                        selected = sourceId == player.id,
                        onClick = {
                            sourceId = player.id
                            if (targetId == player.id) targetId = ""
                        },
                        label = { Text(player.name) }
                    )
                }
            }

            Text("Type", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == MachinationType.SUPPORT,
                    onClick = { type = MachinationType.SUPPORT },
                    label = { Text("Support") },
                    leadingIcon = if (type == MachinationType.SUPPORT) {
                        { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = type == MachinationType.SABOTAGE,
                    onClick = { type = MachinationType.SABOTAGE },
                    label = { Text("Sabotage") },
                    leadingIcon = if (type == MachinationType.SABOTAGE) {
                        { Icon(Icons.Default.Dangerous, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Text("Target player", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                campaign.players.filter { it.id != sourceId }.forEach { player ->
                    FilterChip(
                        selected = targetId == player.id,
                        onClick = { targetId = player.id },
                        label = { Text(player.name) }
                    )
                }
                if (sourceId.isEmpty()) {
                    Text(
                        "Select yourself first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = theme.cardShape) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(sourceId, targetId, type) },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                    shape = theme.cardShape
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttacksTab(
    campaign: Campaign,
    state: CharacterState,
    viewModel: CharacterViewModel,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    var isAdding by remember { mutableStateOf(false) }
    val currentRoundAttacks = campaign.rounds.find { it.roundNumber == campaign.currentRound }?.attacks ?: emptyList()

    fun saveAttacks(updated: List<CampaignAttack>) {
        val newRounds = campaign.rounds.toMutableList()
        val roundIdx = newRounds.indexOfFirst { it.roundNumber == campaign.currentRound }
        if (roundIdx != -1) {
            newRounds[roundIdx] = newRounds[roundIdx].copy(attacks = updated)
        } else {
            newRounds.add(CampaignRound(campaign.currentRound, emptyList(), attacks = updated))
        }
        viewModel.updateCampaign(campaign.copy(rounds = newRounds))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
            if (isAdding) {
                AddAttackPanel(
                    campaign = campaign,
                    state = state,
                    theme = theme,
                    onConfirm = { sourceId, targetId, charId, type ->
                        viewModel.recordCampaignAttack(campaign, sourceId, targetId, charId, type)
                        isAdding = false
                    },
                    onCancel = { isAdding = false }
                )
            } else if (currentRoundAttacks.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Gavel,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No attacks this round",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                    modifier = Modifier.weight(1f).padding(bottom = 80.dp)
                ) {
                    items(currentRoundAttacks) { attack ->
                        AttackCard(
                            attack = attack,
                            campaign = campaign,
                            state = state,
                            theme = theme,
                            onDelete = { saveAttacks(currentRoundAttacks.filter { it != attack }) }
                        )
                    }
                }
            }
        }

        if (!isAdding) {
            FloatingActionButton(
                onClick = { isAdding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(theme.screenPadding),
                shape = theme.navItemShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Launch attack")
            }
        }
    }
}

@Composable
private fun AttackCard(
    attack: CampaignAttack,
    campaign: Campaign,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onDelete: () -> Unit
) {
    val source = campaign.players.find { it.id == attack.sourcePlayerId }
    val target = campaign.players.find { it.id == attack.targetPlayerId }
    val character = state.characters.find { it.id == attack.targetCharacterId }
    val isAssault = attack.type == AttackType.ASSAULT

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isAssault) Icons.Default.FlashOn else Icons.Default.Gavel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(source?.name ?: "Unknown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (isAssault) "assaults" else "abducts from",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "${target?.name ?: "Unknown"} — ${character?.name ?: "Unknown"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove attack",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAttackPanel(
    campaign: Campaign,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onConfirm: (String, String, Int, AttackType) -> Unit,
    onCancel: () -> Unit
) {
    var sourceId by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }
    var targetCharacterId by remember { mutableStateOf(-1) }
    var type by remember { mutableStateOf(AttackType.ASSAULT) }

    val sourcePlayer = campaign.players.find { it.id == sourceId }
    val targetPlayer = campaign.players.find { it.id == targetId }
    val targetTroupe = state.troupes.find { it.id == targetPlayer?.troupeId }
    val targetCharacters = targetTroupe?.characterIds?.mapNotNull { id -> state.characters.find { it.id == id } } ?: emptyList()
    val canAfford = (sourcePlayer?.attackPoints ?: 0) >= type.cost
    val canConfirm = sourceId.isNotEmpty() && targetId.isNotEmpty() && targetCharacterId != -1 && canAfford

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)) {
            Text("Launch Attack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text("Who are you?", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                campaign.players.forEach { player ->
                    FilterChip(
                        selected = sourceId == player.id,
                        onClick = {
                            sourceId = player.id
                            if (targetId == player.id) { targetId = ""; targetCharacterId = -1 }
                        },
                        label = { Text(player.name) }
                    )
                }
            }
            if (sourcePlayer != null) {
                Text(
                    "${sourcePlayer.attackPoints} AP available",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sourcePlayer.attackPoints > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Text("Attack type", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == AttackType.ASSAULT,
                    onClick = { type = AttackType.ASSAULT },
                    label = { Text("Assault (1 AP)") },
                    leadingIcon = if (type == AttackType.ASSAULT) {
                        { Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = type == AttackType.ABDUCTION,
                    onClick = { type = AttackType.ABDUCTION },
                    label = { Text("Abduction (2 AP)") },
                    leadingIcon = if (type == AttackType.ABDUCTION) {
                        { Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Text("Target player", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                campaign.players.filter { it.id != sourceId }.forEach { player ->
                    FilterChip(
                        selected = targetId == player.id,
                        onClick = { targetId = player.id; targetCharacterId = -1 },
                        label = { Text(player.name) }
                    )
                }
                if (sourceId.isEmpty()) {
                    Text(
                        "Select yourself first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (targetId.isNotEmpty()) {
                Text("Target character", style = MaterialTheme.typography.labelMedium)
                if (targetCharacters.isEmpty()) {
                    Text(
                        "No troupe assigned to this player",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        targetCharacters.forEach { character ->
                            FilterChip(
                                selected = targetCharacterId == character.id,
                                onClick = { targetCharacterId = character.id },
                                label = { Text(character.name) }
                            )
                        }
                    }
                }
            }

            if (!canAfford && sourceId.isNotEmpty()) {
                Text(
                    "Not enough AP for this attack type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = theme.cardShape) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(sourceId, targetId, targetCharacterId, type) },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                    shape = theme.cardShape
                ) {
                    Text("Launch")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDropdown(characters: List<Character>, selectedId: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedChar = characters.find { it.id == selectedId }
    
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedChar?.name ?: "Select Character",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            characters.forEach { character ->
                DropdownMenuItem(
                    text = { Text(character.name) },
                    onClick = { onSelected(character.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDropdown(players: List<CampaignPlayer>, selectedId: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlayer = players.find { it.id == selectedId }
    
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedPlayer?.name ?: "Select Player",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.name) },
                    onClick = { onSelected(player.id); expanded = false }
                )
            }
        }
    }
}

@Composable
fun ScheduleTab(
    campaign: Campaign,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    val rounds = campaign.rounds.sortedBy { it.roundNumber }

    if (rounds.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No schedule yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // Auto-expand current round
    var expandedRounds by remember { mutableStateOf(setOf(campaign.currentRound)) }

    LazyColumn(contentPadding = PaddingValues(theme.screenPadding), verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)) {
        items(rounds) { round ->
            val isExpanded = round.roundNumber in expandedRounds
            val isCurrent = round.roundNumber == campaign.currentRound
            val isPast = round.roundNumber < campaign.currentRound
            val allPlayed = round.games.isNotEmpty() && round.games.all { it.isPlayed }

            val roundLabel = when {
                isCurrent -> "Round ${round.roundNumber} — Current"
                isPast -> "Round ${round.roundNumber} — Complete"
                else -> "Round ${round.roundNumber}"
            }
            val roundLabelColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else theme.cardBackground
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedRounds = if (isExpanded)
                                    expandedRounds - round.roundNumber
                                else
                                    expandedRounds + round.roundNumber
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(roundLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = roundLabelColor)
                            if (round.games.isNotEmpty()) {
                                Text(
                                    "${round.games.size} game(s)${if (round.skipPlayerIds.isNotEmpty()) " · ${round.skipPlayerIds.size} bye(s)" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (allPlayed) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Complete", tint = theme.positiveColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isExpanded) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            round.games.forEach { game ->
                                val p1 = campaign.players.find { it.id == game.playerIds.getOrNull(0) }
                                val p2 = campaign.players.find { it.id == game.playerIds.getOrNull(1) }
                                val p1Name = game.playerNameOverrides[game.playerIds.getOrNull(0) ?: ""]
                                    ?.let { "$it (sub)" } ?: (p1?.name ?: "?")
                                val p2Name = game.playerNameOverrides[game.playerIds.getOrNull(1) ?: ""]
                                    ?.let { "$it (sub)" } ?: (p2?.name ?: "?")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p1Name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                                    Text(p2Name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    val scoreStr = if (game.playerVictoryPoints.isNotEmpty()) {
                                        val scores = game.playerIds.mapNotNull { pid -> game.playerVictoryPoints[pid] }
                                        " ${scores.joinToString("–")}"
                                    } else ""
                                    val resultText = when {
                                        !game.isPlayed -> "Pending"
                                        game.winnerId == null -> "Draw$scoreStr"
                                        else -> "${campaign.players.find { it.id == game.winnerId }?.name ?: "Win"}$scoreStr"
                                    }
                                    val resultColor = when {
                                        !game.isPlayed -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> theme.positiveColor
                                    }
                                    Text(resultText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = resultColor)
                                }
                            }
                            if (round.skipPlayerIds.isNotEmpty()) {
                                val byeNames = round.skipPlayerIds.mapNotNull { pid -> campaign.players.find { it.id == pid }?.name }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bye: ${byeNames.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(campaign: Campaign, state: CharacterState, theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties) {
    // Only show completed rounds (rounds before the current one)
    val rounds = campaign.rounds.filter { it.roundNumber < campaign.currentRound }.sortedBy { it.roundNumber }

    if (rounds.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No completed rounds yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Auto-expand the most recently completed round (last in chronological order)
    val mostRecentCompleted = rounds.lastOrNull()?.roundNumber
    var expandedRounds by remember { mutableStateOf(if (mostRecentCompleted != null) setOf(mostRecentCompleted) else emptySet()) }

    LazyColumn(contentPadding = PaddingValues(theme.screenPadding), verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)) {
        items(rounds) { round ->
            val isExpanded = round.roundNumber in expandedRounds
            val summaryParts = buildList {
                if (round.games.isNotEmpty()) add("${round.games.size} games")
                if (round.skipPlayerIds.isNotEmpty()) add("${round.skipPlayerIds.size} skipped")
                if (round.machinations.isNotEmpty()) add("${round.machinations.size} machinations")
                if (round.attacks.isNotEmpty()) add("${round.attacks.size} attacks")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = theme.cardShape,
                colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
            ) {
                Column {
                    // Tappable header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedRounds = if (isExpanded)
                                    expandedRounds - round.roundNumber
                                else
                                    expandedRounds + round.roundNumber
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Round ${round.roundNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (summaryParts.isNotEmpty()) {
                                Text(
                                    summaryParts.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isExpanded) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (round.games.isNotEmpty()) {
                                HistorySectionHeader("Games", Icons.Default.PlayArrow)
                                round.games.forEach { game ->
                                    val players = game.playerIds.mapNotNull { pid -> campaign.players.find { it.id == pid } }
                                    val winner = campaign.players.find { it.id == game.winnerId }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            players.joinToString(" vs ") { it.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            if (game.isPlayed) (winner?.name ?: "Draw") else "Pending",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (game.isPlayed) theme.positiveColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (round.skipPlayerIds.isNotEmpty()) {
                                HistorySectionHeader("Skipped", Icons.Default.Block)
                                Text(
                                    round.skipPlayerIds.mapNotNull { pid -> campaign.players.find { it.id == pid }?.name }.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (round.machinations.isNotEmpty()) {
                                HistorySectionHeader("Machinations", Icons.Default.Favorite)
                                round.machinations.forEach { mach ->
                                    val source = campaign.players.find { it.id == mach.sourcePlayerId }?.name ?: "Unknown"
                                    val target = campaign.players.find { it.id == mach.targetPlayerId }?.name ?: "Unknown"
                                    val isSupport = mach.type == MachinationType.SUPPORT
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            if (isSupport) Icons.Default.Favorite else Icons.Default.Dangerous,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (isSupport) theme.positiveColor else MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "$source ${if (isSupport) "supported" else "sabotaged"} $target",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (round.attacks.isNotEmpty()) {
                                HistorySectionHeader("Attacks", Icons.Default.FlashOn)
                                round.attacks.forEach { attack ->
                                    val source = campaign.players.find { it.id == attack.sourcePlayerId }?.name ?: "Unknown"
                                    val target = campaign.players.find { it.id == attack.targetPlayerId }?.name ?: "Unknown"
                                    val character = state.characters.find { it.id == attack.targetCharacterId }?.name ?: "Unknown"
                                    val isAssault = attack.type == AttackType.ASSAULT
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            if (isAssault) Icons.Default.FlashOn else Icons.Default.Gavel,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "$source ${if (isAssault) "assaulted" else "abducted"} $target's $character",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (round.mpDeltas.isNotEmpty()) {
                                HistorySectionHeader("Machination Results", Icons.Default.Stars)
                                round.mpDeltas.entries.sortedByDescending { it.value }.forEach { (playerId, delta) ->
                                    val playerName = campaign.players.find { it.id == playerId }?.name ?: "Unknown"
                                    val sign = if (delta > 0) "+" else ""
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(playerName, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "$sign$delta MP",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (delta > 0) theme.positiveColor else MaterialTheme.colorScheme.error
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
}

@Composable
private fun HistorySectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MachinationPhasePanel(
    campaign: Campaign,
    state: CharacterState,
    currentRound: CampaignRound?,
    nextRound: CampaignRound?,
    viewModel: CharacterViewModel,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties,
    onConfirm: (List<CampaignMachination>, List<CampaignAttack>) -> Unit,
    onSaveDraft: () -> Unit
) {
    // Opponents are determined by the UPCOMING round's schedule, not the round just played
    val upcomingGames = nextRound?.games ?: emptyList()
    fun opponentsOf(playerId: String) = upcomingGames
        .filter { playerId in it.playerIds }
        .flatMap { it.playerIds }
        .filter { it != playerId }
        .toSet()

    val byePlayerIds = nextRound?.skipPlayerIds?.toSet() ?: emptySet()
    val hasBonusPlayers = byePlayerIds.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
        Text("Machinations & Attacks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            buildString {
                append("Each player can use 2 machinations. Targets cannot be scheduled opponents.")
                if (hasBonusPlayers) append(" Bye players get a bonus 3rd slot.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(campaign.players) { index, player ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = theme.verticalSpacing))
                }
                val hasBonus = player.id in byePlayerIds
                val type1 = viewModel.machinationType1[player.id]
                val type2 = viewModel.machinationType2[player.id]
                val type3 = viewModel.machinationType3[player.id]
                val eligibleTargets = campaign.players.filter { it.id != player.id && it.id !in opponentsOf(player.id) }
                PlayerMachinationCard(
                    player = player,
                    hasBonus = hasBonus,
                    type1 = type1,
                    onType1Change = { newType ->
                        viewModel.machinationType1[player.id] = newType
                        if (newType == null) {
                            viewModel.machinationType2[player.id] = null
                            viewModel.machinationType3[player.id] = null
                            viewModel.machinationTarget1.remove(player.id)
                            viewModel.machinationTarget2.remove(player.id)
                            viewModel.machinationTarget3.remove(player.id)
                        }
                    },
                    type2 = type2,
                    onType2Change = { newType ->
                        viewModel.machinationType2[player.id] = newType
                        if (newType == null) {
                            viewModel.machinationType3[player.id] = null
                            viewModel.machinationTarget2.remove(player.id)
                            viewModel.machinationTarget3.remove(player.id)
                        }
                    },
                    type3 = type3,
                    onType3Change = { newType ->
                        viewModel.machinationType3[player.id] = newType
                        if (newType == null) viewModel.machinationTarget3.remove(player.id)
                    },
                    target1 = viewModel.machinationTarget1[player.id] ?: "",
                    onTarget1Change = { viewModel.machinationTarget1[player.id] = it },
                    target2 = viewModel.machinationTarget2[player.id] ?: "",
                    onTarget2Change = { viewModel.machinationTarget2[player.id] = it },
                    target3 = viewModel.machinationTarget3[player.id] ?: "",
                    onTarget3Change = { viewModel.machinationTarget3[player.id] = it },
                    eligibleTargets = eligibleTargets,
                    attacksEnabled = campaign.attacksEnabled,
                    isAttacking = viewModel.machinationIsAttacking[player.id] == true,
                    onIsAttackingChange = {
                        viewModel.machinationIsAttacking[player.id] = it
                        if (!it) { viewModel.machinationAttackTargetPlayer.remove(player.id); viewModel.machinationAttackTargetChar.remove(player.id) }
                    },
                    attackType = viewModel.machinationAttackType[player.id] ?: AttackType.ASSAULT,
                    onAttackTypeChange = { viewModel.machinationAttackType[player.id] = it },
                    attackTargetPlayer = viewModel.machinationAttackTargetPlayer[player.id] ?: "",
                    onAttackTargetPlayerChange = { viewModel.machinationAttackTargetPlayer[player.id] = it; viewModel.machinationAttackTargetChar.remove(player.id) },
                    attackTargetChar = viewModel.machinationAttackTargetChar[player.id] ?: -1,
                    onAttackTargetCharChange = { viewModel.machinationAttackTargetChar[player.id] = it },
                    allPlayers = campaign.players,
                    state = state,
                    theme = theme
                )
            }
        }

        Spacer(modifier = Modifier.height(theme.verticalSpacing))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSaveDraft, modifier = Modifier.weight(1f), shape = theme.cardShape) {
                Text("Save Draft")
            }
            Button(
                onClick = {
                    val machinations = mutableListOf<CampaignMachination>()
                    val attacks = mutableListOf<CampaignAttack>()
                    campaign.players.forEach { player ->
                        val t1 = viewModel.machinationType1[player.id]
                        val t2 = viewModel.machinationType2[player.id]
                        val t3 = viewModel.machinationType3[player.id]
                        val target1 = viewModel.machinationTarget1[player.id] ?: ""
                        val target2 = viewModel.machinationTarget2[player.id] ?: ""
                        val target3 = viewModel.machinationTarget3[player.id] ?: ""
                        if (t1 != null && target1.isNotEmpty()) machinations.add(CampaignMachination(player.id, target1, t1))
                        if (t2 != null && target2.isNotEmpty()) machinations.add(CampaignMachination(player.id, target2, t2))
                        if (player.id in byePlayerIds && t3 != null && target3.isNotEmpty()) machinations.add(CampaignMachination(player.id, target3, t3))
                        if (campaign.attacksEnabled && viewModel.machinationIsAttacking[player.id] == true) {
                            val type = viewModel.machinationAttackType[player.id] ?: AttackType.ASSAULT
                            val tPlayer = viewModel.machinationAttackTargetPlayer[player.id] ?: ""
                            val tChar = viewModel.machinationAttackTargetChar[player.id] ?: -1
                            if (tPlayer.isNotEmpty() && tChar != -1 && player.attackPoints >= type.cost) {
                                attacks.add(CampaignAttack(player.id, tPlayer, tChar, type))
                            }
                        }
                    }
                    onConfirm(machinations, attacks)
                },
                modifier = Modifier.weight(1f),
                shape = theme.cardShape
            ) {
                Text("Confirm")
            }
        }
    }
}

@Composable
private fun PlayerMachinationCard(
    player: CampaignPlayer,
    hasBonus: Boolean,
    type1: MachinationType?,
    onType1Change: (MachinationType?) -> Unit,
    type2: MachinationType?,
    onType2Change: (MachinationType?) -> Unit,
    type3: MachinationType?,
    onType3Change: (MachinationType?) -> Unit,
    target1: String,
    onTarget1Change: (String) -> Unit,
    target2: String,
    onTarget2Change: (String) -> Unit,
    target3: String,
    onTarget3Change: (String) -> Unit,
    eligibleTargets: List<CampaignPlayer>,
    attacksEnabled: Boolean,
    isAttacking: Boolean,
    onIsAttackingChange: (Boolean) -> Unit,
    attackType: AttackType,
    onAttackTypeChange: (AttackType) -> Unit,
    attackTargetPlayer: String,
    onAttackTargetPlayerChange: (String) -> Unit,
    attackTargetChar: Int,
    onAttackTargetCharChange: (Int) -> Unit,
    allPlayers: List<CampaignPlayer>,
    state: CharacterState,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    val attackTargetTroupe = state.troupes.find { it.id == allPlayers.find { p -> p.id == attackTargetPlayer }?.troupeId }
    val attackTargetCharacters = attackTargetTroupe?.characterIds?.mapNotNull { id -> state.characters.find { it.id == id } } ?: emptyList()
    // For row 2: exclude same player targeted with same type as row 1
    val eligibleTargets2 = if (type1 != null && type1 == type2 && target1.isNotEmpty())
        eligibleTargets.filter { it.id != target1 }
    else eligibleTargets
    // For row 3: exclude players already targeted with the same type in rows 1 and 2
    val usedByType3 = buildSet {
        if (type1 != null && target1.isNotEmpty() && type1 == type3) add(target1)
        if (type2 != null && target2.isNotEmpty() && type2 == type3) add(target2)
    }
    val eligibleTargets3 = eligibleTargets.filter { it.id !in usedByType3 }

    ThemedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: name + bye badge + AP + attack toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    player.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (hasBonus) {
                    Text(
                        "Bye +1 slot",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (attacksEnabled) {
                    Text(
                        "${player.attackPoints} AP",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (player.attackPoints > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (player.attackPoints > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Attack", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = isAttacking, onCheckedChange = onIsAttackingChange)
                        }
                    }
                }
            }

            // Machination row 1
            MachinationRowItem(
                selectedType = type1,
                onTypeChange = onType1Change,
                target = target1,
                onTargetChange = onTarget1Change,
                eligibleTargets = eligibleTargets,
                theme = theme
            )

            // Machination row 2 — hidden when row 1 is Skip
            if (type1 != null) {
                MachinationRowItem(
                    selectedType = type2,
                    onTypeChange = onType2Change,
                    target = target2,
                    onTargetChange = onTarget2Change,
                    eligibleTargets = eligibleTargets2,
                    theme = theme
                )
            }

            // Machination row 3 — bonus slot for bye players, shown after row 2 is filled
            if (hasBonus && type2 != null) {
                MachinationRowItem(
                    selectedType = type3,
                    onTypeChange = onType3Change,
                    target = target3,
                    onTargetChange = onTarget3Change,
                    eligibleTargets = eligibleTargets3,
                    theme = theme
                )
            }

            // Attack section
            if (isAttacking && attacksEnabled && player.attackPoints > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MachTypeIconButton(
                        icon = Icons.Default.Bolt,
                        label = "Assault",
                        subtitle = "(1 AP)",
                        selected = attackType == AttackType.ASSAULT,
                        enabled = player.attackPoints >= 1,
                        onClick = { onAttackTypeChange(AttackType.ASSAULT) },
                        theme = theme
                    )
                    MachTypeIconButton(
                        icon = Icons.Default.PersonRemove,
                        label = "Abduction",
                        subtitle = "(2 AP)",
                        selected = attackType == AttackType.ABDUCTION,
                        enabled = player.attackPoints >= 2,
                        onClick = { onAttackTypeChange(AttackType.ABDUCTION) },
                        theme = theme
                    )
                }
                val attackEligible = allPlayers.filter { it.id != player.id }
                Text("Target player", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlayerDropdownSmall(players = attackEligible, selectedId = attackTargetPlayer, onSelected = onAttackTargetPlayerChange)
                if (attackTargetPlayer.isNotEmpty()) {
                    Text("Target character", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (attackTargetCharacters.isEmpty()) {
                        Text("No troupe assigned to this player", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        CharacterPortraitSelector(
                            characters = attackTargetCharacters,
                            selectedId = attackTargetChar,
                            onSelected = onAttackTargetCharChange,
                            state = state
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MachinationRowItem(
    selectedType: MachinationType?,
    onTypeChange: (MachinationType?) -> Unit,
    target: String,
    onTargetChange: (String) -> Unit,
    eligibleTargets: List<CampaignPlayer>,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MachTypeIconButton(
            icon = Icons.Default.Block,
            label = "Sabotage",
            subtitle = null,
            selected = selectedType == MachinationType.SABOTAGE,
            enabled = true,
            onClick = { onTypeChange(MachinationType.SABOTAGE) },
            theme = theme
        )
        MachTypeIconButton(
            icon = Icons.Default.Favorite,
            label = "Support",
            subtitle = null,
            selected = selectedType == MachinationType.SUPPORT,
            enabled = true,
            onClick = { onTypeChange(MachinationType.SUPPORT) },
            theme = theme
        )
        MachTypeIconButton(
            icon = Icons.Default.Remove,
            label = "Skip",
            subtitle = null,
            selected = selectedType == null,
            enabled = true,
            onClick = { onTypeChange(null) },
            theme = theme
        )
        if (selectedType != null) {
            if (eligibleTargets.isEmpty()) {
                Text(
                    "No eligible targets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    PlayerDropdownSmall(
                        players = eligibleTargets,
                        selectedId = target,
                        onSelected = onTargetChange
                    )
                }
            }
        }
    }
}

@Composable
private fun MachTypeIconButton(
    icon: ImageVector,
    label: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    theme: io.github.garemat.lunachron.ui.theme.AppThemeProperties
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(theme.cardShape)
            .border(1.dp, borderColor, theme.cardShape)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = contentColor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterPortraitSelector(
    characters: List<Character>,
    selectedId: Int,
    onSelected: (Int) -> Unit,
    state: CharacterState
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        characters.forEach { char ->
            val isSelected = char.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable { onSelected(char.id) }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                ) {
                    CharacterPortrait(character = char, size = 48.dp)
                }
                Text(
                    char.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 56.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDropdownSmall(
    players: List<CampaignPlayer>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlayer = players.find { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedPlayer?.name ?: "—",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.name, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelected(player.id); expanded = false }
                )
            }
        }
    }
}


enum class CampaignSubScreen {
    RANKINGS, GAMES, SCHEDULE, HISTORY
}
