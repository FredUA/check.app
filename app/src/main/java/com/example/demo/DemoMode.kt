package com.example.demo

import com.example.data.CheckAppRepository
import com.example.data.model.Project
import com.example.data.model.TaskItem
import com.example.data.model.TaskList
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Clean self-contained Demo Mode module.
 *
 * TO DISABLE OR TURN OFF:
 * Set [IS_DEMO_AVAILABLE] = false.
 * When false, all demo buttons and functionality are immediately disabled.
 */
object DemoMode {

    // Global toggle switch. Off for now: the sign-in screen hides the Demo Mode button.
    // Set to true to bring it back; all the demo code is kept.
    const val IS_DEMO_AVAILABLE = false

    // State of whether the user is currently inside Demo Mode
    val isDemoActive = MutableStateFlow(false)

    fun enterDemoMode() {
        if (!IS_DEMO_AVAILABLE) return
        resetDemoData()
        isDemoActive.value = true
    }

    fun exitDemoMode() {
        isDemoActive.value = false
    }

    // Demo user profile matching the reference screenshot ("What's up, Olivia!")
    val demoProfile = UserProfile(
        id = "demo_user_olivia",
        email = "olivia.mitchell@example.com",
        firstName = "Olivia",
        lastName = "Mitchell",
        displayName = "Olivia Mitchell",
        avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop"
    )

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    private val _lists = MutableStateFlow<List<TaskList>>(emptyList())
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())

    fun resetDemoData() {
        val projBusiness = Project(
            id = "proj_business",
            ownerId = "demo_user_olivia",
            title = "Business",
            description = "Work priorities, team meetings and client deliverables",
            colorHex = "#FF2A85",
            createdAt = Timestamp.now()
        )
        val projPersonal = Project(
            id = "proj_personal",
            ownerId = "demo_user_olivia",
            title = "Personal",
            description = "Fitness, books, and daily routines",
            colorHex = "#00D2FF",
            createdAt = Timestamp.now()
        )

        val listToday = TaskList(
            id = "list_today",
            projectId = "proj_business",
            ownerId = "demo_user_olivia",
            title = "Today's Tasks",
            colorHex = "#FF2A85",
            createdAt = Timestamp.now()
        )
        val listPersonalDaily = TaskList(
            id = "list_personal_daily",
            projectId = "proj_personal",
            ownerId = "demo_user_olivia",
            title = "Daily Focus",
            colorHex = "#00D2FF",
            createdAt = Timestamp.now()
        )

        val initialTasks = listOf(
            TaskItem(
                id = "task_1",
                listId = "list_today",
                projectId = "proj_business",
                ownerId = "demo_user_olivia",
                title = "Daily meeting with team",
                notes = "Discuss sprint board and launch timeline",
                isCompleted = false,
                createdAt = Timestamp.now()
            ),
            TaskItem(
                id = "task_2",
                listId = "list_personal_daily",
                projectId = "proj_personal",
                ownerId = "demo_user_olivia",
                title = "Pay for rent",
                notes = "Bank transfer due before 5 PM",
                isCompleted = true,
                completedAt = Timestamp.now(),
                createdAt = Timestamp.now()
            ),
            TaskItem(
                id = "task_3",
                listId = "list_today",
                projectId = "proj_business",
                ownerId = "demo_user_olivia",
                title = "Check emails & client requests",
                isCompleted = false,
                createdAt = Timestamp.now()
            ),
            TaskItem(
                id = "task_4",
                listId = "list_personal_daily",
                projectId = "proj_personal",
                ownerId = "demo_user_olivia",
                title = "Lunch with Emma",
                notes = "Green bistro at 1:30 PM",
                isCompleted = false,
                createdAt = Timestamp.now()
            ),
            TaskItem(
                id = "task_5",
                listId = "list_personal_daily",
                projectId = "proj_personal",
                ownerId = "demo_user_olivia",
                title = "Meditation & Evening Walk",
                isCompleted = false,
                createdAt = Timestamp.now()
            )
        )

        _projects.value = listOf(projBusiness, projPersonal)
        _lists.value = listOf(listToday, listPersonalDaily)
        _tasks.value = initialTasks
    }

    // --- Mock In-Memory Repository Operations for Demo Mode ---

    fun observeProfile(): Flow<UserProfile?> = MutableStateFlow(demoProfile).asStateFlow()

    fun observeProjects(): Flow<List<Project>> = _projects.asStateFlow()
    fun observeSharedProjects(): Flow<List<Project>> = MutableStateFlow(emptyList<Project>()).asStateFlow()

    fun createProject(title: String, description: String, colorHex: String): String {
        val newProj = Project(
            id = "demo_proj_${UUID.randomUUID().toString().take(6)}",
            ownerId = "demo_user_olivia",
            name = title,
            title = title,
            description = description,
            colorHex = colorHex,
            memberIds = listOf("demo_user_olivia"),
            createdAt = Timestamp.now()
        )
        _projects.value = _projects.value + newProj
        return newProj.id
    }

    fun updateProject(project: Project) {
        _projects.value = _projects.value.map { if (it.id == project.id) project else it }
    }

    fun deleteProject(projectId: String) {
        _projects.value = _projects.value.filterNot { it.id == projectId }
        _lists.value = _lists.value.filterNot { it.projectId == projectId }
        _tasks.value = _tasks.value.filterNot { it.projectId == projectId }
    }

    fun observeLists(projectId: String?): Flow<List<TaskList>> =
        _lists.map { list ->
            if (projectId.isNullOrBlank()) list else list.filter { it.projectId == projectId }
        }

    fun observeSharedLists(): Flow<List<TaskList>> = MutableStateFlow(emptyList<TaskList>()).asStateFlow()

    fun createList(projectId: String, title: String, colorHex: String): String {
        val project = _projects.value.find { it.id == projectId }
        val members = project?.memberEmails ?: emptyList()
        val newList = TaskList(
            id = "demo_list_${UUID.randomUUID().toString().take(6)}",
            projectId = projectId,
            ownerId = "demo_user_olivia",
            name = title,
            title = title,
            colorHex = colorHex,
            memberEmails = members,
            createdAt = Timestamp.now()
        )
        _lists.value = _lists.value + newList
        return newList.id
    }

    fun updateList(list: TaskList) {
        _lists.value = _lists.value.map { if (it.id == list.id) list else it }
    }

    fun deleteList(listId: String) {
        _lists.value = _lists.value.filterNot { it.id == listId }
        _tasks.value = _tasks.value.filterNot { it.listId == listId }
    }

    fun observeTasks(listId: String?): Flow<List<TaskItem>> =
        _tasks.map { taskList ->
            if (listId.isNullOrBlank()) taskList else taskList.filter { it.listId == listId }
        }

    fun observeSharedTasks(): Flow<List<TaskItem>> = MutableStateFlow(emptyList<TaskItem>()).asStateFlow()

    fun createTask(listId: String, projectId: String, title: String, notes: String): String {
        val project = _projects.value.find { it.id == projectId }
        val members = project?.memberEmails ?: emptyList()
        val newTask = TaskItem(
            id = "demo_task_${UUID.randomUUID().toString().take(6)}",
            listId = listId,
            projectId = projectId,
            ownerId = "demo_user_olivia",
            title = title,
            notes = notes,
            memberEmails = members,
            createdAt = Timestamp.now()
        )
        _tasks.value = _tasks.value + newTask
        return newTask.id
    }

    fun updateTask(task: TaskItem) {
        _tasks.value = _tasks.value.map { if (it.id == task.id) task else it }
    }

    fun toggleTaskCompletion(task: TaskItem): Boolean {
        val nextCompleted = !task.isCompleted
        val updated = task.copy(
            isCompleted = nextCompleted,
            completedAt = if (nextCompleted) Timestamp.now() else null
        )
        updateTask(updated)
        return nextCompleted
    }

    fun reassignTask(task: TaskItem, targetListId: String, targetProjectId: String) {
        updateTask(task.copy(listId = targetListId, projectId = targetProjectId))
    }

    fun deleteTask(taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
    }

    fun shareProject(projectId: String, targetEmail: String) {
        val trimmed = targetEmail.trim().lowercase()
        if (trimmed.isBlank()) return
        _projects.value = _projects.value.map { proj ->
            if (proj.id == projectId) {
                proj.copy(memberEmails = (proj.memberEmails + trimmed).distinct())
            } else proj
        }
        _lists.value = _lists.value.map { list ->
            if (list.projectId == projectId) {
                list.copy(memberEmails = (list.memberEmails + trimmed).distinct())
            } else list
        }
        _tasks.value = _tasks.value.map { task ->
            if (task.projectId == projectId) {
                task.copy(memberEmails = (task.memberEmails + trimmed).distinct())
            } else task
        }
    }

    fun unshareProject(projectId: String, targetEmail: String) {
        val trimmed = targetEmail.trim().lowercase()
        _projects.value = _projects.value.map { proj ->
            if (proj.id == projectId) {
                proj.copy(memberEmails = proj.memberEmails.filterNot { it.trim().lowercase() == trimmed })
            } else proj
        }
        _lists.value = _lists.value.map { list ->
            if (list.projectId == projectId) {
                list.copy(memberEmails = list.memberEmails.filterNot { it.trim().lowercase() == trimmed })
            } else list
        }
        _tasks.value = _tasks.value.map { task ->
            if (task.projectId == projectId) {
                task.copy(memberEmails = task.memberEmails.filterNot { it.trim().lowercase() == trimmed })
            } else task
        }
    }

    fun unshareList(listId: String, targetEmail: String) {
        val trimmed = targetEmail.trim().lowercase()
        _lists.value = _lists.value.map { list ->
            if (list.id == listId) {
                list.copy(memberEmails = list.memberEmails.filterNot { it.trim().lowercase() == trimmed })
            } else list
        }
    }

    fun unshareTask(taskId: String, targetEmail: String) {
        val trimmed = targetEmail.trim().lowercase()
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(memberEmails = task.memberEmails.filterNot { it.trim().lowercase() == trimmed })
            } else task
        }
    }
}
