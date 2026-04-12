package io.github.garemat.lunachron.ui

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.HostMode
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties

@Composable
fun GameModeHeroUI(
    onLocalSelected: () -> Unit,
    onHostSelected: () -> Unit,
    onJoinSelected: () -> Unit,
    onJoinTournamentSelected: () -> Unit,
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val theme = LocalAppThemeProperties.current
    val context = LocalContext.current

    // Cycling faction colour tint for hero card background
    val infiniteTransition = rememberInfiniteTransition(label = "heroFaction")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heroPhase"
    )
    val factionTints = remember {
        listOf(
            Color(0xFFC0392B), // Dominion
            Color(0xFF4A90D9), // Commonwealth
            Color(0xFF27AE60), // Leshavult
            Color(0xFF8E44AD), // Shades
        )
    }
    val tintIndex = ((phase * 4f).toInt()).coerceIn(0, 3)
    val nextTintIndex = (tintIndex + 1) % 4
    val tintFraction = (phase * 4f) - tintIndex.toFloat()
    val heroTint = lerp(factionTints[tintIndex], factionTints[nextTintIndex], tintFraction)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Choose Mode",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 3.sp, fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // ── Hero card — Local Game ───────────────────────────────────────────
        Card(
            onClick = onLocalSelected,
            modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned { onTargetPositioned("LocalGameOption", it) },
            shape = theme.cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().background(
                    Brush.linearGradient(
                        colors = listOf(
                            heroTint.copy(alpha = 0.10f),
                            heroTint.copy(alpha = 0.20f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
            ) {
                Column(modifier = Modifier.padding(start = 22.dp, top = 24.dp, end = 22.dp, bottom = 20.dp)) {
                    Text("⚔️", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Local Game", style = theme.titleStyle.copy(fontSize = 26.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Play at the table with friends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Begin Setup",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Other Modes",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // ── Secondary modes ─────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = theme.cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                SecondaryModeRow("📡", "Host Game", "Invite players over Wi-Fi") {
                    Toast.makeText(context, "These features are getting improved in an upcoming update", Toast.LENGTH_SHORT).show()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SecondaryModeRow("🔗", "Join Game", "Connect to a hosted session") {
                    Toast.makeText(context, "These features are getting improved in an upcoming update", Toast.LENGTH_SHORT).show()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SecondaryModeRow("🏆", "Local Tournament", "Round-robin bracket play") {
                    Toast.makeText(context, "These features are getting improved in an upcoming update", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun SecondaryModeRow(icon: String, title: String, description: String, onClick: () -> Unit) {
    val theme = LocalAppThemeProperties.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.5f)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(26.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = theme.headerStyle.copy(fontSize = 13.sp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Dialog shown when the user taps "Host Game", letting them choose between
 * same-Wi-Fi (NSD) and router-free (Wi-Fi Direct) hosting.
 */
@Composable
fun HostModeDialog(
    onSelectMode: (HostMode) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("How do you want to host?", style = theme.headerStyle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HostModeOptionCard(
                    title = "Same Wi-Fi",
                    description = "All players must be on the same router. Recommended for most setups.",
                    icon = Icons.Default.Wifi,
                    onClick = { onSelectMode(HostMode.WIFI_NSD) }
                )
                HostModeOptionCard(
                    title = "Wi-Fi Direct",
                    description = "No router needed — devices connect directly. Note: joining players may temporarily lose internet access.",
                    icon = Icons.Default.WifiTethering,
                    onClick = { onSelectMode(HostMode.WIFI_DIRECT) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = theme.cardShape
    )
}

@Composable
private fun HostModeOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val theme = LocalAppThemeProperties.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = theme.headerStyle.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val isMoonstone = LocalAppThemeProperties.current.showExpandedStatsHeader
    
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
            Text(
                if (isMultiplayer) "REJOIN SESSION" else "CONTINUE GAME", 
                fontSize = 18.sp, 
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { onNewGame() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(if (isMoonstone) 0.dp else 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(
                "START NEW GAME", 
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
