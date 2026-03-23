package io.github.garemat.lunachron.ui

import androidx.compose.runtime.*
import io.github.garemat.lunachron.*

@Composable
fun EditCampaignScreen(
    campaignId: Int,
    viewModel: CharacterViewModel,
    state: CharacterState,
    onNavigateBack: () -> Unit,
    onSelectTroupeForPlayer: (String) -> Unit
) {
    LaunchedEffect(campaignId) {
        viewModel.editCampaign(campaignId)
    }

    val originalCampaign = remember(state.campaigns, campaignId) {
        state.campaigns.find { it.id == campaignId }
    }
    val hasChanges = viewModel.newCampaignName != (originalCampaign?.name ?: "") ||
            viewModel.newCampaignDescription != (originalCampaign?.description ?: "") ||
            viewModel.newCampaignAttacksEnabled != (originalCampaign?.attacksEnabled ?: false) ||
            viewModel.newCampaignGameSize != (originalCampaign?.gameSize ?: 6) ||
            viewModel.newCampaignStartingCharacters != (originalCampaign?.startingCharacters ?: 6) ||
            viewModel.newCampaignCharacterGrowthEvery != (originalCampaign?.characterGrowthEvery ?: 1) ||
            viewModel.newCampaignUpgradeGrowthEvery != (originalCampaign?.upgradeGrowthEvery ?: 3) ||
            viewModel.selectedCampaignPlayers.toList() != (originalCampaign?.players ?: emptyList<CampaignPlayer>())

    CampaignFormContent(
        title = "Edit Campaign",
        isNewCampaign = false,
        campaignName = viewModel.newCampaignName,
        onNameChange = { viewModel.newCampaignName = it },
        description = viewModel.newCampaignDescription,
        onDescriptionChange = { viewModel.newCampaignDescription = it },
        attacksEnabled = viewModel.newCampaignAttacksEnabled,
        onAttacksEnabledChange = { viewModel.newCampaignAttacksEnabled = it },
        totalRounds = viewModel.newCampaignTotalRounds,
        onTotalRoundsChange = { viewModel.newCampaignTotalRounds = it },
        gameSize = viewModel.newCampaignGameSize,
        onGameSizeChange = { viewModel.newCampaignGameSize = it },
        startingCharacters = viewModel.newCampaignStartingCharacters,
        onStartingCharactersChange = { viewModel.newCampaignStartingCharacters = it },
        characterGrowthEvery = viewModel.newCampaignCharacterGrowthEvery,
        onCharacterGrowthEveryChange = { viewModel.newCampaignCharacterGrowthEvery = it },
        upgradeGrowthEvery = viewModel.newCampaignUpgradeGrowthEvery,
        onUpgradeGrowthEveryChange = { viewModel.newCampaignUpgradeGrowthEvery = it },
        players = viewModel.selectedCampaignPlayers,
        troupes = state.troupes,
        onSelectTroupeForPlayer = onSelectTroupeForPlayer,
        onPlayerAdded = { viewModel.selectedCampaignPlayers.add(it) },
        onPlayerRemoved = { viewModel.selectedCampaignPlayers.remove(it) },
        hasChanges = hasChanges,
        onSave = {
            viewModel.createCampaign(
                viewModel.newCampaignName,
                viewModel.newCampaignDescription,
                viewModel.selectedCampaignPlayers.toList(),
                viewModel.newCampaignAttacksEnabled,
                viewModel.newCampaignTotalRounds,
                viewModel.newCampaignGameSize,
                viewModel.newCampaignStartingCharacters,
                viewModel.newCampaignCharacterGrowthEvery,
                viewModel.newCampaignUpgradeGrowthEvery
            )
            onNavigateBack()
        },
        onNavigateBack = onNavigateBack
    )
}
