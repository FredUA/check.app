package com.example

import android.content.Intent
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
import com.example.data.CheckAppRepository
import com.example.ui.components.DrawerContent
import com.example.ui.components.ThreeDimensionalDrawerScaffold
import com.example.data.DeviceIdentity
import com.example.ui.AppForegroundTracker
import com.example.ui.NotificationHelper
import com.example.ui.ShareLinks
import com.example.ui.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SharedEntitiesScreen
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.CheckAppViewModel
import com.example.widget.CheckListWidget
import com.example.widget.WidgetIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    /** Set when the home-screen widget asks for the "New list" dialog of a project. */
    private var newListProjectId by mutableStateOf<String?>(null)

    /** Set when the app was opened from a project share link. */
    private var linkedProjectId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A recreated activity (rotation, process restore) gets the original intent again: don't re-open the dialog.
        if (savedInstanceState == null) handleWidgetIntent(intent)

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
        val repository = CheckAppRepository(firestoreDb)

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
                        val checkAppViewModel = remember(user?.uid ?: "demo_session") {
                            CheckAppViewModel(repository)
                        }

                        AppMainContent(
                            authViewModel = authViewModel,
                            checkAppViewModel = checkAppViewModel,
                            userProfile = profile,
                            userEmail = userEmail,
                            newListProjectId = newListProjectId,
                            onNewListHandled = { newListProjectId = null },
                            linkedProjectId = linkedProjectId,
                            onLinkedProjectHandled = { linkedProjectId = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Changes made in the app show up in placed widgets as soon as the user leaves it.
        val appContext = applicationContext
        CoroutineScope(Dispatchers.Default).launch { CheckListWidget.refreshAll(appContext) }
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.action == WidgetIntents.ACTION_NEW_LIST) {
            newListProjectId = intent.getStringExtra(WidgetIntents.EXTRA_PROJECT_ID)
        }
        // A share link: remembered until the user is signed in and the project can be opened.
        if (intent?.action == Intent.ACTION_VIEW) {
            ShareLinks.projectIdFrom(intent.data)?.let { linkedProjectId = it }
        }
    }
}

@Composable
fun AppMainContent(
    authViewModel: AuthViewModel,
    checkAppViewModel: CheckAppViewModel,
    userProfile: com.example.data.model.UserProfile?,
    userEmail: String,
    newListProjectId: String? = null,
    onNewListHandled: () -> Unit = {},
    linkedProjectId: String? = null,
    onLinkedProjectHandled: () -> Unit = {}
) {
    var isDrawerOpen by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("home") } // "home", "shared", "settings"

    // A project share link: go home and select that project (or say there's no access).
    val linkContext = LocalContext.current
    LaunchedEffect(linkedProjectId, checkAppViewModel) {
        val projectId = linkedProjectId ?: return@LaunchedEffect
        currentScreen = "home"
        isDrawerOpen = false
        val opened = checkAppViewModel.openProjectFromLink(projectId)
        if (!opened) {
            android.widget.Toast.makeText(
                linkContext,
                "You don't have access to this project. Ask the owner to invite your email.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        onLinkedProjectHandled()
    }

    // The widget's "New list" button: go to the home screen, where the dialog opens.
    LaunchedEffect(newListProjectId) {
        if (newListProjectId != null) {
            currentScreen = "home"
            isDrawerOpen = false
        }
    }

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
    LaunchedEffect(checkAppViewModel) {
        checkAppViewModel.registerForPush()
    }

    // Tray notifications for changes made elsewhere in shared projects while the app isn't visible.
    // Collected here (not in a single screen) so it works from any screen.
    LaunchedEffect(checkAppViewModel) {
        checkAppViewModel.remoteEvents.collect { event ->
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
                    viewModel = checkAppViewModel,
                    onOpenDrawer = { isDrawerOpen = true },
                    newListProjectId = newListProjectId,
                    onNewListHandled = onNewListHandled
                )
            }
            "shared" -> {
                SharedEntitiesScreen(
                    viewModel = checkAppViewModel,
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
