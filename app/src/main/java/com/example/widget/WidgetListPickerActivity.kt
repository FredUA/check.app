package com.example.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonPink
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** The widget's "select": a small popup with the lists of the widget's project. */
class WidgetListPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent?.getIntExtra(
            WidgetIntents.EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val projectId = WidgetPrefs.projectId(this, widgetId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || projectId == null) {
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                var loading by remember { mutableStateOf(true) }
                var options by remember { mutableStateOf<List<WidgetListOption>>(emptyList()) }
                val currentListId = remember { WidgetPrefs.listId(this@WidgetListPickerActivity, widgetId) }

                LaunchedEffect(Unit) {
                    options = WidgetDataSource.fetchLists(this@WidgetListPickerActivity, projectId, waitForServer = false)
                        .orEmpty()
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                        .map { WidgetListOption(it.id, it.displayTitle.ifBlank { "Untitled list" }) }
                    loading = false
                }

                // Tap outside the card to dismiss.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() }
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceCard)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            "Show list",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                        when {
                            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonPink)
                            }
                            options.isEmpty() -> Text(
                                "No lists in this project yet.",
                                color = TextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(20.dp)
                            )
                            else -> LazyColumn {
                                items(options, key = { it.id }) { option ->
                                    val selected = option.id == (currentListId ?: options.first().id)
                                    Text(
                                        text = option.title,
                                        color = if (selected) NeonPink else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (selected) SurfaceCardLight else SurfaceCard)
                                            .clickable { onListPicked(widgetId, option.id) }
                                            .padding(horizontal = 20.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onListPicked(widgetId: Int, listId: String) {
        val appContext = applicationContext
        WidgetPrefs.setList(appContext, widgetId, listId)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            CheckListWidget().refresh(appContext, glanceId)
            finish()
        }
    }
}
