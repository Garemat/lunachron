package com.garemat.moonstone_companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.*
import com.garemat.moonstone_companion.ui.theme.LocalAppThemeProperties

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
                
                val round = campaign.rounds.find { it.roundNumber == campaign.currentRound }
                val hasScheduled = round?.games?.isNotEmpty() == true || round?.skipPlayerIds?.isNotEmpty() == true
                SetupOptionCard(
                    title = if (hasScheduled) "Active Games" else "Schedule Games",
                    description = if (hasScheduled) "Record match results" else "Pair up players for Round ${campaign.currentRound}",
                    icon = Icons.Default.PlayArrow,
                    onClick = { viewModel.currentCampaignSubScreen = CampaignSubScreen.GAMES }
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
                    title = "Round History",
                    description = "Review previous rounds",
                    icon = Icons.Default.History,
                    onClick = { viewModel.currentCampaignSubScreen = CampaignSubScreen.HISTORY }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                when (viewModel.currentCampaignSubScreen) {
                    CampaignSubScreen.RANKINGS -> RankingsTab(campaign, state, theme)
                    CampaignSubScreen.GAMES -> GamesTab(campaign, state, viewModel, theme)
                    CampaignSubScreen.MACHINATIONS -> MachinationsTab(campaign, state, viewModel, theme)
                    CampaignSubScreen.ATTACKS -> AttacksTab(campaign, state, viewModel, theme)
                    CampaignSubScreen.HISTORY -> HistoryTab(campaign, state, theme)
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun RankingsTab(campaign: Campaign, state: CharacterState, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties) {
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties
) {
    val currentRound = campaign.rounds.find { it.roundNumber == campaign.currentRound }
    val games = currentRound?.games ?: emptyList()
    val skipPlayers = currentRound?.skipPlayerIds ?: emptyList()
    val hasSchedule = games.isNotEmpty() || skipPlayers.isNotEmpty()

    var isScheduling by remember(campaign.currentRound) { mutableStateOf(!hasSchedule) }
    val isMachinating = viewModel.isCampaignMachinating

    fun saveSchedule(gamesList: List<CampaignGame>, skips: List<String>) {
        val newRounds = campaign.rounds.toMutableList()
        val roundIdx = newRounds.indexOfFirst { it.roundNumber == campaign.currentRound }
        val existing = newRounds.getOrNull(roundIdx)
        val updatedRound = (existing ?: CampaignRound(campaign.currentRound, emptyList())).copy(
            games = gamesList, skipPlayerIds = skips
        )
        if (roundIdx != -1) newRounds[roundIdx] = updatedRound else newRounds.add(updatedRound)
        viewModel.updateCampaign(campaign.copy(rounds = newRounds))
    }

    fun saveMachinationsAndAttacks(machinations: List<CampaignMachination>, attacks: List<CampaignAttack>) {
        val newRounds = campaign.rounds.toMutableList()
        val roundIdx = newRounds.indexOfFirst { it.roundNumber == campaign.currentRound }
        val existing = newRounds.getOrNull(roundIdx)
        val updatedRound = (existing ?: CampaignRound(campaign.currentRound, emptyList())).copy(
            machinations = machinations, attacks = attacks
        )
        if (roundIdx != -1) newRounds[roundIdx] = updatedRound else newRounds.add(updatedRound)
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

    if (isScheduling) {
        val initialPlayersPerGame = games.firstOrNull()?.playerIds?.size ?: 2
        val initialAssignments = buildMap {
            campaign.players.forEach { put(it.id, -1) }
            games.forEachIndexed { idx, game -> game.playerIds.forEach { put(it, idx + 1) } }
            skipPlayers.forEach { put(it, 0) }
        }
        SchedulingPanel(
            campaign = campaign,
            initialPlayersPerGame = initialPlayersPerGame,
            initialAssignments = initialAssignments,
            theme = theme,
            onConfirm = { gamesList, skips ->
                saveSchedule(gamesList, skips)
                isScheduling = false
                viewModel.initMachinationDraft(campaign)
            },
            onCancel = if (hasSchedule) { { isScheduling = false } } else null
        )
    } else if (isMachinating) {
        MachinationPhasePanel(
            campaign = campaign,
            state = state,
            currentRound = currentRound,
            viewModel = viewModel,
            theme = theme,
            onConfirm = { machinations, attacks ->
                saveMachinationsAndAttacks(machinations, attacks)
                viewModel.clearMachinationDraft()
            },
            onSaveDraft = {
                viewModel.saveMachinationDraft(campaign)
                viewModel.currentCampaignSubScreen = null
            }
        )
    } else {
        var editingGameIndices by remember(campaign.currentRound) { mutableStateOf(emptySet<Int>()) }
        Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { isScheduling = true; viewModel.isCampaignMachinating = false }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Pairings")
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing), modifier = Modifier.weight(1f)) {
                itemsIndexed(games) { idx, game ->
                    val isEditing = !game.isPlayed || idx in editingGameIndices
                    GameMatchCard(
                        game = game,
                        campaign = campaign,
                        isEditing = isEditing,
                        theme = theme,
                        onRecordResult = { winnerId ->
                            viewModel.recordCampaignGameResult(campaign, game, winnerId)
                            editingGameIndices = editingGameIndices - idx
                        },
                        onEditClick = { editingGameIndices = editingGameIndices + idx }
                    )
                }

                if (skipPlayers.isNotEmpty()) {
                    item {
                        Text("Skipping this round:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
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

            if (games.all { it.isPlayed } && hasSchedule) {
                Button(
                    onClick = { viewModel.progressCampaignRound(campaign) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape
                ) {
                    Text("Progress to Round ${campaign.currentRound + 1}")
                }
            }
        }
    }
}

@Composable
fun GameMatchCard(
    game: CampaignGame,
    campaign: Campaign,
    isEditing: Boolean,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
    onRecordResult: (String?) -> Unit,
    onEditClick: () -> Unit
) {
    val players = game.playerIds.mapNotNull { pid -> campaign.players.find { it.id == pid } }
    val isTie = game.isPlayed && game.winnerId == null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    when {
                        isTie -> "Result: Draw"
                        game.isPlayed -> "Result recorded"
                        else -> "Select winner"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (game.isPlayed && !isEditing) {
                    TextButton(
                        onClick = onEditClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit result", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (players.size == 2) {
                val p1 = players[0]
                val p2 = players[1]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerSelectCard(
                        name = p1.name,
                        isWinner = game.isPlayed && game.winnerId == p1.id,
                        isClickable = isEditing,
                        onClick = { onRecordResult(p1.id) },
                        modifier = Modifier.weight(1f),
                        theme = theme
                    )
                    Text(
                        "VS",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PlayerSelectCard(
                        name = p2.name,
                        isWinner = game.isPlayed && game.winnerId == p2.id,
                        isClickable = isEditing,
                        onClick = { onRecordResult(p2.id) },
                        modifier = Modifier.weight(1f),
                        theme = theme
                    )
                }
                if (isEditing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onRecordResult(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = theme.cardShape
                    ) {
                        Text("Tie / Draw")
                    }
                }
            } else {
                players.forEach { player ->
                    PlayerSelectCard(
                        name = player.name,
                        isWinner = game.isPlayed && game.winnerId == player.id,
                        isClickable = isEditing,
                        onClick = { onRecordResult(player.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        theme = theme
                    )
                }
                if (isEditing) {
                    OutlinedButton(
                        onClick = { onRecordResult(null) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = theme.cardShape
                    ) {
                        Text("Tie / No Winner")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSelectCard(
    name: String,
    isWinner: Boolean,
    isClickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties
) {
    val containerColor = when {
        isWinner -> MaterialTheme.colorScheme.primaryContainer
        isClickable -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        onClick = onClick,
        enabled = isClickable,
        modifier = modifier,
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isWinner) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isWinner) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Winner",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                color = if (isWinner) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulingPanel(
    campaign: Campaign,
    initialPlayersPerGame: Int,
    initialAssignments: Map<String, Int>,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
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
fun HistoryTab(campaign: Campaign, state: CharacterState, theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties) {
    val rounds = campaign.rounds.reversed()

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
                    "No round history yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val mostRecentRound = rounds.firstOrNull()?.roundNumber
    var expandedRounds by remember { mutableStateOf(if (mostRecentRound != null) setOf(mostRecentRound) else emptySet()) }

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

private enum class MachCombo { SS, S_SAB, SAB_SAB, SKIP }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MachinationPhasePanel(
    campaign: Campaign,
    state: CharacterState,
    currentRound: CampaignRound?,
    viewModel: CharacterViewModel,
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties,
    onConfirm: (List<CampaignMachination>, List<CampaignAttack>) -> Unit,
    onSaveDraft: () -> Unit
) {
    val games = currentRound?.games ?: emptyList()
    fun opponentsOf(playerId: String) = games
        .filter { playerId in it.playerIds }
        .flatMap { it.playerIds }
        .filter { it != playerId }
        .toSet()

    fun comboOf(playerId: String): MachCombo {
        val t1 = viewModel.machinationType1[playerId]
        val t2 = viewModel.machinationType2[playerId]
        return when {
            t1 == null -> MachCombo.SKIP
            t1 == MachinationType.SUPPORT && t2 == MachinationType.SUPPORT -> MachCombo.SS
            t1 == MachinationType.SUPPORT -> MachCombo.S_SAB
            else -> MachCombo.SAB_SAB
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
        Text("Machinations & Attacks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Each player can use 2 machinations. Targets cannot be scheduled opponents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(theme.verticalSpacing))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(campaign.players) { index, player ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = theme.verticalSpacing))
                }
                val combo = comboOf(player.id)
                val eligibleTargets = campaign.players.filter { it.id != player.id && it.id !in opponentsOf(player.id) }
                PlayerMachinationCard(
                    player = player,
                    combo = combo,
                    onComboChange = { newCombo ->
                        when (newCombo) {
                            MachCombo.SKIP -> { viewModel.machinationType1[player.id] = null; viewModel.machinationType2[player.id] = null }
                            MachCombo.SS -> { viewModel.machinationType1[player.id] = MachinationType.SUPPORT; viewModel.machinationType2[player.id] = MachinationType.SUPPORT }
                            MachCombo.S_SAB -> { viewModel.machinationType1[player.id] = MachinationType.SUPPORT; viewModel.machinationType2[player.id] = MachinationType.SABOTAGE }
                            MachCombo.SAB_SAB -> { viewModel.machinationType1[player.id] = MachinationType.SABOTAGE; viewModel.machinationType2[player.id] = MachinationType.SABOTAGE }
                        }
                        viewModel.machinationTarget1.remove(player.id)
                        viewModel.machinationTarget2.remove(player.id)
                    },
                    target1 = viewModel.machinationTarget1[player.id] ?: "",
                    onTarget1Change = { viewModel.machinationTarget1[player.id] = it },
                    target2 = viewModel.machinationTarget2[player.id] ?: "",
                    onTarget2Change = { viewModel.machinationTarget2[player.id] = it },
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
                        val target1 = viewModel.machinationTarget1[player.id] ?: ""
                        val target2 = viewModel.machinationTarget2[player.id] ?: ""
                        if (t1 != null && target1.isNotEmpty()) machinations.add(CampaignMachination(player.id, target1, t1))
                        if (t2 != null && target2.isNotEmpty()) machinations.add(CampaignMachination(player.id, target2, t2))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerMachinationCard(
    player: CampaignPlayer,
    combo: MachCombo,
    onComboChange: (MachCombo) -> Unit,
    target1: String,
    onTarget1Change: (String) -> Unit,
    target2: String,
    onTarget2Change: (String) -> Unit,
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
    theme: com.garemat.moonstone_companion.ui.theme.AppThemeProperties
) {
    val attackTargetTroupe = state.troupes.find { it.id == allPlayers.find { p -> p.id == attackTargetPlayer }?.troupeId }
    val attackTargetCharacters = attackTargetTroupe?.characterIds?.mapNotNull { id -> state.characters.find { it.id == id } } ?: emptyList()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(player.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (attacksEnabled) {
                    Text(
                        "${player.attackPoints} AP",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (player.attackPoints > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Machinations", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (attacksEnabled && player.attackPoints > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Attack", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = isAttacking, onCheckedChange = onIsAttackingChange)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = combo == MachCombo.SS, onClick = { onComboChange(MachCombo.SS) }, label = { Text("Support × 2") })
                FilterChip(selected = combo == MachCombo.S_SAB, onClick = { onComboChange(MachCombo.S_SAB) }, label = { Text("Support + Sabotage") })
                FilterChip(selected = combo == MachCombo.SAB_SAB, onClick = { onComboChange(MachCombo.SAB_SAB) }, label = { Text("Sabotage × 2") })
                FilterChip(selected = combo == MachCombo.SKIP, onClick = { onComboChange(MachCombo.SKIP) }, label = { Text("Skip") })
            }

            if (combo != MachCombo.SKIP) {
                val mach1Label = when (combo) {
                    MachCombo.SS -> "Support 1"; MachCombo.S_SAB -> "Support"
                    MachCombo.SAB_SAB -> "Sabotage 1"; MachCombo.SKIP -> ""
                }
                val mach2Label = when (combo) {
                    MachCombo.SS -> "Support 2"; MachCombo.S_SAB -> "Sabotage"
                    MachCombo.SAB_SAB -> "Sabotage 2"; MachCombo.SKIP -> ""
                }
                if (eligibleTargets.isEmpty()) {
                    Text(
                        "No eligible targets (all opponents this round)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mach1Label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PlayerDropdownSmall(players = eligibleTargets, selectedId = target1, onSelected = onTarget1Change)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mach2Label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PlayerDropdownSmall(players = eligibleTargets, selectedId = target2, onSelected = onTarget2Change)
                        }
                    }
                }
            }

            if (isAttacking && attacksEnabled && player.attackPoints > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = attackType == AttackType.ASSAULT,
                        onClick = { onAttackTypeChange(AttackType.ASSAULT) },
                        label = { Text("Assault (1 AP)") },
                        enabled = player.attackPoints >= 1
                    )
                    FilterChip(
                        selected = attackType == AttackType.ABDUCTION,
                        onClick = { onAttackTypeChange(AttackType.ABDUCTION) },
                        label = { Text("Abduction (2 AP)") },
                        enabled = player.attackPoints >= 2
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
                        CharacterDropdownSmall(characters = attackTargetCharacters, selectedId = attackTargetChar, onSelected = onAttackTargetCharChange)
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterDropdownSmall(
    characters: List<Character>,
    selectedId: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedChar = characters.find { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedChar?.name ?: "—",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            characters.forEach { char ->
                DropdownMenuItem(
                    text = { Text(char.name, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelected(char.id); expanded = false }
                )
            }
        }
    }
}

enum class CampaignSubScreen {
    RANKINGS, GAMES, MACHINATIONS, ATTACKS, HISTORY
}
