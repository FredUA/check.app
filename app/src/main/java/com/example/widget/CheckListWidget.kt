package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.currentState
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R

/** Intent contract between the widget and [MainActivity]. */
object WidgetIntents {
    const val ACTION_NEW_LIST = "com.example.action.WIDGET_NEW_LIST"
    const val EXTRA_PROJECT_ID = "com.example.extra.PROJECT_ID"
    const val EXTRA_WIDGET_ID = "com.example.extra.WIDGET_ID"
}

private val WidgetBackground = Color(0xFF283E7A)
private val PillBackground = Color(0xFF384E96)
private val NeonPinkColor = Color(0xFFFF2A85)
private val TextPrimaryColor = Color(0xFFFFFFFF)
private val TextSecondaryColor = Color(0xFFA6BAE8)
private val TextMutedColor = Color(0xFF7E93CA)

private val ProjectIdKey = ActionParameters.Key<String>("projectId")
private val ListIdKey = ActionParameters.Key<String>("listId")
private val TaskIdKey = ActionParameters.Key<String>("taskId")

private val RefreshTickKey = longPreferencesKey("refresh_tick")

class CheckListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            // Glance keeps a widget's composition alive between updates and only recomposes it, so data
            // loaded before provideContent would stay stale. Loading inside, keyed on a tick that
            // refresh() bumps, reloads the data on every refresh.
            val tick = currentState<Preferences>()[RefreshTickKey] ?: 0L
            val state by produceState(initialValue = WidgetStateCache.get(widgetId), tick) {
                value = WidgetDataSource.loadState(context, widgetId)
                    .also { WidgetStateCache.put(widgetId, it) }
            }
            WidgetContent(context, widgetId, state)
        }
    }

    /** Reloads the data of one placed widget and redraws it. */
    suspend fun refresh(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { it[RefreshTickKey] = System.currentTimeMillis() }
        update(context, glanceId)
    }

    companion object {
        /** Reloads every placed widget (cheap when there are none). */
        suspend fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            val widget = CheckListWidget()
            GlanceAppWidgetManager(appContext)
                .getGlanceIds(CheckListWidget::class.java)
                .forEach { widget.refresh(appContext, it) }
        }
    }
}

class CheckListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CheckListWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // The periodic system update must reload the data too, not just redraw.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CheckListWidget.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val projectId = parameters[ProjectIdKey] ?: return
        val listId = parameters[ListIdKey] ?: return
        val taskId = parameters[TaskIdKey] ?: return
        WidgetDataSource.toggleTask(context, projectId, listId, taskId)
        CheckListWidget().refresh(context, glanceId)
    }
}

private fun openAppAction(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )

/** Opens the app with the "New list" dialog, the widget's project already chosen. */
private fun newListAction(context: Context, widgetId: Int, projectId: String): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = WidgetIntents.ACTION_NEW_LIST
            // Makes the intent unique per project so the system doesn't reuse another widget's PendingIntent.
            data = Uri.parse("checkapp-widget://new-list/$widgetId/$projectId")
            putExtra(WidgetIntents.EXTRA_PROJECT_ID, projectId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )

private fun pickListAction(context: Context, widgetId: Int): Action =
    actionStartActivity(
        Intent(context, WidgetListPickerActivity::class.java).apply {
            data = Uri.parse("checkapp-widget://pick-list/$widgetId")
            putExtra(WidgetIntents.EXTRA_WIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )

private fun reconfigureAction(context: Context, widgetId: Int): Action =
    actionStartActivity(
        Intent(context, WidgetConfigActivity::class.java).apply {
            data = Uri.parse("checkapp-widget://configure/$widgetId")
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )

@Composable
private fun WidgetContent(context: Context, widgetId: Int, state: WidgetState?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .cornerRadius(24.dp)
            .padding(10.dp)
    ) {
        when (state) {
            null -> Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading…",
                    style = TextStyle(color = ColorProvider(TextMutedColor), fontSize = 14.sp)
                )
            }
            is WidgetState.Ready -> ReadyContent(context, widgetId, state)
            WidgetState.NotConfigured -> MessageContent(
                text = "Tap to choose a project",
                action = reconfigureAction(context, widgetId)
            )
            WidgetState.SignedOut -> MessageContent(
                text = "Open check.app and sign in",
                action = openAppAction(context)
            )
            WidgetState.Failed -> MessageContent(
                text = "Couldn't load your lists. Tap to open check.app",
                action = openAppAction(context)
            )
        }
    }
}

@Composable
private fun MessageContent(text: String, action: Action) {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(TextSecondaryColor), fontSize = 14.sp)
        )
    }
}

@Composable
private fun ReadyContent(context: Context, widgetId: Int, state: WidgetState.Ready) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // New list (left)
        Row(
            modifier = GlanceModifier
                .background(ColorProvider(NeonPinkColor))
                .cornerRadius(18.dp)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                .clickable(newListAction(context, widgetId, state.projectId)),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_plus),
                contentDescription = "New list",
                modifier = GlanceModifier.size(18.dp)
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = "New list",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(TextPrimaryColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(GlanceModifier.width(8.dp))

        // List select (right)
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .background(ColorProvider(PillBackground))
                .cornerRadius(18.dp)
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                .clickable(pickListAction(context, widgetId)),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = state.selectedList?.title ?: "No lists",
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(TextPrimaryColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Image(
                provider = ImageProvider(R.drawable.ic_widget_chevron_down),
                contentDescription = "Choose list",
                modifier = GlanceModifier.size(18.dp)
            )
        }
    }

    Spacer(GlanceModifier.size(8.dp))

    when {
        state.selectedList == null -> EmptyHint("No lists in this project yet")
        state.tasks.isEmpty() -> EmptyHint("No tasks in this list yet")
        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(state.tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                TaskRow(task)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(TextMutedColor), fontSize = 13.sp)
        )
    }
}

@Composable
private fun TaskRow(task: WidgetTask) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 7.dp, horizontal = 4.dp)
            .clickable(
                actionRunCallback<ToggleTaskAction>(
                    actionParametersOf(
                        ProjectIdKey to task.projectId,
                        ListIdKey to task.listId,
                        TaskIdKey to task.id
                    )
                )
            ),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(
                if (task.completed) R.drawable.ic_widget_check_on else R.drawable.ic_widget_check_off
            ),
            contentDescription = if (task.completed) "Done" else "Not done",
            modifier = GlanceModifier.size(22.dp)
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = task.title,
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(if (task.completed) TextMutedColor else TextPrimaryColor),
                fontSize = 14.sp,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
            )
        )
    }
}
