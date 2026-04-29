package io.github.garemat.lunachron.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.OnlineCampaignSummary
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun ActiveOnlineCampaignsScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onCampaignSelected: (String) -> Unit = {}
) {
    val theme = LocalAppThemeProperties.current

    // Load campaigns when the screen first appears
    LaunchedEffect(Unit) { onEvent(CharacterEvent.LoadOnlineCampaigns) }

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

    Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoadingOnlineCampaigns -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.onlineCampaigns.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(theme.verticalSpacing))
                        Text("No active campaigns", style = theme.headerStyle, color = MaterialTheme.colorScheme.outline)
                        Text("Host or join a campaign to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
                else -> {
                    val approved = state.onlineCampaigns.filter { it.membershipStatus == "APPROVED" }
                    val pending  = state.onlineCampaigns.filter { it.membershipStatus == "PENDING" }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(theme.screenPadding),
                        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
                    ) {
                        if (pending.isNotEmpty()) {
                            item {
                                Text(
                                    "Awaiting Approval",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(pending) { campaign ->
                                PendingCampaignCard(campaign = campaign)
                            }
                        }
                        if (approved.isNotEmpty()) {
                            if (pending.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Active Campaigns",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                            items(approved) { campaign ->
                                OnlineCampaignCard(
                                    campaign = campaign,
                                    onClick = { onCampaignSelected(campaign.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun OnlineCampaignCard(campaign: OnlineCampaignSummary, onClick: () -> Unit = {}) {
    val theme = LocalAppThemeProperties.current
    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (campaign.isHost) Icons.Default.Star else Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    campaign.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                CampaignStatusChip(status = campaign.status)
            }

            if (!campaign.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(campaign.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${campaign.memberCount} members", style = MaterialTheme.typography.bodySmall)
                Text(if (campaign.isHost) "You are the Chamberlain" else "Member", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (campaign.isHost && campaign.pendingCount > 0) {
                    Badge { Text("${campaign.pendingCount}") }
                    Text("awaiting approval", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Show join code with copy button for the host of an open campaign
            if (campaign.isHost && campaign.joinCode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Join code: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        campaign.joinCode,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(campaign.joinCode)) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCampaignCard(campaign: OnlineCampaignSummary) {
    val theme = LocalAppThemeProperties.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.HourglassTop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    campaign.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Awaiting Chamberlain approval",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun CampaignStatusChip(status: String) {
    val (label, color) = when (status) {
        "OPEN"      -> "Open"      to MaterialTheme.colorScheme.primary
        "LOCKED"    -> "Locked"    to MaterialTheme.colorScheme.tertiary
        "DISBANDED" -> "Disbanded" to MaterialTheme.colorScheme.error
        else        -> status      to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
