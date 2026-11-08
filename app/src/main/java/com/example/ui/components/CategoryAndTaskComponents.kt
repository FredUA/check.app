package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.data.model.Project
import com.example.data.model.TaskItem
import com.example.ui.theme.CardCompleted
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceCardLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CategoryCard(
    project: Project,
    taskCount: Int,
    completedCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(project.colorHex))
    } catch (e: Exception) {
        NeonPink
    }

    val progress = if (taskCount > 0) completedCount.toFloat() / taskCount.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier
            .width(185.dp)
            .height(118.dp)
            .shadow(
                elevation = if (isSelected) 14.dp else 6.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = if (isSelected) accentColor else Color.Black
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceCardLight else SurfaceCard
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, accentColor)
                 else androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$taskCount tasks",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (onEdit != null) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.2f))
                                .clickable(onClick = onEdit)
                                .testTag("project_edit_btn_${project.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Edit project",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = project.title,
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Glowing Bottom Accent Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1B2956))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0.12f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.85f))
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun AddProjectCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(185.dp)
            .height(118.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("add_project_card"),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCard
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceCardLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Project",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Add Project",
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TaskRowItem(
    task: TaskItem,
    accentColor: Color = NeonBlue,
    onToggleCompleted: () -> Unit,
    onClick: () -> Unit,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (task.isCompleted) CardCompleted else SurfaceCard,
        label = "task_bg"
    )

    val isRecentlyCreated = remember(task.id) {
        val now = System.currentTimeMillis()
        val createdMs = task.createdAt?.seconds?.let { it * 1000L } ?: now
        (now - createdMs) < 2500L
    }

    val offsetY = remember { Animatable(if (isRecentlyCreated) 56f else 0f) }
    val alphaAnim = remember { Animatable(if (isRecentlyCreated) 0.25f else 1f) }
    val scaleAnim = remember { Animatable(if (isRecentlyCreated) 0.92f else 1f) }

    LaunchedEffect(task.id) {
        if (isRecentlyCreated) {
            launch {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                alphaAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(400)
                )
            }
            launch {
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = (offsetY.value * density).roundToInt()) }
            .scale(scaleAnim.value)
            .alpha(alphaAnim.value)
            .shadow(
                elevation = if (task.isCompleted) 2.dp else 6.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = if (task.isCompleted) null else androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Neon Checkbox
            val checkmarkTint = if (accentColor.luminance() > 0.55f) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleCompleted)
                    .then(
                        if (task.isCompleted) {
                            Modifier.background(accentColor)
                        } else {
                            Modifier.border(2.5.dp, accentColor, CircleShape)
                        }
                    )
                    .testTag("task_checkbox_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = checkmarkTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Task Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    color = if (task.isCompleted) TextMuted else TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.notes,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onShare != null) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuickAddTaskRowItem(
    onQuickAdd: (title: String) -> Unit,
    accentColor: Color = NeonBlue,
    onActiveChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var taskTitle by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val isKeyboardOpen = imeBottom > 0.dp

    LaunchedEffect(isKeyboardOpen) {
        if (!isKeyboardOpen && isEditing) {
            isEditing = false
            taskTitle = ""
            focusManager.clearFocus()
            onActiveChanged?.invoke(false)
        }
    }

    val submitTask: () -> Unit = {
        val title = taskTitle.trim()
        if (title.isNotBlank()) {
            onQuickAdd(title)
            taskTitle = ""
            isEditing = true
            onActiveChanged?.invoke(true)
            coroutineScope.launch {
                delay(30)
                focusRequester.requestFocus()
                keyboardController?.show()
                delay(120)
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
            delay(150)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                enabled = !isEditing,
                onClick = {
                    isEditing = true
                    onActiveChanged?.invoke(true)
                }
            )
            .testTag("quick_add_task_item"),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plus icon: gray when inactive, list color when focused/active
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        if (isEditing) {
                            submitTask()
                        } else {
                            isEditing = true
                            onActiveChanged?.invoke(true)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add task",
                    tint = if (isEditing) accentColor else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (!isEditing) {
                Text(
                    text = "Add new task",
                    color = TextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            } else {
                BasicTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            submitTask()
                            coroutineScope.launch {
                                delay(30)
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        }
                    ),
                    cursorBrush = SolidColor(accentColor),
                    decorationBox = { innerTextField ->
                        if (taskTitle.isEmpty()) {
                            Text(
                                text = "Add new task...",
                                color = TextMuted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onActiveChanged?.invoke(true)
                            }
                        }
                        .testTag("quick_add_task_input")
                )

                // Confirm checkmark button (same color as the task list)
                if (taskTitle.isNotBlank()) {
                    IconButton(
                        onClick = submitTask,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("quick_add_confirm_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Close / cancel button
                IconButton(
                    onClick = {
                        taskTitle = ""
                        isEditing = false
                        onActiveChanged?.invoke(false)
                        keyboardController?.hide()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("quick_add_cancel_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

