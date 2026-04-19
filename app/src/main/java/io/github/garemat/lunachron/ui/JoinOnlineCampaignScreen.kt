package io.github.garemat.lunachron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinOnlineCampaignScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    var joinCode by remember { mutableStateOf("") }

    // Clear pending join state when leaving so stale data doesn't re-show
    DisposableEffect(Unit) { onDispose { /* pendingJoinCampaignId naturally superseded on next join */ } }

    // Error dialog
    if (state.onlineCampaignError != null) {
        AlertDialog(
            onDismissRequest = { onEvent(CharacterEvent.DismissOnlineCampaignError) },
            title = { Text("Could not join campaign", style = theme.headerStyle) },
            text = { Text(state.onlineCampaignError, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { onEvent(CharacterEvent.DismissOnlineCampaignError) }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Join Campaign", style = theme.titleStyle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(theme.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (state.pendingJoinCampaignId != null) {
                // ── Request sent state ────────────────────────────────────────
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Request sent!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The Wizard Chamberlain will review your request. Once approved you'll appear in the Active Campaigns list.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onNavigateBack, shape = theme.cardShape) {
                    Text("Done")
                }
            } else {
                // ── Code entry state ──────────────────────────────────────────
                Text(
                    "Enter join code",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Ask the Wizard Chamberlain for the 6-character code.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase().take(6) },
                    label = { Text("Join code") },
                    modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth(),
                    singleLine = true,
                    shape = theme.cardShape,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onEvent(CharacterEvent.RequestJoinCampaign(joinCode)) },
                    enabled = joinCode.length == 6 && !state.isJoiningCampaign,
                    modifier = Modifier.fillMaxWidth(),
                    shape = theme.cardShape
                ) {
                    if (state.isJoiningCampaign) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending request…", style = theme.buttonTextStyle)
                    } else {
                        Text("Request to join", style = theme.buttonTextStyle)
                    }
                }
            }
        }
    }
}
