package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.Project
import com.example.data.model.Subtask
import com.example.data.model.TaskItem
import com.example.data.model.TaskList
import com.example.data.model.UserProfile
import com.example.demo.DemoMode
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TaskFlowRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun currentUserEmail(): String {
        return auth.currentUser?.email.orEmpty().trim().lowercase()
    }

    // --- User Profile ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.observeProfile()
        }
        return flow {
            val path = "users/$userId"
            val docRef = db.collection("users").document(userId)
            emitAll(
                docRef.snapshots()
                    .map { snapshot ->
                        if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                    }
                    .catch { error ->
                        handleFirestoreError(error, OperationType.GET, path)
                        throw error
                    }
            )
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        if (DemoMode.isDemoActive.value) return
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val payload = mapOf(
                "email" to (profile.email.ifBlank { currentUserEmail() }),
                "displayName" to profile.displayName,
                "firstName" to profile.firstName,
                "lastName" to profile.lastName,
                "avatarUrl" to profile.avatarUrl,
                "createdAt" to (profile.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Projects ---

    fun observeProjects(): Flow<List<Project>> {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.observeProjects()
        }
        return flow {
            val uid = requireUserId()
            val path = "projects"
            emitAll(
                db.collection(path)
                    .whereEqualTo("ownerId", uid)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Project::class.java)?.copy(
                                id = doc.id,
                                name = doc.getString("name") ?: doc.getString("title").orEmpty(),
                                title = doc.getString("name") ?: doc.getString("title").orEmpty()
                            )
                        }
                    }
                    .catch { error ->
                        handleFirestoreError(error, OperationType.LIST, path)
                        throw error
                    }
            )
        }
    }

    fun observeSharedProjects(): Flow<List<Project>> {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.observeSharedProjects()
        }
        return flow {
            val email = currentUserEmail()
            if (email.isBlank()) {
                emit(emptyList())
                return@flow
            }
            val path = "projects"
            emitAll(
                db.collection(path)
                    .whereArrayContains("memberEmails", email)
                    .snapshots()
                    .map { snapshot ->
                        val uid = auth.currentUser?.uid.orEmpty()
                        snapshot.documents.mapNotNull { doc ->
                            val proj = doc.toObject(Project::class.java)?.copy(
                                id = doc.id,
                                name = doc.getString("name") ?: doc.getString("title").orEmpty(),
                                title = doc.getString("name") ?: doc.getString("title").orEmpty()
                            )
                            if (proj?.ownerId != uid) proj else null
                        }
                    }
                    .catch { error ->
                        handleFirestoreError(error, OperationType.LIST, path)
                        throw error
                    }
            )
        }
    }

    fun observeAccessibleProjects(): Flow<List<Project>> {
        return combine(observeProjects(), observeSharedProjects()) { owned, shared ->
            owned + shared.filter { s -> owned.none { it.id == s.id } }
        }
    }

    suspend fun createProject(title: String, description: String, colorHex: String): String {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.createProject(title, description, colorHex)
        }
        val uid = requireUserId()
        val email = currentUserEmail()
        val docRef = db.collection("projects").document()
        val payload = mapOf(
            "name" to title.trim(),
            "title" to title.trim(),
            "description" to description.trim(),
            "colorHex" to colorHex,
            "ownerId" to uid,
            "ownerEmail" to email,
            "memberIds" to listOf(uid),
            "memberEmails" to emptyList<String>(),
            "sharedWith" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateProject(project: Project) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.updateProject(project)
            return
        }
        val docRef = db.collection("projects").document(project.id)
        try {
            val payload = mapOf(
                "ownerId" to project.ownerId,
                "ownerEmail" to project.ownerEmail.ifBlank { currentUserEmail() },
                "name" to project.displayTitle.trim(),
                "title" to project.displayTitle.trim(),
                "description" to project.description.trim(),
                "colorHex" to project.colorHex,
                "memberIds" to project.memberIds,
                "memberEmails" to project.memberEmails,
                "sharedWith" to project.sharedWith,
                "createdAt" to (project.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProject(projectId: String) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.deleteProject(projectId)
            return
        }
        val docRef = db.collection("projects").document(projectId)
        try {
            // Delete all subcollection lists
            val listsSnap = docRef.collection("lists").get().await()
            for (doc in listsSnap.documents) {
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun shareProject(projectId: String, targetEmail: String) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.shareProject(projectId, targetEmail)
            return
        }
        val trimmed = targetEmail.trim().lowercase()
        if (trimmed.isBlank()) return
        val docRef = db.collection("projects").document(projectId)
        try {
            docRef.update(
                "memberEmails", FieldValue.arrayUnion(trimmed),
                "memberIds", FieldValue.arrayUnion(trimmed),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun unshareProject(projectId: String, targetEmail: String) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.unshareProject(projectId, targetEmail)
            return
        }
        val trimmed = targetEmail.trim().lowercase()
        if (trimmed.isBlank()) return
        val docRef = db.collection("projects").document(projectId)
        try {
            docRef.update(
                "memberEmails", FieldValue.arrayRemove(trimmed),
                "memberIds", FieldValue.arrayRemove(trimmed),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Task Lists (Subcollection: /projects/{projectId}/lists/{listId}) ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLists(projectId: String?): Flow<List<TaskList>> {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.observeLists(projectId)
        }
        if (!projectId.isNullOrBlank()) {
            return db.collection("projects").document(projectId).collection("lists")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(TaskList::class.java)?.copy(
                            id = doc.id,
                            projectId = projectId,
                            name = doc.getString("name") ?: doc.getString("title").orEmpty(),
                            title = doc.getString("name") ?: doc.getString("title").orEmpty()
                        )
                    }
                }
                .catch { emit(emptyList()) }
        }
        return observeAccessibleProjects().flatMapLatest { projects ->
            if (projects.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(projects.map { project ->
                    db.collection("projects").document(project.id).collection("lists")
                        .snapshots()
                        .map { snapshot ->
                            snapshot.documents.mapNotNull { doc ->
                                doc.toObject(TaskList::class.java)?.copy(
                                    id = doc.id,
                                    projectId = project.id,
                                    name = doc.getString("name") ?: doc.getString("title").orEmpty(),
                                    title = doc.getString("name") ?: doc.getString("title").orEmpty()
                                )
                            }
                        }
                        .catch { emit(emptyList()) }
                }) { listsArray ->
                    listsArray.flatMap { it }
                }
            }
        }
    }

    fun observeSharedLists(): Flow<List<TaskList>> {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.observeSharedLists()
        }
        // observeLists already streams all lists from all accessible (owned and shared) projects
        return flowOf(emptyList())
    }

    suspend fun createList(projectId: String, title: String, colorHex: String): String {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.createList(projectId, title, colorHex)
        }
        val uid = requireUserId()
        val docRef = db.collection("projects").document(projectId).collection("lists").document()
        val payload = mapOf(
            "id" to docRef.id,
            "name" to title.trim(),
            "title" to title.trim(),
            "colorHex" to colorHex,
            "projectId" to projectId,
            "ownerId" to uid,
            "tasks" to emptyMap<String, Any>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateList(list: TaskList) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.updateList(list)
            return
        }
        val docRef = db.collection("projects").document(list.projectId).collection("lists").document(list.id)
        try {
            docRef.update(
                "name", list.displayTitle.trim(),
                "title", list.displayTitle.trim(),
                "colorHex", list.colorHex,
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteList(listId: String, projectId: String = "") {
        if (DemoMode.isDemoActive.value) {
            DemoMode.deleteList(listId)
            return
        }
        if (projectId.isBlank()) return
        val docRef = db.collection("projects").document(projectId).collection("lists").document(listId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun shareList(listId: String, targetEmail: String) {
        // Scoped under projects
    }

    suspend fun unshareList(listId: String, targetEmail: String) {
        // Scoped under projects
    }

    // --- Tasks (Embedded inside /projects/{projectId}/lists/{listId}.tasks) ---

    fun observeTasks(listId: String?): Flow<List<TaskItem>> {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.observeTasks(listId)
        }
        return observeLists(null).map { allLists ->
            val tasks = allLists.flatMap { it.parseTasks() }
            if (listId.isNullOrBlank()) tasks else tasks.filter { it.listId == listId }
        }
    }

    fun observeSharedTasks(): Flow<List<TaskItem>> {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.observeSharedTasks()
        }
        // observeTasks already streams all tasks from all accessible lists
        return flowOf(emptyList())
    }

    suspend fun createTask(
        listId: String,
        projectId: String,
        title: String,
        notes: String = ""
    ): String {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.createTask(listId, projectId, title, notes)
        }
        val uid = requireUserId()
        val currentEmail = currentUserEmail()
        val taskId = UUID.randomUUID().toString()

        val taskData = mapOf(
            "id" to taskId,
            "listId" to listId,
            "projectId" to projectId,
            "ownerId" to uid,
            "title" to title.trim(),
            "notes" to notes.trim(),
            "isCompleted" to false,
            "completed" to false,
            "subtasks" to emptyList<Map<String, Any>>(),
            "lastUpdatedBy" to currentEmail,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )
        val listRef = db.collection("projects").document(projectId).collection("lists").document(listId)
        try {
            listRef.update(
                "tasks.$taskId", taskData,
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
            return taskId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, listRef.path)
            throw e
        }
    }

    suspend fun updateTask(task: TaskItem) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.updateTask(task)
            return
        }
        val listRef = db.collection("projects").document(task.projectId).collection("lists").document(task.listId)
        try {
            listRef.update(
                "tasks.${task.id}.title", task.title.trim(),
                "tasks.${task.id}.notes", task.notes.trim(),
                "tasks.${task.id}.isCompleted", task.isCompleted,
                "tasks.${task.id}.completed", task.isCompleted,
                "tasks.${task.id}.subtasks", task.subtasks,
                "tasks.${task.id}.lastUpdatedBy", currentUserEmail(),
                "tasks.${task.id}.updatedAt", Timestamp.now(),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, listRef.path)
            throw e
        }
    }

    suspend fun toggleTaskCompletion(task: TaskItem): Boolean {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.toggleTaskCompletion(task)
        }
        val nextCompleted = !task.isCompleted
        val listRef = db.collection("projects").document(task.projectId).collection("lists").document(task.listId)
        try {
            val updates = mutableMapOf<String, Any?>(
                "tasks.${task.id}.isCompleted" to nextCompleted,
                "tasks.${task.id}.completed" to nextCompleted,
                "tasks.${task.id}.completedAt" to if (nextCompleted) Timestamp.now() else null,
                "tasks.${task.id}.lastUpdatedBy" to currentUserEmail(),
                "tasks.${task.id}.updatedAt" to Timestamp.now(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            listRef.update(updates).await()
            return nextCompleted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, listRef.path)
            throw e
        }
    }

    suspend fun addSubtask(task: TaskItem, subtaskTitle: String) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.addSubtask(task, subtaskTitle)
            return
        }
        val newSubtask = mapOf(
            "id" to "sub_${UUID.randomUUID().toString().take(6)}",
            "title" to subtaskTitle.trim(),
            "isCompleted" to false
        )
        val updatedSubtasks = task.subtasks + newSubtask
        val listRef = db.collection("projects").document(task.projectId).collection("lists").document(task.listId)
        try {
            listRef.update(
                "tasks.${task.id}.subtasks", updatedSubtasks,
                "tasks.${task.id}.lastUpdatedBy", currentUserEmail(),
                "tasks.${task.id}.updatedAt", Timestamp.now(),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, listRef.path)
            throw e
        }
    }

    suspend fun toggleSubtaskCompletion(task: TaskItem, subtaskId: String) = toggleSubtask(task, subtaskId)

    suspend fun toggleSubtask(task: TaskItem, subtaskId: String) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.toggleSubtask(task, subtaskId)
            return
        }
        val updatedSubtasks = task.subtasks.map { map ->
            if (map["id"] == subtaskId) {
                val current = map["isCompleted"] as? Boolean ?: false
                map.toMutableMap().apply { put("isCompleted", !current) }
            } else map
        }
        val listRef = db.collection("projects").document(task.projectId).collection("lists").document(task.listId)
        try {
            listRef.update(
                "tasks.${task.id}.subtasks", updatedSubtasks,
                "tasks.${task.id}.lastUpdatedBy", currentUserEmail(),
                "tasks.${task.id}.updatedAt", Timestamp.now(),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, listRef.path)
            throw e
        }
    }

    suspend fun deleteSubtask(task: TaskItem, subtaskId: String) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.deleteSubtask(task, subtaskId)
            return
        }
        val updatedSubtasks = task.subtasks.filterNot { it["id"] == subtaskId }
        val listRef = db.collection("projects").document(task.projectId).collection("lists").document(task.listId)
        try {
            listRef.update(
                "tasks.${task.id}.subtasks", updatedSubtasks,
                "tasks.${task.id}.lastUpdatedBy", currentUserEmail(),
                "tasks.${task.id}.updatedAt", Timestamp.now(),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, listRef.path)
            throw e
        }
    }

    suspend fun promoteSubtaskToTask(task: TaskItem, subtask: Subtask): String {
        if (DemoMode.isDemoActive.value) {
            return DemoMode.promoteSubtaskToTask(task, subtask)
        }
        deleteSubtask(task, subtask.id)
        return createTask(
            listId = task.listId,
            projectId = task.projectId,
            title = subtask.title,
            notes = "Promoted from subtask of '${task.title}'"
        )
    }

    suspend fun reassignTaskToList(task: TaskItem, targetListId: String, targetProjectId: String) {
        if (DemoMode.isDemoActive.value) {
            DemoMode.reassignTask(task, targetListId, targetProjectId)
            return
        }
        try {
            // Remove from source list
            val oldListRef = db.collection("projects").document(task.projectId).collection("lists").document(task.listId)
            try {
                oldListRef.update("tasks.${task.id}", FieldValue.delete()).await()
            } catch (_: Exception) {}

            // Add to target list
            val updatedTask = task.copy(listId = targetListId, projectId = targetProjectId)
            val newListRef = db.collection("projects").document(targetProjectId).collection("lists").document(targetListId)
            newListRef.update(
                "tasks.${task.id}", updatedTask.toMap(),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "projects/$targetProjectId/lists/$targetListId")
            throw e
        }
    }

    suspend fun deleteTask(taskId: String, listId: String = "", projectId: String = "") {
        if (DemoMode.isDemoActive.value) {
            DemoMode.deleteTask(taskId)
            return
        }
        if (projectId.isBlank() || listId.isBlank()) return
        val listRef = db.collection("projects").document(projectId).collection("lists").document(listId)
        try {
            listRef.update(
                "tasks.$taskId", FieldValue.delete(),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, listRef.path)
            throw e
        }
    }

    suspend fun shareTask(taskId: String, targetEmail: String) {
        // Scoped under projects
    }

    suspend fun unshareTask(taskId: String, targetEmail: String) {
        // Scoped under projects
    }

    // --- Error Handling ---

    private fun handleFirestoreError(e: Throwable, operation: OperationType, path: String) {
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            Log.e(
                "TaskFlowRepo",
                "Firestore PERMISSION_DENIED on $operation at $path. Auth state: uid=${auth.currentUser?.uid}, email=${auth.currentUser?.email}",
                e
            )
        } else {
            Log.e("TaskFlowRepo", "Firestore error on $operation at $path: ${e.message}", e)
        }
    }

    enum class OperationType {
        GET, LIST, CREATE, UPDATE, DELETE, WRITE
    }
}
