package com.example.widget

import android.content.Context
import android.util.Log
import com.example.data.DeviceIdentity
import com.example.data.TaskFlowRepository
import com.example.data.model.Project
import com.example.data.model.TaskList
import com.example.ui.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** One list in the widget's drop-down. */
data class WidgetListOption(val id: String, val title: String)

data class WidgetTask(
    val id: String,
    val listId: String,
    val projectId: String,
    val title: String,
    val completed: Boolean
)

/** Everything the widget needs to draw itself. */
sealed interface WidgetState {
    /** The widget was added but no project has been picked yet. */
    data object NotConfigured : WidgetState

    /** Nobody is signed in to the app. */
    data object SignedOut : WidgetState

    /** The data could not be loaded (offline with no cache, permission problem, ...). */
    data object Failed : WidgetState

    data class Ready(
        val projectId: String,
        val projectName: String,
        val lists: List<WidgetListOption>,
        val selectedListId: String?,
        val tasks: List<WidgetTask>
    ) : WidgetState {
        val selectedList: WidgetListOption? get() = lists.firstOrNull { it.id == selectedListId }
    }
}

/**
 * The last state drawn for each widget. Shown right away while a refresh is loading, so the widget
 * doesn't flash empty. Lives only as long as the process.
 */
object WidgetStateCache {
    private val states = java.util.concurrent.ConcurrentHashMap<Int, WidgetState>()

    fun get(widgetId: Int): WidgetState? = states[widgetId]

    fun put(widgetId: Int, state: WidgetState) {
        states[widgetId] = state
    }

    fun remove(widgetId: Int) {
        states.remove(widgetId)
    }

    /**
     * Flips a task's done state in the cached state so the widget can redraw at once, before
     * Firestore has been written. Returns the task's previous done state, or null if the task
     * isn't in the cached state.
     */
    fun flipTask(widgetId: Int, taskId: String): Boolean? {
        var previous: Boolean? = null
        states.computeIfPresent(widgetId) { _, state ->
            if (state !is WidgetState.Ready) return@computeIfPresent state
            val task = state.tasks.firstOrNull { it.id == taskId } ?: return@computeIfPresent state
            previous = task.completed
            state.copy(
                tasks = state.tasks
                    .map { if (it.id == taskId) it.copy(completed = !it.completed) else it }
                    .sortedBy { it.completed } // stable: done tasks sink, the rest keep their order
            )
        }
        return previous
    }
}

/** Which project / list every placed widget shows. Kept per appWidgetId. */
object WidgetPrefs {
    private const val PREFS = "check_widget_prefs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun projectId(context: Context, widgetId: Int): String? =
        prefs(context).getString("project_$widgetId", null)?.ifBlank { null }

    fun projectName(context: Context, widgetId: Int): String =
        prefs(context).getString("project_name_$widgetId", null).orEmpty()

    fun listId(context: Context, widgetId: Int): String? =
        prefs(context).getString("list_$widgetId", null)?.ifBlank { null }

    /** Picking another project forgets the previously chosen list. */
    fun setProject(context: Context, widgetId: Int, projectId: String, projectName: String) {
        WidgetStateCache.remove(widgetId)
        prefs(context).edit()
            .putString("project_$widgetId", projectId)
            .putString("project_name_$widgetId", projectName)
            .remove("list_$widgetId")
            .apply()
    }

    fun setList(context: Context, widgetId: Int, listId: String) {
        WidgetStateCache.remove(widgetId)
        prefs(context).edit().putString("list_$widgetId", listId).apply()
    }

    fun clear(context: Context, widgetId: Int) {
        WidgetStateCache.remove(widgetId)
        prefs(context).edit()
            .remove("project_$widgetId")
            .remove("project_name_$widgetId")
            .remove("list_$widgetId")
            .apply()
    }
}

/** Reads and writes Firestore for the widget, which can run without the app's UI ever having started. */
object WidgetDataSource {
    private const val TAG = "WidgetData"
    private const val SERVER_WAIT_MS = 3_000L
    private const val LOAD_TIMEOUT_MS = 8_000L
    // Firestore applies a write to the local cache straight away; only the server acknowledgement is awaited,
    // so a short wait is enough (the write itself is not cancelled by the timeout).
    private const val WRITE_TIMEOUT_MS = 1_500L

    @Volatile
    private var initialized = false

    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            configureAppCheck(app, null)
            DeviceIdentity.init(app)
            initialized = true
        }
    }

    private fun repository(context: Context) = TaskFlowRepository(context.applicationContext)

    fun isSignedIn(context: Context): Boolean {
        ensureInit(context)
        return Firebase.auth.currentUser != null
    }

    /**
     * Lists of a project. With [waitForServer] a short wait is made for fresh data from the server
     * before falling back to the local cache, which keeps the widget quick offline.
     */
    suspend fun fetchLists(context: Context, projectId: String, waitForServer: Boolean): List<TaskList>? {
        if (!isSignedIn(context)) return null
        var latest: List<TaskList>? = null
        val repo = repository(context)
        try {
            withTimeoutOrNull(if (waitForServer) SERVER_WAIT_MS else LOAD_TIMEOUT_MS) {
                repo.observeLists(projectId, includeMetadata = true).first { lists ->
                    latest = lists
                    // A snapshot holding our own unsent change is already the freshest view we can get offline.
                    !waitForServer || lists.isEmpty() || lists.none { it.fromCache } ||
                        lists.any { it.hasPendingWrites }
                }
            }
            if (latest == null && waitForServer) {
                // No emission at all within the short wait: give the cache a chance.
                withTimeoutOrNull(LOAD_TIMEOUT_MS - SERVER_WAIT_MS) {
                    latest = repo.observeLists(projectId).first()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load lists of project $projectId", e)
        }
        return latest
    }

    suspend fun fetchProjects(context: Context): List<Project>? {
        if (!isSignedIn(context)) return null
        return try {
            withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                repository(context).observeAccessibleProjects().first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load projects", e)
            null
        }
    }

    suspend fun loadState(context: Context, widgetId: Int): WidgetState {
        val projectId = WidgetPrefs.projectId(context, widgetId) ?: return WidgetState.NotConfigured
        if (!isSignedIn(context)) return WidgetState.SignedOut
        val lists = fetchLists(context, projectId, waitForServer = true) ?: return WidgetState.Failed

        val ordered = lists.sortedBy { it.createdAt?.seconds ?: 0L }
        val options = ordered.map { WidgetListOption(it.id, it.displayTitle.ifBlank { "Untitled list" }) }
        val selected = options.firstOrNull { it.id == WidgetPrefs.listId(context, widgetId) }
            ?: options.firstOrNull()

        val tasks = ordered.firstOrNull { it.id == selected?.id }
            ?.parseTasks()
            .orEmpty()
            .sortedWith(
                compareBy<com.example.data.model.TaskItem> { it.isCompleted }
                    .thenBy { it.createdAt?.seconds ?: 0L }
            )
            .map {
                WidgetTask(
                    id = it.id,
                    listId = it.listId,
                    projectId = it.projectId,
                    title = it.title.ifBlank { "Untitled task" },
                    completed = it.isCompleted
                )
            }

        return WidgetState.Ready(
            projectId = projectId,
            projectName = WidgetPrefs.projectName(context, widgetId),
            lists = options,
            selectedListId = selected?.id,
            tasks = tasks
        )
    }

    /** Flips the done state of a task. The write is queued by Firestore even if the server can't be reached. */
    suspend fun toggleTask(context: Context, projectId: String, listId: String, taskId: String): Boolean? {
        val lists = fetchLists(context, projectId, waitForServer = false) ?: return null
        val task = lists.firstOrNull { it.id == listId }
            ?.parseTasks()
            ?.firstOrNull { it.id == taskId }
            ?: return null
        return try {
            // On timeout the write has still been queued, so the new state is the same.
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { repository(context).toggleTaskCompletion(task) }
                ?: !task.isCompleted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle task $taskId", e)
            null
        }
    }

    /** Display name of a project owner, for the project picker. */
    suspend fun resolveOwnerName(context: Context, email: String): String =
        repository(context).resolveDisplayName(email)

    fun currentUserId(context: Context): String {
        ensureInit(context)
        return Firebase.auth.currentUser?.uid.orEmpty()
    }
}
