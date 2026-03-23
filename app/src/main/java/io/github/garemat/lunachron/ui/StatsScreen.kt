package io.github.garemat.lunachron.ui

import androidx.compose.foundation.Image
import io.github.garemat.lunachron.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.garemat.lunachron.*
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatsScreen(
    viewModel: CharacterViewModel
) {
    val results by viewModel.gameResults.collectAsState()
    val theme = LocalAppThemeProperties.current

    Column(modifier = Modifier.fillMaxSize().padding(theme.screenPadding)) {
        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No games played yet!", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(results) { result ->
                    GameResultBanner(result)
                }
            }
        }
    }
}

@Composable
fun GameResultBanner(result: GameResult) {
    val theme = LocalAppThemeProperties.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = theme.cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = theme.surfaceElevation)
    ) {
        Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
            // Quadrant Backgrounds with Borders and Faction Images
            QuadrantLayout(result.playerStats, result.winnerIndex)

            // Center Score Circle
            ScoreCircle(result.playerStats, Modifier.align(Alignment.Center))

            // Player Info Overlays
            PlayerStatsOverlay(result)
            
            // Timestamp
            val date = remember(result.timestamp) {
                SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(result.timestamp))
            }
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp)
            )
        }
    }
}

@Composable
fun ScoreCircle(playerStats: List<PlayerStat>, modifier: Modifier = Modifier) {
    val theme = LocalAppThemeProperties.current
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(theme.scoreCircleColor)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (playerStats.size) {
            2 -> {
                ScoreText(playerStats[0].totalStones, Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
                ScoreText(playerStats[1].totalStones, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            }
            3 -> {
                ScoreText(playerStats[0].totalStones, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp), fontSize = 18.sp)
                ScoreText(playerStats[1].totalStones, Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 8.dp), fontSize = 18.sp)
                ScoreText(playerStats[2].totalStones, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), fontSize = 18.sp)
            }
            4 -> {
                ScoreText(playerStats[0].totalStones, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp), fontSize = 18.sp)
                ScoreText(playerStats[1].totalStones, Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 8.dp), fontSize = 18.sp)
                ScoreText(playerStats[2].totalStones, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp), fontSize = 18.sp)
                ScoreText(playerStats[3].totalStones, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp), fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun ScoreText(score: Int, modifier: Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 22.sp) {
    Text(
        text = score.toString(),
        color = Color.White,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize,
        modifier = modifier
    )
}

@Composable
fun PlayerQuadrant(stat: PlayerStat, isWinner: Boolean, modifier: Modifier) {
    val theme = LocalAppThemeProperties.current
    val backgroundRes = when(stat.faction) {
        Faction.COMMONWEALTH -> R.drawable.commonwealth
        Faction.DOMINION -> R.drawable.dominion
        Faction.LESHAVULT -> R.drawable.leshavult
        Faction.SHADES -> R.drawable.shades
    }

    Box(
        modifier = modifier
            .background(getFactionColor(stat.faction))
            .then(
                if (isWinner) Modifier.border(2.dp, theme.rankingGoldColor)
                else Modifier.border(1.dp, Color.White)
            )
    ) {
        if (theme.showFactionBackgrounds && backgroundRes != 0) {
            Image(
                painter = painterResource(id = backgroundRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun QuadrantLayout(stats: List<PlayerStat>, winnerIndex: Int?) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            PlayerQuadrant(stats[0], isWinner = winnerIndex == 0, modifier = Modifier.weight(1f).fillMaxHeight())
            if (stats.size > 1) {
                PlayerQuadrant(stats[1], isWinner = winnerIndex == 1, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (stats.size > 2) {
            if (stats.size == 3) {
                PlayerQuadrant(stats[2], isWinner = winnerIndex == 2, modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                Row(modifier = Modifier.weight(1f)) {
                    PlayerQuadrant(stats[2], isWinner = winnerIndex == 2, modifier = Modifier.weight(1f).fillMaxHeight())
                    if (stats.size > 3) {
                        PlayerQuadrant(stats[3], isWinner = winnerIndex == 3, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerStatsOverlay(result: GameResult) {
    val stats = result.playerStats
    val winnerIdx = result.winnerIndex

    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (stats.size == 2) {
            PlayerInfo(stats[0], modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.45f), textAlign = TextAlign.Center)
            PlayerInfo(stats[1], modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.45f), textAlign = TextAlign.Center)
        } else {
            PlayerInfo(stats[0], modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.45f))
            if (stats.size > 1) {
                PlayerInfo(stats[1], modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.45f), textAlign = TextAlign.End)
            }
            if (stats.size == 3) {
                PlayerInfo(stats[2], modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.9f), textAlign = TextAlign.Center)
            } else if (stats.size > 3) {
                PlayerInfo(stats[2], modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.45f))
                PlayerInfo(stats[3], modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.45f), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun PlayerInfo(stat: PlayerStat, modifier: Modifier, textAlign: TextAlign = TextAlign.Start) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = when(textAlign) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        val displayName = if (!stat.playerName.isNullOrBlank()) "${stat.playerName} - ${stat.troupeName}" else stat.troupeName
        Text(
            text = displayName,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val charNames = stat.characterStats.joinToString(", ") { it.name }
        Text(
            text = charNames,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp,
            maxLines = if (textAlign == TextAlign.Center) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            lineHeight = if (textAlign == TextAlign.Center) 12.sp else TextUnit.Unspecified
        )
    }
}
