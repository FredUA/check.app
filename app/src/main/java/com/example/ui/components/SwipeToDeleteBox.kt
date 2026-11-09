package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

// The app's own pink (same as the quick-add button and the Delete button in the task dialog),
// used for the outline, the icon and the text of the strip behind the card.
private val StripColor = NeonPink

// A finger movement counts as a swipe of the card only if it starts off no steeper than this, measured
// from the horizontal; anything steeper is left to the list, which scrolls. Measured on a real phone:
// deliberate swipes start at 0-21 degrees, one-handed scrolls at 38-90.
private const val MAX_SWIPE_ANGLE_DEGREES = 30.0

// The card is deleted when it is released after being dragged this far (fraction of its width) ...
private const val DELETE_DISTANCE_FRACTION = 0.40f

// ... or when it is flicked: released this fast, in the direction it was dragged, after at least this far.
private val FLICK_VELOCITY = 600.dp // per second
private val FLICK_MIN_DISTANCE = 40.dp

// After a swipe the card stays off screen for this long, showing "Undo", before the task is really deleted.
private const val UNDO_WINDOW_MS = 2_000L

// "Delete" takes this long to fade out, and "Undo" as long again to fade in.
private const val LABEL_FADE_MS = 200

// A swiped-away card travels this much further than its own width, so nothing of it is left on screen.
private val EXTRA_TRAVEL = 64.dp

/**
 * Swipe the content left or right to delete it. An outlined "Delete" strip shows behind the card.
 * The card slides off screen when it is released after being dragged at least 40% of its width, or
 * when it is flicked sideways; otherwise it springs back.
 *
 * The gesture is recognised here rather than by Material's SwipeToDismissBox, because that one takes any
 * sideways movement, so a slanted one-handed scroll kept dragging cards aside. Here the direction of the
 * first few pixels decides, once per gesture: close to horizontal is a swipe, anything else is a scroll.
 *
 * The task is not deleted straight away: for [UNDO_WINDOW_MS] the strip turns into "Undo". Tapping it
 * brings the card back in from the side it left; otherwise [onDelete] runs when the time is up.
 * Several rows can be waiting at the same time.
 *
 * [onSwipeActiveChanged] reports whether this row is being swiped or waiting, so the list can switch
 * its row animations on only while a deletion may be about to happen.
 */
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeActiveChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val extraTravelPx = with(density) { EXTRA_TRAVEL.toPx() }
    val flickVelocityPx = with(density) { FLICK_VELOCITY.toPx() }
    val flickMinDistancePx = with(density) { FLICK_MIN_DISTANCE.toPx() }
    // The "Delete" / "Undo" label keeps this distance from the strip's edge and from the card's edge.
    val labelEdgePaddingPx = with(density) { 24.dp.toPx() }

    var widthPx by remember { mutableFloatStateOf(0f) }
    var labelWidthPx by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    // Where the card is: 0 = in its place, negative = to the left, positive = to the right.
    val cardOffset = remember { Animatable(0f) }
    // The side the card was swiped off to (-1 left, 1 right) while the row waits for "Undo"; 0 otherwise.
    var dismissedSide by remember { mutableIntStateOf(0) }
    val waitingForUndo = dismissedSide != 0
    // While the card bounces back after "Undo" the strip behind it stays empty.
    var returningAfterUndo by remember { mutableStateOf(false) }
    val cardMoved by remember { derivedStateOf { cardOffset.value != 0f } }
    val stripVisible = (cardMoved || waitingForUndo) && !returningAfterUndo

    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismissedSide) {
        if (dismissedSide != 0) {
            // "Undo" sets dismissedSide back to 0, which cancels this wait.
            delay(UNDO_WINDOW_MS)
            currentOnDelete()
            // Normally the row disappears from the list and this effect is cancelled. If the delete
            // didn't go through, bring the card back instead of leaving an invisible task. (Launched
            // separately: changing dismissedSide restarts this effect, which would cut the animation.)
            delay(3_000)
            scope.launch { cardOffset.animateTo(0f, spring(visibilityThreshold = 1f)) }
            dismissedSide = 0
        }
    }

    // "Delete" fades out completely, and only then "Undo" fades in. The words are swapped while nothing
    // is visible, so the label re-centring for its new width is not seen.
    var labelShowsUndo by remember { mutableStateOf(false) }
    val labelAlpha = remember { Animatable(1f) }
    LaunchedEffect(waitingForUndo) {
        if (labelShowsUndo != waitingForUndo) {
            labelAlpha.animateTo(0f, tween(LABEL_FADE_MS))
            labelShowsUndo = waitingForUndo
        }
        labelAlpha.animateTo(1f, tween(LABEL_FADE_MS))
    }

    val currentSwipeCallback by rememberUpdatedState(onSwipeActiveChanged)
    LaunchedEffect(Unit) {
        snapshotFlow { cardOffset.value != 0f || dismissedSide != 0 }
            .collect { currentSwipeCallback(it) }
    }
    DisposableEffect(Unit) {
        onDispose { currentSwipeCallback(false) }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    // The touch is looked at on its way down to the card (initial pass): the card's own
                    // click handling consumes it afterwards, and a touch seen only after that would look
                    // taken. One that is consumed already here was taken by the list, to stop a fling.
                    val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
                    // While waiting for "Undo" there is no card to drag; taps go to the strip.
                    if (dismissedSide != 0) return@awaitEachGesture
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    // Until the finger has clearly moved, nothing is decided and nothing is consumed.
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        // The same touch again, now in the main pass and consumed by the card.
                        if (change.changedToDownIgnoreConsumed()) continue
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val moved = change.position - down.position
                        if (moved.getDistance() > slop) {
                            val degrees = Math.toDegrees(atan2(abs(moved.y), abs(moved.x)).toDouble())
                            // Too steep: a scroll. Left unconsumed, the list picks it up.
                            if (degrees > MAX_SWIPE_ANGLE_DEGREES) return@awaitEachGesture
                            change.consume()
                            break
                        }
                    }

                    // From here on the gesture is a swipe, whatever the finger does next.
                    var dragged = cardOffset.value
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        dragged += change.positionChange().x
                        change.consume()
                        val target = dragged
                        scope.launch { cardOffset.snapTo(target) }
                    }

                    val velocity = velocityTracker.calculateVelocity().x
                    val side = if (dragged < 0f) -1 else 1
                    val fast = abs(velocity) >= flickVelocityPx
                    val outwards = velocity * dragged > 0f
                    // Dragged far, unless it is being thrown back towards its place.
                    val farEnough = widthPx > 0f &&
                        abs(dragged) >= widthPx * DELETE_DISTANCE_FRACTION &&
                        !(fast && !outwards)
                    val flicked = fast && outwards && abs(dragged) >= flickMinDistancePx
                    scope.launch {
                        if (farEnough || flicked) {
                            // Carries on at the speed the finger left it with.
                            cardOffset.animateTo(
                                targetValue = side * (widthPx + extraTravelPx),
                                animationSpec = spring(stiffness = 500f, visibilityThreshold = 1f),
                                initialVelocity = velocity
                            )
                            dismissedSide = side
                        } else {
                            cardOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(visibilityThreshold = 1f),
                                initialVelocity = velocity
                            )
                        }
                    }
                }
            }
    ) {
        // The strip behind the card.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (stripVisible) Modifier.border(BorderStroke(1.5.dp, StripColor), RoundedCornerShape(18.dp))
                    else Modifier
                )
                .then(
                    if (waitingForUndo) {
                        Modifier
                            .clickable {
                                // The card comes back in from the side it left through, runs a little
                                // past its place and swings back: quick and tight, one small rebound.
                                dismissedSide = 0
                                returningAfterUndo = true
                                scope.launch {
                                    try {
                                        cardOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.6f,
                                                stiffness = 700f,
                                                visibilityThreshold = 1f
                                            )
                                        )
                                    } finally {
                                        returningAfterUndo = false
                                    }
                                }
                            }
                            .testTag("undo_delete_strip")
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            if (stripVisible) {
                Box(
                    modifier = Modifier
                        .onSizeChanged { labelWidthPx = it.width }
                        .offset {
                            // The label first sits by the edge the card uncovers. Once it is fully
                            // uncovered it travels along with the card's edge, until it reaches the
                            // middle of the strip, where it stays.
                            val swiped = cardOffset.value
                            val centred = max(labelEdgePaddingPx, (widthPx - labelWidthPx) / 2f)
                            val inset = (abs(swiped) - labelEdgePaddingPx - labelWidthPx)
                                .coerceIn(labelEdgePaddingPx, centred)
                            val x = if (swiped >= 0f) inset else widthPx - labelWidthPx - inset
                            IntOffset(x.roundToInt(), 0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.graphicsLayer { alpha = labelAlpha.value },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Undo" is text only.
                        if (!labelShowsUndo) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = StripColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = if (labelShowsUndo) "Undo" else "Delete",
                            color = StripColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.graphicsLayer { translationX = cardOffset.value }) {
            content()
        }
    }
}
