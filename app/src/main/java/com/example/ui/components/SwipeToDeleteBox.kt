package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

// Soft red: the same color is used for the outline, the icon and the "Delete" word.
private val DeleteRed = Color(0xFFD96C6C)

// The card has to be dragged this far (fraction of its width) before it is deleted: 3/5.
private const val DELETE_DISTANCE_FRACTION = 0.6f

/**
 * Swipe the content left or right to delete it. An outlined "Delete" strip shows behind the card;
 * only when the card is released after being dragged at least 60% of its width does it slide off
 * screen and [onDelete] run. Shorter drags, even fast flicks, spring back.
 *
 * [onSwipeActiveChanged] reports whether this row is being swiped, so the list can switch its
 * row animations on only while a deletion may be about to happen.
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

    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            currentOnDelete()
            // Normally the row disappears from the list and this effect is cancelled. If the delete
            // didn't go through, bring the card back instead of leaving an invisible task.
            delay(3_000)
            state.reset()
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
                        if (swiping) Modifier.border(BorderStroke(1.5.dp, DeleteRed), RoundedCornerShape(18.dp))
                        else Modifier
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
            ) {
                if (swiping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = DeleteRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Delete",
                            color = DeleteRed,
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
