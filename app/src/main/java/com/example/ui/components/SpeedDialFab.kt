package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary

@Composable
fun SpeedDialFab(
    isOpen: Boolean,
    onToggle: () -> Unit,
    onAddProject: () -> Unit,
    onAddList: () -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fab_rotation"
    )

    Box(modifier = modifier) {
        // Barrier overlay to close on outside tap (pure transparent, NO darkening or tint)
        if (isOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    )
            )
        }

        // Action Column strictly pinned at BottomEnd (above system nav)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 20.dp)
        ) {
            // Speed Dial items expanding upwards directly above the bottom-right FAB
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                    targetOffsetY = { it / 3 },
                    animationSpec = tween(120)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    SpeedDialItem(
                        label = "New Project",
                        sublabel = "Top-level workspace",
                        icon = Icons.Default.Folder,
                        color = NeonPink,
                        testTag = "speed_dial_project",
                        onClick = {
                            onToggle()
                            onAddProject()
                        }
                    )
                    SpeedDialItem(
                        label = "New List",
                        sublabel = "Inside a project",
                        icon = Icons.Default.FormatListBulleted,
                        color = NeonBlue,
                        testTag = "speed_dial_list",
                        onClick = {
                            onToggle()
                            onAddList()
                        }
                    )
                    SpeedDialItem(
                        label = "New Task",
                        sublabel = "Add to a list",
                        icon = Icons.Default.CheckCircleOutline,
                        color = NeonPurple,
                        testTag = "speed_dial_task",
                        onClick = {
                            onToggle()
                            onAddTask()
                        }
                    )
                }
            }

            // Primary Glowing FAB (stays in the exact same spot at bottom-right, smoothly rotating)
            FloatingActionButton(
                onClick = onToggle,
                containerColor = NeonPink,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .shadow(16.dp, CircleShape, spotColor = NeonPink)
                    .testTag("main_speed_dial_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isOpen) "Close menu" else "Add item",
                    modifier = Modifier
                        .size(30.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    sublabel: String,
    icon: ImageVector,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = sublabel,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
