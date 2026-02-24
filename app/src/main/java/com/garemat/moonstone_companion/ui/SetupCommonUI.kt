package com.garemat.moonstone_companion.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garemat.moonstone_companion.AppTheme
import com.garemat.moonstone_companion.ui.theme.LocalAppTheme

@Composable
fun SetupModeSelection(
    onLocalSelected: () -> Unit,
    onHostSelected: () -> Unit,
    onJoinSelected: () -> Unit,
    onJoinTournamentSelected: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Game Mode",
            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp) else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isMoonstone) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SetupOptionCard(
            title = "Local Game",
            description = "Play on a single device with 2-4 players.",
            icon = Icons.Default.Smartphone,
            backgroundType = "commonwealth",
            onClick = onLocalSelected,
            modifier = Modifier.onGloballyPositioned { onTargetPositioned("LocalGameOption", it) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        SetupOptionCard(
            title = "Host Game",
            description = "Start a multiplayer session for others to join.",
            icon = Icons.Default.WifiTethering,
            backgroundType = "dominion",
            onClick = onHostSelected
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        SetupOptionCard(
            title = "Join Game",
            description = "Connect to an existing multiplayer session nearby.",
            icon = Icons.Default.Wifi,
            backgroundType = "leshavult",
            onClick = onJoinSelected
        )

        Spacer(modifier = Modifier.height(12.dp))
        
        SetupOptionCard(
            title = "Join Local Tournament",
            description = "Connect to a tournament session nearby.",
            icon = Icons.Default.EmojiEvents,
            backgroundType = "shades",
            onClick = onJoinTournamentSelected
        )
    }
}

@Composable
fun SetupOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundType: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    val context = LocalContext.current
    val backgroundRes = remember(backgroundType) { context.resources.getIdentifier(backgroundType, "drawable", context.packageName) }
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMoonstone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isMoonstone) 2.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isMoonstone && backgroundRes != 0) {
                Image(
                    painter = painterResource(id = backgroundRes),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().alpha(0.1f),
                    contentScale = ContentScale.Crop
                )
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title, 
                        style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp) else MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold,
                        color = if (isMoonstone) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified
                    )
                    Text(
                        text = description, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GameInProgressContent(
    isMultiplayer: Boolean,
    onContinue: () -> Unit,
    onNewGame: () -> Unit
) {
    val isMoonstone = LocalAppTheme.current == AppTheme.MOONSTONE
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isMultiplayer) "Multiplayer Game in Progress" else "Game in Progress",
            style = if (isMoonstone) MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp) else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (isMoonstone) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = if (isMultiplayer) 
                "You have an active session. Would you like to rejoin it or start fresh?" 
                else "A local game is currently active. You can continue where you left off or start a new game.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isMultiplayer) "REJOIN SESSION" else "CONTINUE GAME", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { onNewGame() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("START NEW GAME", fontWeight = FontWeight.SemiBold)
        }
    }
}
