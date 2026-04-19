package io.github.garemat.lunachron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun CampaignHubScreen(
    state: CharacterState,
    onLocalTrackingSelected: () -> Unit,
    onHostCampaignSelected: () -> Unit,
    onJoinCampaignSelected: () -> Unit,
    onActiveCampaignsSelected: () -> Unit
) {
    val isMoonstone = LocalAppThemeProperties.current.showExpandedStatsHeader

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Campaigns",
            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp)
                    else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isMoonstone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        SetupOptionCard(
            title = "Local Tracking",
            description = "Manage local campaign records and results.",
            icon = Icons.Default.HistoryEdu,
            backgroundType = "commonwealth",
            onClick = onLocalTrackingSelected
        )

        Spacer(modifier = Modifier.height(12.dp))

        SetupOptionCard(
            title = "Host Campaign",
            description = "I am the Wizard Chamberlain.",
            icon = Icons.Default.AddCircle,
            backgroundType = "dominion",
            onClick = { if (state.isRegistered) onHostCampaignSelected() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SetupOptionCard(
            title = "Join Campaign",
            description = "I'll be gathering the stones.",
            icon = Icons.Default.GroupAdd,
            backgroundType = "leshavult",
            onClick = { if (state.isRegistered) onJoinCampaignSelected() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SetupOptionCard(
            title = "Active Campaigns",
            description = "View and manage your online campaigns.",
            icon = Icons.Default.Campaign,
            backgroundType = "shades",
            onClick = { if (state.isRegistered) onActiveCampaignsSelected() }
        )

        if (!state.isRegistered) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Register your device in Settings to access online campaigns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
