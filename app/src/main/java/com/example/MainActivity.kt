package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.TaskFlowRepository
import com.example.ui.components.DrawerContent
import com.example.ui.components.ThreeDimensionalDrawerScaffold
import com.example.data.DeviceIdentity
import com.example.ui.AppForegroundTracker
import com.example.ui.NotificationHelper
import com.example.ui.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SharedEntitiesScreen
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.TaskFlowViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Knows whether the app is visible, so tray notifications are only raised in the background
        AppForegroundTracker.register(application)
        DeviceIdentity.init(this)
        NotificationHelper.ensureChannel(this)

        // Mandatory App Check wiring with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // Pre-warm sound effects for instant latency-free feedback
        com.example.ui.SoundEffectHelper.init(this)

        enableEdgeToEdge()

        val firestoreDb = FirebaseFirestore.getInstance(
            getString(R.string.firestore_database_id)
        )
        val repository = TaskFlowRepository(firestoreDb)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepMidnight
                ) {
                    val authViewModel = remember { AuthViewModel(repository) }

                    LaunchedEffect(Unit) {
                        authViewModel.attemptAutoSignIn(this@MainActivity)
                    }

                    val authState by authViewModel.uiState.collectAsState()

                    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { auth ->
                            currentUser = auth.currentUser
                        }
                        Firebase.auth.addAuthStateListener(listener)
                        onDispose {
                            Firebase.auth.removeAuthStateListener(listener)
                        }
                    }

                    val isDemoActive by com.example.demo.DemoMode.isDemoActive.collectAsState()

                    if (!isDemoActive && (currentUser == null || authState is AuthUiState.Unauthenticated)) {
                        AuthScreen(
                            authViewModel = authViewModel,
                            uiState = authState
                        )
                    } else {
                        val user = currentUser
                        val profile = if (isDemoActive) com.example.demo.DemoMode.demoProfile else (authState as? AuthUiState.Authenticated)?.profile
                        val userEmail = if (isDemoActive) com.example.demo.DemoMode.demoProfile.email else user?.email.orEmpty()
                        val taskFlowViewModel = remember(user?.uid ?: "demo_session") {
                            TaskFlowViewModel(repository)
                        }

                        AppMainContent(
                            authViewModel = authViewModel,
                            taskFlowViewModel = taskFlowViewModel,
                            userProfile = profile,
                            userEmail = userEmail
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppMainContent(
    authViewModel: AuthViewModel,
    taskFlowViewModel: TaskFlowViewModel,
    userProfile: com.example.data.model.UserProfile?,
    userEmail: String
) {
    var isDrawerOpen by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") } // "home", "shared", "settings"

    val context = LocalContext.current

    // Ask for the notification permission once (Android 13+), right after sign-in.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result is read via NotificationHelper.canPost() when a notification is raised */ }
    LaunchedEffect(Unit) {
        if (NotificationHelper.needsRuntimePermission() && !NotificationHelper.canPost(context)) {
            val prefs = context.getSharedPreferences("check_app_prefs", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notification_permission_asked", false)) {
                prefs.edit().putBoolean("notification_permission_asked", true).apply()
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Let the server know where to send pushes for this account (re-done on every start; tokens can change)
    LaunchedEffect(taskFlowViewModel) {
        taskFlowViewModel.registerForPush()
    }

    // Tray notifications for changes made elsewhere in shared projects while the app isn't visible.
    // Collected here (not in a single screen) so it works from any screen.
    LaunchedEffect(taskFlowViewModel) {
        taskFlowViewModel.remoteEvents.collect { event ->
            if (!AppForegroundTracker.isInForeground) {
                NotificationHelper.showTaskUpdate(
                    context = context,
                    key = event.key,
                    title = event.trayTitle,
                    text = event.trayText
                )
            }
        }
    }

    // BackHandler for responsive navigation
    BackHandler(enabled = isDrawerOpen || currentScreen != "home") {
        if (isDrawerOpen) {
            isDrawerOpen = false
        } else if (currentScreen != "home") {
            currentScreen = "home"
        }
    }

    ThreeDimensionalDrawerScaffold(
        isDrawerOpen = isDrawerOpen,
        onCloseDrawer = { isDrawerOpen = false },
        drawerContent = {
            DrawerContent(
                userProfile = userProfile,
                userEmail = userEmail,
                currentScreen = currentScreen,
                onNavigate = { route ->
                    currentScreen = route
                },
                onCloseDrawer = { isDrawerOpen = false }
            )
        }
    ) {
        when (currentScreen) {
            "home" -> {
                HomeScreen(
                    userProfile = userProfile,
                    userEmail = userEmail,
                    viewModel = taskFlowViewModel,
                    onOpenDrawer = { isDrawerOpen = true }
                )
            }
            "shared" -> {
                SharedEntitiesScreen(
                    viewModel = taskFlowViewModel,
                    onBack = { currentScreen = "home" }
                )
            }
            "settings" -> {
                SettingsScreen(
                    userProfile = userProfile,
                    authViewModel = authViewModel,
                    onBack = { currentScreen = "home" }
                )
            }
        }
    }
}
