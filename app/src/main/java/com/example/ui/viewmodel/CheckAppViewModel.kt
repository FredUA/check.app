package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CheckAppRepository
import com.example.data.model.Project
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "CheckAppViewModel"

// A list not seen before counts as "just created" if it was created within this long (server time).
private const val NEW_LIST_WINDOW_MS = 120_000L

// How long list deletions are held back, to tell "project deleted" from "lists deleted one by one".
private const val LIST_DELETION_SETTLE_MS = 2_000L

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

class CheckAppViewModel(
    private val repository: CheckAppRepository
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

    // Previous snapshot of all lists and the tasks in them (null until the first snapshot arrives).
    private var previousLists: Map<String, TaskList>? = null
    private var previousTasks: Map<String, TaskInList>? = null

    private class TaskInList(val task: TaskItem, val listId: String)

    private enum class TaskChangeKind { ADDED, EDITED, DELETED, COMPLETED, REOPENED }

    private class TaskChange(val kind: TaskChangeKind, val task: TaskItem, val byEmail: String)

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

    /**
     * Whether a difference found in [list] is worth telling the user about: it must come from
     * someone else, not from this device (pending writes) or from a stale cache catching up with
     * the server, the list must already have been known, and its project must be shared.
     */
    private fun isRemoteChange(list: TaskList, previousList: TaskList?): Boolean =
        previousList != null &&
            !list.hasPendingWrites &&
            !previousList.fromCache &&
            list.projectId in sharedProjectIds

    /** Compares the new snapshot of all lists with the previous one and announces others' task changes. */
    private fun announceRemoteTaskChanges(lists: List<TaskList>) {
        val currentLists = lists.associateBy { it.id }
        val currentTasks = HashMap<String, TaskInList>()
        for (list in lists) {
            for (task in list.parseTasks()) currentTasks[task.id] = TaskInList(task, list.id)
        }
        val prevLists = previousLists
        val prevTasks = previousTasks
        previousLists = currentLists
        previousTasks = currentTasks
        // First snapshot is only the baseline. Demo mode edits are all local.
        if (prevLists == null || prevTasks == null) return
        if (com.example.demo.DemoMode.isDemoActive.value) return

        announceRemoteListChanges(currentLists, prevLists)

        val changes = mutableListOf<TaskChange>()

        for ((id, now) in currentTasks) {
            val list = currentLists[now.listId] ?: continue
            if (!isRemoteChange(list, prevLists[list.id])) continue
            val by = now.task.lastUpdatedBy.ifBlank { list.lastUpdatedBy }
            val before = prevTasks[id]
            if (before == null) {
                changes.add(TaskChange(TaskChangeKind.ADDED, now.task, by))
            } else if (before.listId == now.listId) {
                // (A task that moved to another list is ignored here.)
                if (before.task.isCompleted != now.task.isCompleted) {
                    val kind = if (now.task.isCompleted) TaskChangeKind.COMPLETED else TaskChangeKind.REOPENED
                    changes.add(TaskChange(kind, now.task, by))
                }
                if (before.task.title.trim() != now.task.title.trim() ||
                    before.task.notes.trim() != now.task.notes.trim()
                ) {
                    changes.add(TaskChange(TaskChangeKind.EDITED, now.task, by))
                }
            }
        }

        for ((id, before) in prevTasks) {
            if (id in currentTasks) continue
            // If the list itself is gone (deleted, or access removed) this isn't a task deletion.
            val list = currentLists[before.listId] ?: continue
            if (!isRemoteChange(list, prevLists[list.id])) continue
            // The deleted task can't say who deleted it; the list records the last editor.
            changes.add(TaskChange(TaskChangeKind.DELETED, before.task, list.lastUpdatedBy))
        }

        if (changes.isEmpty()) return
        // One sound per update, even if several tasks changed at once; only for completion, not reopen.
        if (changes.any { it.kind == TaskChangeKind.COMPLETED }) _remoteTaskCompleted.tryEmit(Unit)
        viewModelScope.launch {
            for (change in changes) {
                val who = if (change.byEmail.isBlank()) "Someone"
                else repository.resolveDisplayName(change.byEmail)
                val title = change.task.title
                val (banner, trayTitle, trayText) = when (change.kind) {
                    TaskChangeKind.COMPLETED -> Triple(
                        "✓ $who completed: '$title'", "Task completed", "$who completed '$title'"
                    )
                    TaskChangeKind.REOPENED -> Triple(
                        "$who reopened: '$title'", "Task reopened", "$who reopened '$title'"
                    )
                    TaskChangeKind.ADDED -> Triple(
                        "$who added task: '$title'", "Task added", "$who added '$title'"
                    )
                    TaskChangeKind.EDITED -> Triple(
                        "$who edited task: '$title'", "Task edited", "$who edited '$title'"
                    )
                    TaskChangeKind.DELETED -> Triple(
                        "$who deleted task: '$title'", "Task deleted", "$who deleted '$title'"
                    )
                }
                postNotification(banner)
                _remoteEvents.emit(RemoteEvent(key = "task:${change.task.id}", trayTitle = trayTitle, trayText = trayText))
            }
        }
    }

    // --- Lists and projects ---

    // Deleted on this device: their disappearance must not be announced as someone else's doing.
    // (A deleted document can't carry a "pending writes" flag the way edited ones do.)
    private val locallyDeletedListIds = mutableSetOf<String>()
    private val locallyDeletedProjectIds = mutableSetOf<String>()

    // List deletions are collected for a moment: deleting a whole project removes its lists one by one
    // right before the project itself, and that should read as one "project deleted", not N list deletions.
    private val pendingListDeletions = mutableListOf<TaskList>()
    private var listDeletionFlushJob: kotlinx.coroutines.Job? = null

    private var previousAccessibleProjects: Map<String, Project>? = null

    /** A notification to show; "{who}" in the texts is replaced with the name of [byEmail]. */
    private class Announcement(
        val key: String,
        val byEmail: String,
        val banner: String,
        val trayTitle: String,
        val trayText: String
    )

    private suspend fun deliver(a: Announcement) {
        val who = if (a.byEmail.isBlank()) "Someone" else repository.resolveDisplayName(a.byEmail)
        postNotification(a.banner.replace("{who}", who))
        _remoteEvents.emit(RemoteEvent(a.key, a.trayTitle, a.trayText.replace("{who}", who)))
    }

    private fun announceRemoteListChanges(currentLists: Map<String, TaskList>, prevLists: Map<String, TaskList>) {
        val now = System.currentTimeMillis()
        val announcements = mutableListOf<Announcement>()

        for ((id, list) in currentLists) {
            if (list.projectId !in sharedProjectIds) continue
            val before = prevLists[id]
            val title = list.displayTitle
            if (before == null) {
                // A list we haven't seen before. Only count it as new if it was created just now: lists of a
                // project that was just shared with me, or that reappear after a reload, are old.
                val createdMs = list.createdAt?.toDate()?.time ?: continue
                if (list.hasPendingWrites || now - createdMs !in -30_000L..NEW_LIST_WINDOW_MS) continue
                announcements.add(
                    Announcement("list:$id", list.lastUpdatedBy, "{who} added list: '$title'", "List added", "{who} added '$title'")
                )
            } else if (isRemoteChange(list, before) && title != before.displayTitle) {
                announcements.add(
                    Announcement(
                        "list:$id", list.lastUpdatedBy,
                        "{who} renamed list: '${before.displayTitle}' → '$title'",
                        "List renamed", "{who} renamed '${before.displayTitle}' to '$title'"
                    )
                )
            }
        }

        var deletionsQueued = false
        for ((id, before) in prevLists) {
            if (id in currentLists) continue
            when {
                id in locallyDeletedListIds -> continue
                before.projectId in locallyDeletedProjectIds -> continue
                // Stale cache catching up with the server: not something that just happened.
                before.fromCache -> continue
                before.projectId !in sharedProjectIds -> continue
            }
            pendingListDeletions.add(before)
            deletionsQueued = true
        }

        if (announcements.isNotEmpty()) {
            viewModelScope.launch { announcements.forEach { deliver(it) } }
        }
        if (deletionsQueued) {
            listDeletionFlushJob?.cancel()
            listDeletionFlushJob = viewModelScope.launch {
                kotlinx.coroutines.delay(LIST_DELETION_SETTLE_MS)
                flushListDeletions()
            }
        }
    }

    private suspend fun flushListDeletions() {
        val batch = pendingListDeletions.toList()
        pendingListDeletions.clear()
        val projectsNow = (projects.value + sharedProjects.value).associateBy { it.id }
        for (list in batch) {
            // The whole project went away meanwhile (deleted or unshared): that has its own notification.
            if (list.projectId !in sharedProjectIds || list.projectId in locallyDeletedProjectIds) continue
            val title = list.displayTitle
            deliver(
                Announcement(
                    "list:${list.id}", projectsNow[list.projectId]?.lastUpdatedBy.orEmpty(),
                    "{who} deleted list: '$title'", "List deleted", "{who} deleted '$title'"
                )
            )
        }
    }

    /** A shared project disappeared: it was deleted, or the owner removed this user's access. */
    private fun announceRemovedProjects(accessible: List<Project>) {
        val current = accessible.associateBy { it.id }
        val previous = previousAccessibleProjects
        previousAccessibleProjects = current
        // First snapshot is only the baseline. Demo mode edits are all local.
        if (previous == null || com.example.demo.DemoMode.isDemoActive.value) return
        val myUid = repository.currentUserId()
        val gone = previous.values.filter { before ->
            before.id !in current &&
                !before.fromCache &&
                before.id !in locallyDeletedProjectIds &&
                // Mine and not shared with anybody: nobody else is affected. Others' projects are shared with me.
                (before.ownerId != myUid || before.memberEmails.isNotEmpty())
        }
        if (gone.isEmpty()) return
        viewModelScope.launch {
            for (project in gone) {
                val title = project.displayTitle.ifBlank { "Untitled project" }
                deliver(
                    if (project.ownerId == myUid) {
                        Announcement("project:${project.id}", "", "Project '$title' was deleted", "Project deleted", "'$title' was deleted")
                    } else {
                        Announcement(
                            "project:${project.id}", project.ownerEmail,
                            "{who} deleted or unshared project: '$title'", "Project removed",
                            "{who} deleted or unshared '$title'"
                        )
                    }
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
        // Tasks added / edited / completed / deleted elsewhere. Uses its own cold flow with snapshot
        // metadata so the first real snapshot is the baseline and local writes can be told apart.
        viewModelScope.launch {
            try {
                repository.observeLists(null, includeMetadata = true).collect { announceRemoteTaskChanges(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Remote task change watcher stopped", e)
            }
        }
        // A shared project was deleted / my access was removed
        viewModelScope.launch {
            try {
                repository.observeAccessibleProjects(includeMetadata = true).collect { announceRemovedProjects(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Removed project watcher stopped", e)
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

    /**
     * Opens the project a share link points to. Waits a moment for the project to show up among the
     * ones this user can access (it arrives from the server if they were just invited).
     * Returns false when they have no access to it.
     */
    suspend fun openProjectFromLink(projectId: String): Boolean {
        val found = kotlinx.coroutines.withTimeoutOrNull(8_000) {
            repository.observeAccessibleProjects().first { list -> list.any { it.id == projectId } }
        }
        if (found == null) return false
        _selectedProjectId.value = projectId
        _selectedListId.value = null
        return true
    }

    /** Shows exactly this list (and its project), without the toggle-off behaviour of the two selectors above. */
    fun showList(projectId: String, listId: String) {
        _selectedProjectId.value = projectId
        _selectedListId.value = listId
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
            // Mark before deleting: the disappearance reaches the watchers before the call returns.
            locallyDeletedProjectIds.add(projectId)
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
            locallyDeletedListIds.add(listId)
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
            try {
                repository.updateTask(task)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update task", e)
            }
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
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
                Log.e(TAG, "Failed to toggle task completion", e)
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

    /** Registers this device's FCM token so the server can send pushes about shared projects. */
    fun registerForPush() {
        viewModelScope.launch {
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                repository.registerDevice(token)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Push registration failed", e)
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
