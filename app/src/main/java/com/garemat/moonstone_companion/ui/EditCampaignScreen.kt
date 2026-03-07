package com.garemat.moonstone_companion.ui

import androidx.compose.runtime.*
import com.garemat.moonstone_companion.*

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
            viewModel.selectedCampaignPlayers.toList() != (originalCampaign?.players ?: emptyList<CampaignPlayer>())

    CampaignFormContent(
        title = "Edit Campaign",
        campaignName = viewModel.newCampaignName,
        onNameChange = { viewModel.newCampaignName = it },
        description = viewModel.newCampaignDescription,
        onDescriptionChange = { viewModel.newCampaignDescription = it },
        attacksEnabled = viewModel.newCampaignAttacksEnabled,
        onAttacksEnabledChange = { viewModel.newCampaignAttacksEnabled = it },
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
                viewModel.newCampaignAttacksEnabled
            )
            onNavigateBack()
        },
        onNavigateBack = onNavigateBack
    )
}
