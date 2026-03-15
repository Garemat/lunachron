package io.github.garemat.lunachron.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun ActiveTournamentCard(
    tournamentName: String,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Active Tournament",
            style = theme.titleStyle.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = tournamentName,
            style = theme.titleStyle.copy(fontSize = 20.sp),
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "You are currently connected to an active tournament session. Would you like to return to the waiting room or leave the event?",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = theme.cardShape
        ) {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("JOIN WAITING ROOM", style = theme.buttonTextStyle)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onLeave,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = theme.cardShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("LEAVE TOURNAMENT", style = theme.buttonTextStyle.copy(color = MaterialTheme.colorScheme.error))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDiscoveryDialog(
    discoveredEndpoints: Map<String, String>,
    onJoinRequest: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onLeaveSession: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    var tournamentPasscode by remember { mutableStateOf("") }
    var selectedTournamentEndpoint by remember { mutableStateOf<Pair<String, String>?>(null) }

    AlertDialog(
        onDismissRequest = { 
            onDismiss()
            onLeaveSession() 
        },
        title = { Text("Join Local Tournament") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (selectedTournamentEndpoint == null) {
                    if (discoveredEndpoints.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("Searching for tournament hosts...", modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    } else {
                        Text("Select a tournament to join:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(discoveredEndpoints.toList()) { (id, name) ->
                                ListItem(
                                    headlineContent = { Text(name) },
                                    leadingContent = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                                    modifier = Modifier.clickable { selectedTournamentEndpoint = id to name }
                                )
                            }
                        }
                    }
                } else {
                    Text("Joining: ${selectedTournamentEndpoint?.second}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tournamentPasscode,
                        onValueChange = { tournamentPasscode = it.uppercase() },
                        label = { Text("Enter Event Pin") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = theme.cardShape
                    )
                }
            }
        },
        confirmButton = {
            if (selectedTournamentEndpoint != null) {
                Button(
                    onClick = {
                        selectedTournamentEndpoint?.let {
                            onJoinRequest(it.first, tournamentPasscode)
                        }
                    },
                    enabled = tournamentPasscode.isNotBlank(),
                    shape = theme.cardShape
                ) {
                    Text("Join", style = theme.buttonTextStyle)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { 
                    if (selectedTournamentEndpoint != null) selectedTournamentEndpoint = null
                    else {
                        onDismiss()
                        onLeaveSession()
                    }
                }
            ) { 
                Text(if (selectedTournamentEndpoint != null) "Back" else "Cancel")
            }
        },
        shape = theme.cardShape
    )
}
