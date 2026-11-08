package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// The app's own pink (same as the quick-add button and the Delete button in the task dialog),
// used for the outline, the icon and the text of the strip behind the card.
private val StripColor = NeonPink

// The card has to be dragged this far (fraction of its width) before it is deleted.
private const val DELETE_DISTANCE_FRACTION = 0.55f

// After a swipe the card stays off screen for this long, showing "Undo", before the task is really deleted.
private const val UNDO_WINDOW_MS = 2_000L

/**
 * Swipe the content left or right to delete it. An outlined "Delete" strip shows behind the card;
 * only when the card is released after being dragged at least 55% of its width does it slide off
 * screen. Shorter drags, even fast flicks, spring back.
 *
 * The task is not deleted straight away: for [UNDO_WINDOW_MS] the strip turns into "Undo". Tapping it
 * slides the card back in from the side it left; otherwise [onDelete] runs when the time is up.
 * Several rows can be waiting at the same time.
 *
 * [onSwipeActiveChanged] reports whether this row is being swiped or waiting, so the list can switch
 * its row animations on only while a deletion may be about to happen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeActiveChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    // The confirm callback below is passed to the state's own constructor, so it reaches the state through this holder.
    val stateHolder = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.Settled) {
                true
            } else {
                // Material3 also dismisses on a quick flick regardless of distance; require the real distance.
                val offset = stateHolder[0]?.let { runCatching { it.requireOffset() }.getOrNull() } ?: 0f
                widthPx > 0f && abs(offset) >= widthPx * DELETE_DISTANCE_FRACTION
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * DELETE_DISTANCE_FRACTION }
    )
    stateHolder[0] = state

    val scope = rememberCoroutineScope()
    val waitingForUndo = state.currentValue != SwipeToDismissBoxValue.Settled
    var undone by remember { mutableStateOf(false) }

    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.Settled) {
            undone = false
        } else {
            delay(UNDO_WINDOW_MS)
            if (!undone) {
                currentOnDelete()
                // Normally the row disappears from the list and this effect is cancelled. If the delete
                // didn't go through, bring the card back instead of leaving an invisible task.
                delay(3_000)
                state.reset()
            }
        }
    }

    val currentSwipeCallback by rememberUpdatedState(onSwipeActiveChanged)
    LaunchedEffect(state) {
        snapshotFlow { state.dismissDirection != SwipeToDismissBoxValue.Settled }
            .collect { currentSwipeCallback(it) }
    }
    DisposableEffect(Unit) {
        onDispose { currentSwipeCallback(false) }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { widthPx = it.width.toFloat() },
        backgroundContent = {
            val direction = state.dismissDirection
            val swiping = direction != SwipeToDismissBoxValue.Settled
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (swiping) Modifier.border(BorderStroke(1.5.dp, StripColor), RoundedCornerShape(18.dp))
                        else Modifier
                    )
                    .then(
                        if (waitingForUndo) {
                            Modifier
                                .clickable {
                                    undone = true
                                    // The card slides back in from the side it left through.
                                    scope.launch { state.reset() }
                                }
                                .testTag("undo_delete_strip")
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = when {
                    waitingForUndo -> Alignment.Center
                    direction == SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                }
            ) {
                if (swiping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (waitingForUndo) Icons.AutoMirrored.Filled.Undo else Icons.Default.Delete,
                            contentDescription = null,
                            tint = StripColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (waitingForUndo) "Undo" else "Delete",
                            color = StripColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        content = { content() }
    )
}
