package io.github.garemat.lunachron.ui

import androidx.compose.foundation.gestures.awaitEachGesture
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
 * Non-blocking tutorial overlay. Sits above the NavHost in a root [Box] (NOT a Dialog) so that
 * touches within the spotlight hole pass through to the real UI beneath.
 *
 * Touch model:
 * - Touches on the tooltip card or skip button are always allowed (tracked as exclusion zones).
 * - Touches within the spotlight are allowed (and trigger [onAdvance] for [AdvanceCondition.OnSpotlightTap]).
 * - All other touches are blocked so the user stays focused on the current step.
 * - Arrowless steps (no spotlight) never block any touches.
 *
 * Scalability contract: adding a new step only requires:
 *  1. Appending a [TutorialStep] to [appTutorialSteps].
 *  2. Adding `Modifier.onGloballyPositioned { onTargetPositioned("<tag>", it) }` at the target site
 *     if the tag is new.
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

    // Coordinate tracking — overlay root, tooltip card, skip button.
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var tooltipBounds by remember { mutableStateOf<Rect?>(null) }
    var skipBounds by remember { mutableStateOf<Rect?>(null) }

    // Spotlight bounds derived from the registered target coordinates.
    val targetCoords = currentStep.targetTag?.let { tutorialCoords[it] }
    val spotlightBounds: Rect? = remember(targetCoords, overlayCoords) {
        val tc = targetCoords ?: return@remember null
        val oc = overlayCoords ?: return@remember null
        if (!tc.isAttached) return@remember null
        tc.boundsRelativeTo(oc)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayCoords = it }
            .pointerInput(spotlightBounds, tooltipBounds, skipBounds, currentStep) {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pos = event.changes.firstOrNull()?.position ?: return@awaitEachGesture

                    // Arrowless steps have no spotlight — never block.
                    if (spotlightBounds == null) return@awaitEachGesture

                    // OnNavigation steps rely on the user freely tapping nav items that live
                    // outside the NavHost (bottom bar, drawer). Blocking here would prevent those
                    // taps from reaching sibling composables, so allow everything through.
                    if (currentStep.advance is AdvanceCondition.OnNavigation) return@awaitEachGesture

                    val inSpotlight = spotlightBounds.expand(SPOTLIGHT_PADDING).contains(pos)
                    val inTooltip = tooltipBounds?.contains(pos) == true
                    val inSkip = skipBounds?.contains(pos) == true

                    when {
                        inTooltip || inSkip -> { /* always allow — tooltip buttons and skip must work */ }
                        inSpotlight -> {
                            // Pass through to real UI; additionally advance on tap if required.
                            if (currentStep.advance == AdvanceCondition.OnSpotlightTap) {
                                val isDown = event.changes.any { it.pressed && !it.previousPressed }
                                if (isDown) onAdvance()
                            }
                        }
                        else -> event.changes.forEach { it.consume() }
                    }
                }
            }
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

        // ── Skip (X) icon — top-right corner ───────────────────────────────────
        IconButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .onGloballyPositioned { coords ->
                    overlayCoords?.let { skipBounds = coords.boundsRelativeTo(it) }
                }
        ) {
            Icon(Icons.Default.Close, contentDescription = "Skip tutorial", tint = Color.White)
        }

        // ── Tooltip card ────────────────────────────────────────────────────────
        val screenHeightPx = LocalConfiguration.current.screenHeightDp * LocalDensity.current.density
        val cardAbove = spotlightBounds != null && spotlightBounds.center.y > screenHeightPx * 0.55f
        val cardAlignment = when {
            spotlightBounds == null -> Alignment.Center
            cardAbove -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }

        Column(
            modifier = Modifier
                .align(cardAlignment)
                .padding(horizontal = 24.dp, vertical = 72.dp)
                .onGloballyPositioned { coords ->
                    overlayCoords?.let { tooltipBounds = coords.boundsRelativeTo(it) }
                },
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

                    // Skip option — visible on all steps except the final one.
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
