package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.SharedGray
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.data.model.Project
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonPink
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Shown when the widget is added (and when it is reconfigured): pick the project it shows. */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Backing out of the picker cancels adding the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                var loading by remember { mutableStateOf(true) }
                var signedIn by remember { mutableStateOf(true) }
                var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
                var myUserId by remember { mutableStateOf("") }
                // Owner's display name by email, filled in after the list is shown.
                val ownerNames = remember { mutableStateMapOf<String, String>() }

                LaunchedEffect(Unit) {
                    signedIn = WidgetDataSource.isSignedIn(this@WidgetConfigActivity)
                    if (signedIn) {
                        myUserId = WidgetDataSource.currentUserId(this@WidgetConfigActivity)
                        // My own projects first, then the ones shared with me; each group A-Z.
                        projects = WidgetDataSource.fetchProjects(this@WidgetConfigActivity)
                            .orEmpty()
                            .sortedWith(
                                compareBy<Project> { it.ownerId != myUserId }
                                    .thenBy { it.displayTitle.lowercase() }
                            )
                    }
                    loading = false

                    projects.filter { it.ownerId != myUserId && it.ownerEmail.isNotBlank() }
                        .map { it.ownerEmail }
                        .distinct()
                        .forEach { email ->
                            ownerNames[email] = WidgetDataSource.resolveOwnerName(this@WidgetConfigActivity, email)
                        }
                }

                ConfigContent(
                    loading = loading,
                    signedIn = signedIn,
                    projects = projects,
                    myUserId = myUserId,
                    ownerNames = ownerNames,
                    onPick = ::onProjectPicked
                )
            }
        }
    }

    private fun onProjectPicked(project: Project) {
        val appContext = applicationContext
        WidgetPrefs.setProject(appContext, widgetId, project.id, project.displayTitle)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            CheckListWidget().refresh(appContext, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

@Composable
private fun ConfigContent(
    loading: Boolean,
    signedIn: Boolean,
    projects: List<Project>,
    myUserId: String,
    ownerNames: Map<String, String>,
    onPick: (Project) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepMidnight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Text("Choose a project", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.size(6.dp))
        Text(
            "The widget shows lists and tasks from this project.",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.size(20.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonPink)
            }
            !signedIn -> CenteredNote("Open check.app and sign in first, then add the widget again.")
            projects.isEmpty() -> CenteredNote("No projects yet. Create one in check.app first.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { project ->
                    val sharedWithMe = project.ownerId != myUserId
                    ProjectRow(
                        project = project,
                        sharedWithMe = sharedWithMe,
                        ownerName = if (sharedWithMe) {
                            ownerNames[project.ownerEmail] ?: project.ownerEmail.substringBefore("@")
                        } else null,
                        onClick = { onPick(project) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredNote(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    sharedWithMe: Boolean,
    ownerName: String?,
    onClick: () -> Unit
) {
    // Projects somebody else shared with me are always gray, like everywhere else in the app.
    val dot = if (sharedWithMe) SharedGray
    else runCatching { Color(AndroidColor.parseColor(project.colorHex)) }.getOrDefault(NeonPink)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Text(
            project.displayTitle.ifBlank { "Untitled project" },
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (sharedWithMe) {
            Spacer(Modifier.width(12.dp))
            if (!ownerName.isNullOrBlank()) {
                Text(
                    ownerName,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = "Shared with you",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
