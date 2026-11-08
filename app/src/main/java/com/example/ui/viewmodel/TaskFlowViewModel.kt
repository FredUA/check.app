package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TaskFlowRepository
import com.example.data.model.Project
import com.example.data.model.Subtask
import com.example.data.model.TaskItem
import com.example.data.model.TaskList
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "TaskFlowViewModel"
private const val LOCAL_CHANGE_WINDOW_MS = 15_000L

enum class SortOption(val displayName: String) {
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    TYPE("By Category/Type")
}

data class AppNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Something changed in a shared project because of someone else (another device or a collaborator).
 * [key] identifies the thing that changed, so a newer notification about it replaces the older one.
 */
data class RemoteEvent(
    val key: String,
    val trayTitle: String,
    val trayText: String
)

class TaskFlowViewModel(
    private val repository: TaskFlowRepository
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedProjects: StateFlow<List<Project>> = repository.observeSharedProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLists: StateFlow<List<TaskList>> = repository.observeLists(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedLists: StateFlow<List<TaskList>> = repository.observeSharedLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<TaskItem>> = repository.observeTasks(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedTasks: StateFlow<List<TaskItem>> = repository.observeSharedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_OLDEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _floatingBanner = MutableStateFlow<String?>(null)
    val floatingBanner: StateFlow<String?> = _floatingBanner.asStateFlow()

    val notificationMessage: StateFlow<String?> = _floatingBanner.asStateFlow()

    fun postNotification(message: String) {
        val item = AppNotification(message = message)
        _notifications.value = (listOf(item) + _notifications.value).take(20)
        _floatingBanner.value = message
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            if (_floatingBanner.value == message) {
                _floatingBanner.value = null
            }
        }
    }

    /** Notifications about tasks/lists only make sense for projects shared with somebody. */
    private fun postNotificationIfShared(message: String, vararg projectIds: String) {
        if (projectIds.any { it in sharedProjectIds }) postNotification(message)
    }

    // --- Notifications for changes made on other devices / by collaborators ---

    // Completion changes this device made itself: taskId -> (expected isCompleted, mark time).
    // They already post their own notification, so the snapshot diff below must skip them.
    private val localCompletionChanges = mutableMapOf<String, Pair<Boolean, Long>>()
    private var previousTaskStates: Map<String, TaskItem>? = null

    // Fired when a task was marked completed by another device / collaborator, so the UI can play the sound.
    private val _remoteTaskCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val remoteTaskCompleted: SharedFlow<Unit> = _remoteTaskCompleted.asSharedFlow()

    // One event per remote change in a shared project, used for system tray notifications.
    private val _remoteEvents = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 16)
    val remoteEvents: SharedFlow<RemoteEvent> = _remoteEvents.asSharedFlow()

    // Only shared projects produce notifications: ones I own and shared with somebody,
    // and ones somebody else shared with me. Updated by the watchers started in init.
    private var sharedProjectIds: Set<String> = emptySet()

    // Ids of projects shared with me as of the previous snapshot (null until the first one arrives).
    private var knownSharedWithMeIds: Set<String>? = null

    private fun markLocalCompletionChange(taskId: String, expectedCompleted: Boolean) {
        localCompletionChanges[taskId] = expectedCompleted to System.currentTimeMillis()
    }

    private fun consumeLocalCompletionChange(taskId: String, nowCompleted: Boolean): Boolean {
        val (expected, markedAt) = localCompletionChanges[taskId] ?: return false
        val fresh = System.currentTimeMillis() - markedAt < LOCAL_CHANGE_WINDOW_MS
        if (fresh && expected == nowCompleted) {
            localCompletionChanges.remove(taskId)
            return true
        }
        if (!fresh) localCompletionChanges.remove(taskId)
        return false
    }

    private fun announceRemoteCompletionChanges(tasks: List<TaskItem>) {
        val current = tasks.associateBy { it.id }
        val previous = previousTaskStates
        previousTaskStates = current
        // First snapshot is only the baseline.
        if (previous == null) return
        val remoteChanges = mutableListOf<TaskItem>()
        for ((id, task) in current) {
            val before = previous[id] ?: continue
            if (before.isCompleted == task.isCompleted) continue
            if (consumeLocalCompletionChange(id, task.isCompleted)) continue
            // Not shared with anyone: nothing to tell the user about.
            if (task.projectId !in sharedProjectIds) continue
            remoteChanges.add(task)
        }
        if (remoteChanges.isEmpty()) return
        // One sound per update, even if several tasks changed at once; only for completion, not reopen.
        if (remoteChanges.any { it.isCompleted }) _remoteTaskCompleted.tryEmit(Unit)
        viewModelScope.launch {
            for (task in remoteChanges) {
                val who = if (task.lastUpdatedBy.isBlank()) "Someone"
                else repository.resolveDisplayName(task.lastUpdatedBy)
                postNotification(
                    if (task.isCompleted) "✓ $who completed: '${task.title}'"
                    else "$who reopened: '${task.title}'"
                )
                _remoteEvents.emit(
                    RemoteEvent(
                        key = "task:${task.id}",
                        trayTitle = if (task.isCompleted) "Task completed" else "Task reopened",
                        trayText = if (task.isCompleted) "$who completed '${task.title}'"
                        else "$who reopened '${task.title}'"
                    )
                )
            }
        }
    }

    /** Tells the user when somebody shares a project with them. */
    private fun announceNewlySharedProjects(sharedWithMe: List<Project>) {
        val currentIds = sharedWithMe.map { it.id }.toSet()
        val previous = knownSharedWithMeIds
        knownSharedWithMeIds = currentIds
        // First snapshot is only the baseline.
        if (previous == null) return
        val added = sharedWithMe.filter { it.id !in previous }
        if (added.isEmpty()) return
        viewModelScope.launch {
            for (project in added) {
                val who = if (project.ownerEmail.isBlank()) "Someone"
                else repository.resolveDisplayName(project.ownerEmail)
                val title = project.displayTitle.ifBlank { "Untitled project" }
                postNotification("$who shared project '$title' with you")
                _remoteEvents.emit(
                    RemoteEvent(
                        key = "project-shared:${project.id}",
                        trayTitle = "Project shared with you",
                        trayText = "$who shared '$title' with you"
                    )
                )
            }
        }
    }

    init {
        // Which projects count as shared (owned + shared with somebody, or shared with me)
        viewModelScope.launch {
            try {
                combine(projects, sharedProjects) { owned, sharedWithMe ->
                    owned.filter { it.memberEmails.isNotEmpty() }.map { it.id }.toSet() +
                        sharedWithMe.map { it.id }
                }.collect { sharedProjectIds = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Shared project watcher stopped", e)
            }
        }
        // Task completed / reopened elsewhere
        viewModelScope.launch {
            try {
                allTasks.collect { announceRemoteCompletionChanges(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Remote task change watcher stopped", e)
            }
        }
        // Somebody shared a project with me. Uses its own cold flow (not the StateFlow above) so the
        // initial empty placeholder value is never mistaken for "no projects shared yet".
        viewModelScope.launch {
            try {
                repository.observeSharedProjects().collect { announceNewlySharedProjects(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Project share watcher stopped", e)
            }
        }
    }

    fun dismissFloatingBanner() {
        _floatingBanner.value = null
    }

    fun clearNotification() {
        _floatingBanner.value = null
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
        _floatingBanner.value = null
    }

    fun selectProject(projectId: String?) {
        _selectedProjectId.value = if (_selectedProjectId.value == projectId) null else projectId
        _selectedListId.value = null
    }

    fun selectList(listId: String?) {
        _selectedListId.value = if (_selectedListId.value == listId) null else listId
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // CRUD - Project

    fun createProject(title: String, description: String = "", colorHex: String = "#FF2A85", onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val id = repository.createProject(title, description, colorHex)
                onCreated(id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create project", e)
            }
        }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch {
            try {
                repository.updateProject(project)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update project", e)
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            try {
                repository.deleteProject(projectId)
                if (_selectedProjectId.value == projectId) {
                    _selectedProjectId.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete project", e)
            }
        }
    }

    // CRUD - List

    fun createList(projectId: String, title: String, colorHex: String = "#00D2FF", onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val id = repository.createList(projectId, title, colorHex)
                onCreated(id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create list", e)
            }
        }
    }

    fun updateList(list: TaskList) {
        viewModelScope.launch {
            try {
                repository.updateList(list)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update list", e)
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            try {
                val targetList = allLists.value.firstOrNull { it.id == listId }
                    ?: sharedLists.value.firstOrNull { it.id == listId }
                val projectId = targetList?.projectId.orEmpty()
                repository.deleteList(listId, projectId)
                if (_selectedListId.value == listId) {
                    _selectedListId.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete list", e)
            }
        }
    }

    // CRUD - Task

    fun createTask(listId: String, projectId: String, title: String, notes: String = "", onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val id = repository.createTask(listId, projectId, title, notes)
                onCreated(id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create task", e)
            }
        }
    }

    fun updateTask(task: TaskItem) {
        viewModelScope.launch {
            val existing = allTasks.value.firstOrNull { it.id == task.id }
            if (existing != null && existing.isCompleted != task.isCompleted) {
                markLocalCompletionChange(task.id, task.isCompleted)
            }
            try {
                repository.updateTask(task)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update task", e)
            }
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            // Mark before writing: Firestore delivers the local snapshot before the write completes.
            markLocalCompletionChange(task.id, !task.isCompleted)
            try {
                val nowCompleted = repository.toggleTaskCompletion(task)
                val currentEmail = repository.currentUserEmail()
                val collaborators = task.memberEmails.filter { it.isNotBlank() && !it.equals(currentEmail, ignoreCase = true) }
                val msg = if (nowCompleted) {
                    if (collaborators.isNotEmpty()) "✓ Completed shared task: '${task.title}'"
                    else "✓ Completed: '${task.title}'"
                } else {
                    "Reopened task: '${task.title}'"
                }
                postNotificationIfShared(msg, task.projectId)
            } catch (e: Exception) {
                localCompletionChanges.remove(task.id)
                Log.e(TAG, "Failed to toggle task completion", e)
            }
        }
    }

    fun addSubtask(task: TaskItem, title: String) {
        viewModelScope.launch {
            try {
                repository.addSubtask(task, title)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add subtask", e)
            }
        }
    }

    fun toggleSubtask(task: TaskItem, subtaskId: String) {
        viewModelScope.launch {
            try {
                repository.toggleSubtaskCompletion(task, subtaskId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle subtask", e)
            }
        }
    }

    fun deleteSubtask(task: TaskItem, subtaskId: String) {
        viewModelScope.launch {
            try {
                repository.deleteSubtask(task, subtaskId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete subtask", e)
            }
        }
    }

    fun promoteSubtaskToTask(task: TaskItem, subtask: Subtask) {
        viewModelScope.launch {
            try {
                repository.promoteSubtaskToTask(task, subtask)
                postNotificationIfShared("Promoted subtask '${subtask.title}' to a task!", task.projectId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to promote subtask", e)
            }
        }
    }

    fun reassignTask(task: TaskItem, targetListId: String, targetProjectId: String) {
        viewModelScope.launch {
            try {
                repository.reassignTaskToList(task, targetListId, targetProjectId)
                postNotificationIfShared("Task moved to selected list", task.projectId, targetProjectId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reassign task", e)
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                val targetTask = allTasks.value.firstOrNull { it.id == taskId }
                    ?: sharedTasks.value.firstOrNull { it.id == taskId }
                val projectId = targetTask?.projectId.orEmpty()
                val listId = targetTask?.listId.orEmpty()
                repository.deleteTask(taskId, listId, projectId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete task", e)
            }
        }
    }

    // Sharing

    fun shareProject(projectId: String, email: String) {
        viewModelScope.launch {
            try {
                repository.shareProject(projectId, email)
                postNotification("Shared project with $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share project", e)
            }
        }
    }

    fun shareList(listId: String, email: String) {
        viewModelScope.launch {
            try {
                repository.shareList(listId, email)
                postNotification("Shared list with $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share list", e)
            }
        }
    }

    fun shareTask(taskId: String, email: String) {
        viewModelScope.launch {
            try {
                repository.shareTask(taskId, email)
                postNotification("Shared task with $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share task", e)
            }
        }
    }

    fun unshareProject(projectId: String, email: String) {
        viewModelScope.launch {
            try {
                repository.unshareProject(projectId, email)
                postNotification("Removed access for $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unshare project", e)
            }
        }
    }

    fun unshareList(listId: String, email: String) {
        viewModelScope.launch {
            try {
                repository.unshareList(listId, email)
                postNotification("Removed access for $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unshare list", e)
            }
        }
    }

    fun unshareTask(taskId: String, email: String) {
        viewModelScope.launch {
            try {
                repository.unshareTask(taskId, email)
                postNotification("Removed access for $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unshare task", e)
            }
        }
    }
}
