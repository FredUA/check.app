package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val DeleteRed = Color(0xFFEF4444)

/**
 * Swipe the content left or right to delete it: a red "Delete" strip shows behind the card and,
 * once the card is dragged past the threshold, it slides off screen and [onDelete] is called.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { true },
        // Dragging 40% of the card's width is enough to delete it.
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )

    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
            // Normally the row disappears from the list and this effect is cancelled. If the delete
            // didn't go through, bring the card back instead of leaving an invisible task.
            delay(3_000)
            state.reset()
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val direction = state.dismissDirection
            val swiping = direction != SwipeToDismissBoxValue.Settled
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (swiping) DeleteRed else Color.Transparent)
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
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(0.dp))
                        Text(
                            text = "Delete",
                            color = Color.White,
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
