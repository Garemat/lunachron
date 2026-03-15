package io.github.garemat.lunachron.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import java.util.UUID

@Composable
fun CampaignManagementScreen(
    state: CharacterState,
    viewModel: CharacterViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Int) -> Unit,
    onNavigateToAddCampaign: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    
    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.campaigns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HistoryEdu, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(theme.verticalSpacing))
                    Text("No campaigns found", style = theme.headerStyle, color = MaterialTheme.colorScheme.outline)
                    Text("Start your journey as a Wizard Chamberlain!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(theme.screenPadding),
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
            ) {
                items(state.campaigns) { campaign ->
                    CampaignListItem(
                        campaign = campaign,
                        onClick = { onNavigateToDetails(campaign.id) },
                        onDelete = { viewModel.deleteCampaign(campaign) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNavigateToAddCampaign,
            modifier = Modifier.align(Alignment.BottomEnd).padding(theme.screenPadding),
            shape = theme.navItemShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Campaign")
        }
    }
}

@Composable
fun CampaignListItem(
    campaign: Campaign,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(campaign.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${campaign.players.size} Players • Round ${campaign.currentRound}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
