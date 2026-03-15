package io.github.garemat.lunachron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun CompendiumScreen(
    onNavigateToCharacters: () -> Unit,
    onNavigateToUpgrades: () -> Unit,
    onNavigateToCampaignCards: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(theme.screenPadding),
        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
    ) {
        SetupOptionCard(
            title = "Characters",
            description = "View all characters and their abilities",
            icon = Icons.Default.PersonSearch,
            onClick = onNavigateToCharacters
        )
        
        SetupOptionCard(
            title = "Campaign Cards",
            description = "Manage cards used during a campaign",
            icon = Icons.Default.HistoryEdu,
            onClick = onNavigateToCampaignCards
        )
        
        SetupOptionCard(
            title = "Upgrades",
            description = "Equippable upgrades for your troupe",
            icon = Icons.Default.AutoAwesome,
            onClick = onNavigateToUpgrades
        )
    }
}
