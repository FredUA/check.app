package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.data.model.Project
import com.example.data.model.TaskItem
import com.example.data.model.TaskList
import com.example.ui.components.CollaboratorRowItem
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceCardLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailDialog(
    task: TaskItem,
    allLists: List<TaskList>,
    allProjects: List<Project>,
    onDismiss: () -> Unit,
    onToggleCompleted: () -> Unit,
    onUpdateTask: (TaskItem) -> Unit,
    // Receives the task including any unsaved title/notes edits, so moving it doesn't lose them.
    onReassignList: (editedTask: TaskItem, targetListId: String, targetProjectId: String) -> Unit,
    onDeleteTask: () -> Unit,
    onShareTask: (() -> Unit)? = null,
    onUnshareTask: ((String) -> Unit)? = null
) {
    // Edits stay local while typing and are saved once, when the dialog closes (Done / back / outside tap).
    // Keyed by id so a change arriving from another device doesn't wipe what is being typed.
    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.notes) }
    val initialTitle = remember(task.id) { task.title }
    val initialNotes = remember(task.id) { task.notes }

    // Built from the live task, so completion toggled in the dialog isn't overwritten with a stale value.
    // A blank title is not allowed and keeps the previous one.
    fun editedTask(): TaskItem = task.copy(title = title.trim().ifBlank { initialTitle }, notes = notes)

    fun hasEdits(): Boolean =
        title.trim().ifBlank { initialTitle }.trim() != initialTitle.trim() ||
            notes.trim() != initialNotes.trim()

    val saveAndDismiss = {
        if (hasEdits()) onUpdateTask(editedTask())
        onDismiss()
    }

    val currentList = allLists.firstOrNull { it.id == task.listId }
    val currentProject = allProjects.firstOrNull { it.id == task.projectId }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var candidateReassignList by remember { mutableStateOf<TaskList?>(null) }
    var showReassignConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = SurfaceCard,
            title = { Text("Delete Task?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${task.title}'?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteTask()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = saveAndDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(26.dp)),
            color = SurfaceCard,
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Top Header: List info & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ASSIGNED TO LIST",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Exposed dropdown to change list
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceCardLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .clickable { dropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = currentList?.title ?: "Select List",
                                        color = NeonBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                }
                            }

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(SurfaceCardLight)
                            ) {
                                allLists.forEach { targetList ->
                                    val parentProj = allProjects.firstOrNull { it.id == targetList.projectId }
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(targetList.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                if (parentProj != null) {
                                                    Text("Project: ${parentProj.title}", color = TextSecondary, fontSize = 11.sp)
                                                }
                                            }
                                        },
                                        onClick = {
                                            dropdownExpanded = false
                                            if (targetList.id != task.listId) {
                                                candidateReassignList = targetList
                                                showReassignConfirmation = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SurfaceCardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Task Title & Completion status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleCompleted)
                            .then(
                                if (task.isCompleted) Modifier.background(NeonPink)
                                else Modifier.border(2.5.dp, NeonBlue, CircleShape)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonPink,
                            unfocusedBorderColor = SurfaceCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("edit_task_title")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notes / Description
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Description & Notes (Optional)") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonPink,
                        unfocusedBorderColor = SurfaceCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_task_notes")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Buttons: Left is Delete, Right is Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("delete_task_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Button(
                        onClick = saveAndDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("done_task_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardLight),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Done", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    // Confirmation Dialog when reassigning task to another list
    if (showReassignConfirmation && candidateReassignList != null) {
        val target = candidateReassignList!!
        AlertDialog(
            onDismissRequest = {
                showReassignConfirmation = false
                candidateReassignList = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = SurfaceCard,
            title = {
                Text("Move Task to Another List?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Do you want to move task '${task.title}' to list '${target.title}'?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReassignList(editedTask(), target.id, target.projectId)
                        showReassignConfirmation = false
                        candidateReassignList = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                    modifier = Modifier.testTag("confirm_reassign_button")
                ) {
                    Text("Move", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReassignConfirmation = false
                        candidateReassignList = null
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
