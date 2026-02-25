package com.garemat.moonstone_companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.garemat.moonstone_companion.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    targetCoordinates: Map<String, LayoutCoordinates>,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onStepChange: (Int) -> Unit = {},
    navController: NavController? = null
) {
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps.getOrNull(currentStepIndex) ?: return
    
    val navBackStackEntry by navController?.currentBackStackEntryAsState() ?: remember { mutableStateOf(null) }
    val currentDestination = navBackStackEntry?.destination

    // Check if we need to navigate for this step
    LaunchedEffect(currentStep.requiredRoute, currentDestination) {
        val requiredRoute = currentStep.requiredRoute
        if (navController != null && requiredRoute != null) {
            val currentRoute = currentDestination?.route
            if (currentRoute != requiredRoute) {
                navController.navigate(requiredRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    LaunchedEffect(currentStepIndex) {
        onStepChange(currentStepIndex)
    }

    val targetCoords = targetCoordinates[currentStep.targetName]

    var dialogRectInOverlay by remember { mutableStateOf<Rect?>(null) }
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val configuration = LocalConfiguration.current
    val screenHeightPx = configuration.screenHeightDp * configuration.densityDpi / 160f

    Dialog(
        onDismissRequest = { /* Don't dismiss on outside tap */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayCoords = it }
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures { /* Do nothing, just consume */ }
                }
        ) {
            val isArrowless = currentStep.isArrowless
            val isAbilityLegend = currentStep.targetName == "AbilityLegend"

            // Check if target is actually visible and attached if not arrowless
            val isTargetAvailable = isArrowless || (targetCoords != null && targetCoords.isAttached)

            if (isTargetAvailable && overlayCoords != null) {

                val targetRect = if (!isArrowless && targetCoords != null && overlayCoords != null) {
                    val offsetInOverlay = targetCoords.positionInWindow() - overlayCoords!!.positionInWindow()
                    Rect(offsetInOverlay, targetCoords.size.toSize())
                } else null

                // Adaptive alignment for the dialog box
                val dialogAlignment = if (targetRect != null && targetRect.center.y > screenHeightPx * 0.5f) {
                    Alignment.TopCenter
                } else {
                    Alignment.BottomCenter
                }

                // Draw Arrow pointing to the target
                if (dialogRectInOverlay != null && targetRect != null) {
                    TutorialArrow(
                        fromRect = dialogRectInOverlay!!,
                        toRect = targetRect,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Text Box / Dialog
                Column(
                    modifier = Modifier
                        .align(if (isAbilityLegend) Alignment.Center else dialogAlignment)
                        .padding(horizontal = 24.dp, vertical = 64.dp)
                        .onGloballyPositioned {
                            if (overlayCoords != null) {
                                val pos = overlayCoords!!.localPositionOf(it, Offset.Zero)
                                dialogRectInOverlay = Rect(pos, it.size.toSize())
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { /* Do nothing here */ }
                        }
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isAbilityLegend) {
                        Text(
                            text = "Quick Glance Legend",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LegendRow(resId = R.drawable.piercing, label = "Piercing Damage Buff")
                        LegendRow(resId = R.drawable.impact, label = "Impact Damage Buff")
                        LegendRow(resId = R.drawable.slicing, label = "Slicing Damage Buff")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LegendRow(resId = R.drawable.piercing, label = "Reduce Piercing Damage", isDefensive = true)
                        LegendRow(resId = R.drawable.impact, label = "Reduce Impact Damage", isDefensive = true)
                        LegendRow(resId = R.drawable.slicing, label = "Reduce Slicing Damage", isDefensive = true)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LegendRow(resId = R.drawable.alldamagemitigation, label = "Reduce ALL Damage")
                    } else {
                        Text(
                            text = currentStep.text,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSkip) {
                            Text("Skip")
                        }

                        Row {
                            if (currentStepIndex > 0) {
                                IconButton(onClick = {
                                    currentStepIndex--
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                                }
                            }

                            Button(
                                onClick = {
                                    if (currentStepIndex < steps.size - 1) {
                                        currentStepIndex++
                                    } else {
                                        onComplete()
                                    }
                                }
                            ) {
                                if (currentStepIndex < steps.size - 1) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                                } else {
                                    Text("Finish")
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Yellow)
                }
            }

            IconButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
fun LegendRow(resId: Int, label: String, isDefensive: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            if (isDefensive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color.Red.copy(alpha = 0.7f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TutorialArrow(fromRect: Rect, toRect: Rect, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val arrowColor = Color.Yellow
        val strokeWidth = 4.dp.toPx()
        val start = getClosestPointOnRect(fromRect, toRect.center)
        val end = toRect.center

        val path = Path().apply {
            moveTo(start.x, start.y)
            val controlPoint = calculateControlPoint(start, end)
            quadraticTo(controlPoint.x, controlPoint.y, end.x, end.y)
        }

        drawPath(
            path = path,
            color = arrowColor,
            style = Stroke(width = strokeWidth)
        )

        drawArrowHead(start, end, arrowColor, strokeWidth)
    }
}

private fun getClosestPointOnRect(rect: Rect, point: Offset): Offset {
    val x = point.x.coerceIn(rect.left, rect.right)
    val y = point.y.coerceIn(rect.top, rect.bottom)
    return Offset(x, y)
}

private fun calculateControlPoint(start: Offset, end: Offset): Offset {
    val midX = (start.x + end.x) / 2
    val midY = (start.y + end.y) / 2
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val bowMagnitude = 60f
    val perpX = -dy / length * bowMagnitude
    val perpY = dx / length * bowMagnitude
    return Offset(midX + perpX, midY + perpY)
}

private fun DrawScope.drawArrowHead(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    val headSize = 40f
    val arrowAngle = Math.PI / 6
    val controlPoint = calculateControlPoint(start, end)
    val angle = atan2(end.y - controlPoint.y, end.x - controlPoint.x)

    val path = Path().apply {
        moveTo(
            end.x - headSize * cos(angle - arrowAngle).toFloat(),
            end.y - headSize * sin(angle - arrowAngle).toFloat()
        )
        lineTo(end.x, end.y)
        lineTo(
            end.x - headSize * cos(angle + arrowAngle).toFloat(),
            end.y - headSize * sin(angle + arrowAngle).toFloat()
        )
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth)
    )
}
