package io.github.garemat.lunachron.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.garemat.lunachron.CharacterEvent
import io.github.garemat.lunachron.CharacterState
import io.github.garemat.lunachron.NewsItem
import io.github.garemat.lunachron.Troupe
import io.github.garemat.lunachron.ui.theme.LocalAppThemeProperties
import androidx.compose.ui.layout.ContentScale

@Composable
fun HomeScreen(
    state: CharacterState,
    onEvent: (CharacterEvent) -> Unit,
    onQuickStartTroupe: (Troupe) -> Unit = {},
    onTargetPositioned: (String, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val theme = LocalAppThemeProperties.current
    var backPressedTime by remember { mutableLongStateOf(0L) }
    
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (backPressedTime + 2000 > currentTime) {
            (context as? Activity)?.finish()
        } else {
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            backPressedTime = currentTime
        }
    }

    val favouriteTroupes = remember(state.troupes) { state.troupes.filter { it.isFavourite } }
    val newsListState = rememberLazyListState()
    val showQuickStart by remember {
        derivedStateOf {
            newsListState.firstVisibleItemIndex == 0 && newsListState.firstVisibleItemScrollOffset < 80
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = theme.screenPadding)) {
            AnimatedVisibility(
                visible = favouriteTroupes.isNotEmpty() && showQuickStart,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(theme.verticalSpacing))
                    Text(
                        text = "Quick Start",
                        style = theme.titleStyle.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                    favouriteTroupes.forEach { troupe ->
                        QuickStartCard(troupe = troupe, onClick = { onQuickStartTroupe(troupe) })
                        Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Text(
                text = "Latest News",
                style = theme.titleStyle.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(vertical = theme.verticalSpacing)
                    .onGloballyPositioned { onTargetPositioned("Latest News", it) }
            )

            when {
                state.isFetchingNews && state.newsItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.newsItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "News not loaded",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(theme.verticalSpacing))
                            Button(onClick = { onEvent(CharacterEvent.RefreshNews) }) {
                                Text("Load news", style = theme.buttonTextStyle)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = newsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = theme.verticalSpacing),
                        verticalArrangement = Arrangement.spacedBy(theme.verticalSpacing)
                    ) {
                        items(state.newsItems) { item ->
                            NewsCard(item = item, onClick = { uriHandler.openUri(item.url) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStartCard(troupe: Troupe, onClick: () -> Unit) {
    val theme = LocalAppThemeProperties.current
    val factionColor = getFactionColor(troupe.faction)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(theme.cardContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = factionColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = troupe.troupeName,
                    style = theme.headerStyle.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${troupe.faction.name.lowercase().replaceFirstChar { it.uppercase() }} · ${troupe.characterIds.size} characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play", style = theme.buttonTextStyle)
            }
        }
    }
}

@Composable
fun NewsCard(item: NewsItem, onClick: () -> Unit) {
    val theme = LocalAppThemeProperties.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = theme.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(theme.cardContentPadding)) {
                Text(
                    text = item.title,
                    style = theme.titleStyle.copy(fontSize = 20.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(theme.verticalSpacing / 4))
                Text(
                    text = item.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (item.summary != null) {
                    Spacer(modifier = Modifier.height(theme.verticalSpacing / 2))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
