package io.github.garemat.lunachron.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.garemat.lunachron.CharacterState
import kotlin.math.roundToInt

private const val SPOTLIGHT_PADDING = 14f
private const val SPOTLIGHT_RADIUS = 18f
private const val SCRIM_ALPHA = 0.68f

private fun Rect.expand(delta: Float) = Rect(
    left = left - delta, top = top - delta,
    right = right + delta, bottom = bottom + delta
)

private fun LayoutCoordinates.boundsRelativeTo(anchor: LayoutCoordinates): Rect {
    val offset = positionInWindow() - anchor.positionInWindow()
    return Rect(Offset(offset.x, offset.y), size.toSize())
}

/**
 * Non-blocking tutorial overlay. Sits above the Scaffold in a root [Box] so that touches within
 * the spotlight hole pass through to the real UI beneath.
 *
 * Touch model:
 * - The overlay root Box has NO pointerInput modifier — it is transparent to touches.
 * - Four [Spacer]s are placed as children around the spotlight hole and consume all touches
 *   (blocking the user from interacting with the scrim area).
 * - The spotlight hole itself has no child composable on top of it, so touches fall through
 *   directly to the sibling Scaffold without any interception.
 * - For [AdvanceCondition.OnSpotlightTap] a thin transparent Box exactly covers the spotlight and
 *   fires [onAdvance] on DOWN without consuming, so the underlying action also fires.
 * - For [AdvanceCondition.OnNavigation] no blockers are added (nav bar items are siblings).
 * - Arrowless steps (no spotlight) have no blockers — all touches pass through.
 */
@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    currentStepIndex: Int,
    tutorialCoords: Map<String, LayoutCoordinates>,
    navController: NavController,
    state: CharacterState,
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
    onStepChanged: (Int) -> Unit = {},
) {
    val currentStep = steps.getOrNull(currentStepIndex) ?: return
    val isLastStep = currentStepIndex == steps.lastIndex
    val totalSteps = steps.size

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Capture troupe count when entering each step so we can detect additions.
    var troupeCountAtStepStart by remember { mutableIntStateOf(state.troupes.size) }
    LaunchedEffect(currentStepIndex) {
        troupeCountAtStepStart = state.troupes.size
        onStepChanged(currentStepIndex)
    }

    // Navigate to requiredRoute when this step demands it.
    LaunchedEffect(currentStep.requiredRoute, currentRoute) {
        val required = currentStep.requiredRoute ?: return@LaunchedEffect
        if (currentRoute != required) {
            navController.navigate(required) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // OnNavigation advancement.
    LaunchedEffect(currentRoute) {
        val cond = currentStep.advance
        if (cond is AdvanceCondition.OnNavigation && currentRoute == cond.route) onAdvance()
    }

    // OnStateChange advancement.
    LaunchedEffect(state.troupes) {
        val cond = currentStep.advance
        if (cond !is AdvanceCondition.OnStateChange) return@LaunchedEffect
        when (cond.key) {
            "troupe_added" -> if (state.troupes.size > troupeCountAtStepStart) onAdvance()
            "troupe_favourited" -> if (state.troupes.any { it.isFavourite }) onAdvance()
        }
    }

    // Overlay root coordinates — used to compute spotlight bounds in overlay-local space.
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val targetCoords = currentStep.targetTag?.let { tutorialCoords[it] }
    val spotlightBounds: Rect? = remember(targetCoords, overlayCoords) {
        val tc = targetCoords ?: return@remember null
        val oc = overlayCoords ?: return@remember null
        if (!tc.isAttached) return@remember null
        tc.boundsRelativeTo(oc)
    }

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp * density.density
    val screenH = config.screenHeightDp * density.density

    // The root Box intentionally has no pointerInput — it is transparent to touches.
    // Blocking is done by child Spacers that cover only the non-spotlight area.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayCoords = it }
    ) {
        // ── Scrim + spotlight hole ──────────────────────────────────────────────
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
            if (spotlightBounds != null) {
                val padded = spotlightBounds.expand(SPOTLIGHT_PADDING)
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = padded.topLeft,
                    size = Size(padded.width, padded.height),
                    cornerRadius = CornerRadius(SPOTLIGHT_RADIUS),
                    blendMode = BlendMode.Clear
                )
            }
        }

        // ── Touch blockers — 4 Spacers surrounding the spotlight hole ──────────
        // These are drawn BEFORE the skip button and tooltip so that the tooltip/skip
        // (higher z-order = innermost in Main pass = run first) are never blocked.
        // OnNavigation steps skip blocking entirely: nav targets (bottom bar, drawer)
        // are siblings of this overlay and would not be reachable even with non-consuming
        // pointerInput on a full-screen composable.
        val shouldBlock = spotlightBounds != null &&
                currentStep.advance !is AdvanceCondition.OnNavigation
        if (shouldBlock && spotlightBounds != null) {
            val padded = spotlightBounds.expand(SPOTLIGHT_PADDING)
            val consumeAll = Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                    }
                }
            }
            with(density) {
                // Top strip
                if (padded.top > 0f) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(padded.top.toDp())
                            .then(consumeAll)
                    )
                }
                // Bottom strip
                val bottomH = (screenH - padded.bottom).coerceAtLeast(0f)
                if (bottomH > 0f) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(bottomH.toDp())
                            .offset { IntOffset(0, padded.bottom.roundToInt()) }
                            .then(consumeAll)
                    )
                }
                // Left strip (spotlight band)
                val bandH = (padded.bottom - padded.top).coerceAtLeast(0f)
                if (padded.left > 0f && bandH > 0f) {
                    Spacer(
                        Modifier
                            .width(padded.left.toDp())
                            .height(bandH.toDp())
                            .offset { IntOffset(0, padded.top.roundToInt()) }
                            .then(consumeAll)
                    )
                }
                // Right strip (spotlight band)
                val rightW = (screenW - padded.right).coerceAtLeast(0f)
                if (rightW > 0f && bandH > 0f) {
                    Spacer(
                        Modifier
                            .width(rightW.toDp())
                            .height(bandH.toDp())
                            .offset { IntOffset(padded.right.roundToInt(), padded.top.roundToInt()) }
                            .then(consumeAll)
                    )
                }
            }
        }

        // ── OnSpotlightTap detector — transparent Box over the spotlight hole ──
        // Fires onAdvance() on DOWN without consuming, so the underlying composable
        // (inside Scaffold's subtree) also receives the touch and fires its own click.
        if (currentStep.advance == AdvanceCondition.OnSpotlightTap && spotlightBounds != null) {
            val padded = spotlightBounds.expand(SPOTLIGHT_PADDING)
            with(density) {
                Box(
                    Modifier
                        .size(padded.width.toDp(), padded.height.toDp())
                        .offset { IntOffset(padded.left.roundToInt(), padded.top.roundToInt()) }
                        .pointerInput(currentStep) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.changes.any { it.pressed && !it.previousPressed }) {
                                        onAdvance()
                                        // Do not consume — Scaffold's subtree receives the touch too.
                                    }
                                }
                            }
                        }
                )
            }
        }

        // ── Skip (X) icon — top-right corner ───────────────────────────────────
        IconButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Skip tutorial", tint = Color.White)
        }

        // ── Tooltip card ────────────────────────────────────────────────────────
        val cardAbove = spotlightBounds != null && spotlightBounds.center.y > screenH * 0.55f
        val cardAlignment = when {
            spotlightBounds == null -> Alignment.Center
            cardAbove -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }

        Column(
            modifier = Modifier
                .align(cardAlignment)
                .padding(horizontal = 24.dp, vertical = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (currentStepIndex + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(2.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentStep.message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(16.dp))

                    if (currentStep.advance == AdvanceCondition.Manual) {
                        Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) {
                            Text(currentStep.buttonLabel)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (!isLastStep) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Skip tutorial",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
