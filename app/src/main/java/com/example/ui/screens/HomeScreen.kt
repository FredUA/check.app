package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppForegroundTracker
import com.example.ui.SoundEffectHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.model.Project
import com.example.data.model.TaskItem
import com.example.data.model.TaskList
import com.example.data.model.UserProfile
import com.example.ui.components.AddProjectCard
import com.example.ui.components.CategoryCard
import com.example.ui.components.QuickAddTaskRowItem
import com.example.ui.components.SpeedDialFab
import com.example.ui.components.SwipeToDeleteBox
import com.example.ui.components.highlightNotificationKeywords
import com.example.ui.components.TaskRowItem
import com.example.ui.dialogs.CreateListDialog
import com.example.ui.dialogs.CreateProjectDialog
import com.example.ui.dialogs.CreateTaskDialog
import com.example.ui.dialogs.EditListDialog
import com.example.ui.dialogs.EditProjectDialog
import com.example.ui.dialogs.ShareEntityDialog
import com.example.ui.dialogs.TaskDetailDialog
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SharedGray
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceCardLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SortOption
import com.example.ui.viewmodel.TaskFlowViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userProfile: UserProfile?,
    userEmail: String,
    viewModel: TaskFlowViewModel,
    onOpenDrawer: () -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    val sharedProjects by viewModel.sharedProjects.collectAsState()
    val allLists by viewModel.allLists.collectAsState()
    val sharedLists by viewModel.sharedLists.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val sharedTasks by viewModel.sharedTasks.collectAsState()

    val selectedProjectId by viewModel.selectedProjectId.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val floatingBanner by viewModel.floatingBanner.collectAsState()

    val context = LocalContext.current
    val taskListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var speedDialOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showNotificationsPopover by remember { mutableStateOf(false) }

    // Play the "task done" sound when someone else completes a task on another device.
    LaunchedEffect(viewModel) {
        viewModel.remoteTaskCompleted.collect {
            // In the background the system notification plays its own sound instead.
            if (AppForegroundTracker.isInForeground) {
                SoundEffectHelper.playTaskDone(context)
            }
        }
    }

    val bellRotation = remember { Animatable(0f) }
    LaunchedEffect(notifications.size) {
        if (notifications.isNotEmpty()) {
            bellRotation.snapTo(0f)
            bellRotation.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 650
                    0f at 0
                    (-16f) at 90
                    16f at 180
                    (-12f) at 270
                    12f at 360
                    (-6f) at 450
                    6f at 540
                    0f at 650
                }
            )
        }
    }

    // Dialog States
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var selectedTaskForDetail by remember { mutableStateOf<TaskItem?>(null) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }
    var listToEdit by remember { mutableStateOf<TaskList?>(null) }
    var shareEntityTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) } // type, id, title

    // Combine user's own and shared entities
    val combinedProjects = remember(projects, sharedProjects) {
        projects + sharedProjects.filter { sp -> projects.none { it.id == sp.id } }
    }
    // Projects somebody else shared with me: shown gray, and their colors can't be changed by me.
    val sharedWithMeIds = remember(sharedProjects) { sharedProjects.map { it.id }.toSet() }
    val combinedLists = remember(allLists, sharedLists) {
        allLists + sharedLists.filter { sl -> allLists.none { it.id == sl.id } }
    }
    val combinedTasks = remember(allTasks, sharedTasks) {
        allTasks + sharedTasks.filter { st -> allTasks.none { it.id == st.id } }
    }

    // Filter tasks based on selected project, list, and search query
    val filteredTasks = remember(combinedTasks, selectedProjectId, selectedListId, searchQuery, sortOption) {
        var list = combinedTasks
        if (!selectedProjectId.isNullOrBlank()) {
            list = list.filter { it.projectId == selectedProjectId }
        }
        if (!selectedListId.isNullOrBlank()) {
            list = list.filter { it.listId == selectedListId }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.notes.contains(searchQuery, ignoreCase = true)
            }
        }
        // Apply sorting
        when (sortOption) {
            SortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.DATE_NEWEST -> list.sortedByDescending { it.createdAt?.seconds ?: 0L }
            SortOption.DATE_OLDEST -> list.sortedBy { it.createdAt?.seconds ?: 0L }
            SortOption.TYPE -> list.sortedBy { it.listId }
        }
    }

    val firstName = userProfile?.firstName?.ifBlank { null }
        ?: userProfile?.displayName?.split(" ")?.firstOrNull()?.ifBlank { null }
        ?: userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

    val contentBlurRadius by animateDpAsState(
        targetValue = if (speedDialOpen) 18.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "content_blur"
    )

    // Row move animations are on only around a swipe-to-delete (see the task rows below).
    var swipeInProgress by remember { mutableStateOf(false) }
    var animateRowPlacement by remember { mutableStateOf(false) }
    LaunchedEffect(swipeInProgress) {
        if (swipeInProgress) {
            animateRowPlacement = true
        } else {
            // Keep it on while the deleted row's neighbours slide into place.
            delay(1_500)
            animateRowPlacement = false
        }
    }

    var isQuickAddActive by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val isImeOpen = imeBottom > 0.dp

    LaunchedEffect(isImeOpen) {
        if (!isImeOpen) {
            isQuickAddActive = false
        }
    }

    val isInputActive = isQuickAddActive || isImeOpen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepMidnight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(contentBlurRadius)
                .padding(horizontal = 20.dp)
        ) {
            // Top Bar (with two lines icon matching screenshot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Two horizontal lines menu button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenDrawer)
                        .testTag("menu_drawer_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.width(22.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextPrimary)
                        )
                    }
                }

                // Right icons: Search & Notifications
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { showNotificationsPopover = true },
                        modifier = Modifier.testTag("notifications_bell_button")
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer(rotationZ = bellRotation.value)
                            )
                            if (notifications.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(NeonPink)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }

            // LISTS Section (Under Selected Project or All)
            val availableLists = remember(combinedLists, selectedProjectId) {
                if (selectedProjectId == null) combinedLists
                else combinedLists.filter { it.projectId == selectedProjectId }
            }

            var lastListIdForTaskAdd by remember { mutableStateOf(selectedListId) }
            var previousTaskCount by remember { mutableStateOf(filteredTasks.size) }

            // When switching lists or projects, reset quick add active state and reset scroll position to tasks header if scrolled deep
            LaunchedEffect(selectedListId, selectedProjectId) {
                isQuickAddActive = false
                if (taskListState.firstVisibleItemIndex > 3) {
                    taskListState.animateScrollToItem(3)
                }
            }

            // Auto-scroll ONLY when a new task is actually created in the current active list while input is active
            LaunchedEffect(filteredTasks.size, selectedListId) {
                if (selectedListId != lastListIdForTaskAdd) {
                    lastListIdForTaskAdd = selectedListId
                    previousTaskCount = filteredTasks.size
                } else if (filteredTasks.size > previousTaskCount && isInputActive) {
                    previousTaskCount = filteredTasks.size
                    delay(50)
                    val bottomIndex = 5 + filteredTasks.size
                    taskListState.animateScrollToItem(bottomIndex)
                } else {
                    previousTaskCount = filteredTasks.size
                }
            }

            LaunchedEffect(isInputActive, isImeOpen) {
                if (isInputActive) {
                    val bottomIndex = if (filteredTasks.isEmpty()) 6 else (5 + filteredTasks.size)
                    delay(50)
                    taskListState.animateScrollToItem(bottomIndex)
                    delay(180)
                    taskListState.animateScrollToItem(bottomIndex)
                } else {
                    if (taskListState.firstVisibleItemIndex > 2) {
                        taskListState.animateScrollToItem(0)
                    }
                }
            }

            // Entire page below top bar is inside unified LazyColumn
            LazyColumn(
                state = taskListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "header_greeting") {
                    Column {
                        val isDemoActive by com.example.demo.DemoMode.isDemoActive.collectAsState()
                        if (isDemoActive) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceCardLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(NeonBlue)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "DEMO MODE (Development Preview • Sharing off)",
                                            color = NeonBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "Exit",
                                        color = NeonPink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { com.example.demo.DemoMode.exitDemoMode() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("exit_demo_mode_button")
                                    )
                                }
                            }
                        }

                        // Search Bar (Expandable)
                        AnimatedVisibility(visible = showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search tasks and projects...") },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = NeonPink,
                                    unfocusedBorderColor = SurfaceCardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .testTag("search_text_input")
                            )
                        }

                        // Greeting Header
                        Text(
                            text = "What's up, $firstName!",
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                item(key = "projects_section") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PROJECTS",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (selectedProjectId != null) {
                                val activeProj = combinedProjects.firstOrNull { it.id == selectedProjectId }
                                if (activeProj != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${activeProj.title}",
                                        color = NeonPink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Horizontal Carousel of Projects
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(combinedProjects, key = { it.id }) { project ->
                                val projectTasks = combinedTasks.filter { it.projectId == project.id }
                                val doneCount = projectTasks.count { it.isCompleted }
                                CategoryCard(
                                    project = project,
                                    taskCount = projectTasks.size,
                                    completedCount = doneCount,
                                    isSelected = selectedProjectId == project.id,
                                    onClick = {
                                        if (selectedProjectId == project.id) {
                                            viewModel.selectProject(null)
                                        } else {
                                            viewModel.selectProject(project.id)
                                        }
                                    },
                                    onEdit = { projectToEdit = project },
                                    isSharedWithMe = project.id in sharedWithMeIds,
                                    isSharedByMe = project.id !in sharedWithMeIds && project.memberEmails.isNotEmpty()
                                )
                            }
                            item {
                                AddProjectCard(onClick = { showCreateProjectDialog = true })
                            }
                        }
                    }
                }

                item(key = "lists_section") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LISTS",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (selectedListId != null) {
                                val activeList = availableLists.firstOrNull { it.id == selectedListId }
                                if (activeList != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${activeList.title}",
                                        color = NeonBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (availableLists.isNotEmpty()) {
                                FilterChip(
                                    selected = selectedListId == null,
                                    onClick = { viewModel.selectList(null) },
                                    label = { Text("All Lists (${filteredTasks.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonPink,
                                        selectedLabelColor = Color.White,
                                        containerColor = SurfaceCard,
                                        labelColor = TextSecondary
                                    )
                                )
                                availableLists.forEach { taskList ->
                                    val count = combinedTasks.count { it.listId == taskList.id }
                                    val isSelected = selectedListId == taskList.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                listToEdit = taskList
                                            } else {
                                                viewModel.selectList(taskList.id)
                                            }
                                        },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${taskList.title} ($count)")
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit ${taskList.title}",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonBlue,
                                            selectedLabelColor = Color.Black,
                                            containerColor = SurfaceCard,
                                            labelColor = TextSecondary
                                        )
                                    )
                                }
                            }

                            FilterChip(
                                selected = false,
                                onClick = {
                                    if (combinedProjects.isEmpty()) showCreateProjectDialog = true
                                    else showCreateListDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add List",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "List",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SurfaceCard,
                                    selectedLabelColor = TextSecondary,
                                    containerColor = SurfaceCard,
                                    labelColor = TextSecondary
                                ),
                                border = BorderStroke(1.dp, SurfaceCardBorder),
                                modifier = Modifier.testTag("add_list_chip_btn")
                            )
                        }
                    }
                }

                item(key = "todays_tasks_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TODAY'S TASKS",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showSortMenu = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("sort_menu_button")
                            ) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sortOption.displayName,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(SurfaceCardLight)
                            ) {
                                SortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayName, color = TextPrimary) },
                                        onClick = {
                                            viewModel.setSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (filteredTasks.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (selectedListId != null) "No tasks in this list yet" else "All Lists view",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (selectedListId != null) "Type below to quickly add a task to this list"
                                           else "Select a specific list above to quickly add tasks to it",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTasks, key = { it.id }) { task ->
                        val taskList = combinedLists.firstOrNull { it.id == task.listId }
                        val accentColor = try {
                            if (taskList != null && taskList.projectId in sharedWithMeIds) SharedGray
                            else if (taskList != null) Color(android.graphics.Color.parseColor(taskList.colorHex))
                            else NeonBlue
                        } catch (e: Exception) {
                            NeonBlue
                        }

                        // Swipe left or right to delete. The rows below slide up smoothly (animateItem), but only
                        // while a swipe is under way: always-on item animations made the list flicker when the
                        // keyboard opened or a task was added.
                        SwipeToDeleteBox(
                            onDelete = { viewModel.deleteTask(task.id) },
                            onSwipeActiveChanged = { active -> swipeInProgress = active },
                            modifier = if (animateRowPlacement) {
                                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                            } else {
                                Modifier
                            }
                        ) {
                            TaskRowItem(
                                task = task,
                                accentColor = accentColor,
                                onToggleCompleted = {
                                    if (!task.isCompleted) {
                                        SoundEffectHelper.playTaskDone(context)
                                    }
                                    viewModel.toggleTaskCompletion(task)
                                },
                                onClick = { selectedTaskForDetail = task },
                                onShare = null
                            )
                        }
                    }
                }

                // Quick Add Task Card - ONLY shown when a specific list is selected!
                if (selectedListId != null) {
                    val activeTaskList = combinedLists.firstOrNull { it.id == selectedListId }
                    val listColor = try {
                        if (activeTaskList != null && activeTaskList.projectId in sharedWithMeIds) SharedGray
                        else if (activeTaskList != null) Color(android.graphics.Color.parseColor(activeTaskList.colorHex))
                        else NeonBlue
                    } catch (e: Exception) {
                        NeonBlue
                    }

                    item(key = "quick_add_task_item") {
                        QuickAddTaskRowItem(
                            accentColor = listColor,
                            onActiveChanged = { active -> isQuickAddActive = active },
                            onQuickAdd = { title ->
                                val targetListId = selectedListId!!
                                val targetProjectId = selectedProjectId
                                    ?: combinedLists.firstOrNull { it.id == targetListId }?.projectId
                                    ?: combinedProjects.firstOrNull()?.id

                                if (targetProjectId != null) {
                                    viewModel.createTask(targetListId, targetProjectId, title)
                                }
                            }
                        )
                    }
                }

                item(key = "bottom_spacer") {
                    val bottomSpacing = if (isInputActive) 16.dp else 85.dp
                    Spacer(modifier = Modifier.height(bottomSpacing))
                }
            }
        }

        // Speed Dial FAB overlay and action button (anchored at bottom-right, full screen scrim)
        if (!isInputActive) {
            SpeedDialFab(
                isOpen = speedDialOpen,
                onToggle = { speedDialOpen = !speedDialOpen },
                onAddProject = { showCreateProjectDialog = true },
                onAddList = {
                    if (combinedProjects.isEmpty()) {
                        showCreateProjectDialog = true
                    } else {
                        showCreateListDialog = true
                    }
                },
                onAddTask = {
                    if (combinedLists.isEmpty()) {
                        if (combinedProjects.isEmpty()) showCreateProjectDialog = true
                        else showCreateListDialog = true
                    } else {
                        showCreateTaskDialog = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Floating notification banner overlay (high z-order, does not displace content)
        AnimatedVisibility(
            visible = floatingBanner != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 58.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCardLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.dismissFloatingBanner() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = highlightNotificationKeywords(floatingBanner.orEmpty()),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.dismissFloatingBanner() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showCreateProjectDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateProjectDialog = false },
            onConfirm = { title, desc, colorHex ->
                viewModel.createProject(title, desc, colorHex)
                showCreateProjectDialog = false
            }
        )
    }

    if (showCreateListDialog) {
        CreateListDialog(
            projects = combinedProjects,
            initialProjectId = selectedProjectId,
            onDismiss = { showCreateListDialog = false },
            onConfirm = { projId, title, colorHex ->
                viewModel.createList(projId, title, colorHex)
                showCreateListDialog = false
            },
            colorLockedProjectIds = sharedWithMeIds
        )
    }

    if (showCreateTaskDialog) {
        CreateTaskDialog(
            lists = combinedLists,
            initialListId = selectedListId,
            onDismiss = { showCreateTaskDialog = false },
            onConfirm = { listId, projId, title, notes ->
                viewModel.createTask(listId, projId, title, notes)
                showCreateTaskDialog = false
            }
        )
    }

    if (selectedTaskForDetail != null) {
        val selected = selectedTaskForDetail!!
        // Always show the live state of the task (e.g. its completion circle after a tap),
        // not the copy that was stored when the dialog was opened.
        val task = allTasks.firstOrNull { it.id == selected.id } ?: selected
        TaskDetailDialog(
            task = task,
            allLists = combinedLists,
            allProjects = combinedProjects,
            onDismiss = { selectedTaskForDetail = null },
            onToggleCompleted = {
                if (!task.isCompleted) {
                    SoundEffectHelper.playTaskDone(context)
                }
                viewModel.toggleTaskCompletion(task)
            },
            onUpdateTask = { updated -> viewModel.updateTask(updated) },
            onReassignList = { editedTask, targetListId, targetProjId ->
                viewModel.reassignTask(editedTask, targetListId, targetProjId)
                selectedTaskForDetail = null
            },
            onDeleteTask = {
                viewModel.deleteTask(task.id)
                selectedTaskForDetail = null
            }
        )
    }

    if (shareEntityTarget != null) {
        val (type, id, title) = shareEntityTarget!!
        ShareEntityDialog(
            entityType = "project",
            entityTitle = title,
            entityId = id,
            onDismiss = { shareEntityTarget = null },
            onShareWithEmail = { email ->
                viewModel.shareProject(id, email)
                shareEntityTarget = null
            }
        )
    }

    if (projectToEdit != null) {
        val project = projectToEdit!!
        EditProjectDialog(
            project = project,
            onDismiss = { projectToEdit = null },
            onSave = { updated ->
                viewModel.updateProject(updated)
                projectToEdit = null
            },
            onDelete = { projId ->
                viewModel.deleteProject(projId)
                projectToEdit = null
            },
            onShare = {
                shareEntityTarget = Triple("project", project.id, project.title)
                projectToEdit = null
            },
            onUnshare = { email ->
                viewModel.unshareProject(project.id, email)
            },
            allowColorChange = project.id !in sharedWithMeIds
        )
    }

    if (listToEdit != null) {
        val list = listToEdit!!
        EditListDialog(
            list = list,
            projects = combinedProjects,
            onDismiss = { listToEdit = null },
            onSave = { updated ->
                viewModel.updateList(updated)
                listToEdit = null
            },
            onDelete = { listId ->
                viewModel.deleteList(listId)
                listToEdit = null
            },
            allowColorChange = list.projectId !in sharedWithMeIds
        )
    }

    if (showNotificationsPopover) {
        com.example.ui.dialogs.NotificationsPopoverDialog(
            notifications = notifications,
            onDismiss = { showNotificationsPopover = false },
            onClearAll = {
                viewModel.clearNotifications()
                showNotificationsPopover = false
            }
        )
    }
}
