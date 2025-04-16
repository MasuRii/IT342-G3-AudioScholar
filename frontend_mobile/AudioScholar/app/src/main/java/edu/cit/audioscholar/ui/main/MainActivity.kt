package edu.cit.audioscholar.ui.main

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import edu.cit.audioscholar.R
import edu.cit.audioscholar.service.NAVIGATE_TO_EXTRA
import edu.cit.audioscholar.service.UPLOAD_SCREEN_VALUE
import edu.cit.audioscholar.ui.about.AboutScreen
import edu.cit.audioscholar.ui.auth.LoginScreen
import edu.cit.audioscholar.ui.auth.RegistrationScreen
import edu.cit.audioscholar.ui.details.RecordingDetailsScreen
import edu.cit.audioscholar.ui.library.LibraryScreen
import edu.cit.audioscholar.ui.onboarding.OnboardingScreen
import edu.cit.audioscholar.ui.profile.EditProfileScreen
import edu.cit.audioscholar.ui.profile.UserProfileScreen
import edu.cit.audioscholar.ui.recording.RecordingScreen
import edu.cit.audioscholar.ui.settings.SettingsViewModel
import edu.cit.audioscholar.ui.settings.ThemeSetting
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import edu.cit.audioscholar.ui.upload.UploadScreen
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class Screen(val route: String, val labelResId: Int, val icon: ImageVector? = null) {
    object Onboarding : Screen("onboarding", R.string.nav_onboarding, Icons.Filled.Info)
    object Record : Screen("record", R.string.nav_record, Icons.Filled.Mic)
    object Library : Screen("library", R.string.nav_library, Icons.AutoMirrored.Filled.List)
    object Upload : Screen("upload", R.string.nav_upload, Icons.Filled.CloudUpload)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings)
    object Profile : Screen("profile", R.string.nav_profile, Icons.Filled.AccountCircle)
    object EditProfile : Screen("edit_profile", R.string.nav_edit_profile, Icons.Filled.Edit)
    object About : Screen("about", R.string.nav_about, Icons.Filled.Info)
    object Login : Screen("login", R.string.nav_login, Icons.AutoMirrored.Filled.Login)
    object Registration : Screen("registration", R.string.nav_registration, Icons.Filled.PersonAdd)
    object ChangePassword : Screen("change_password", R.string.nav_change_password, Icons.Filled.Password)

    object RecordingDetails : Screen("recording_details/{recordingId}", R.string.nav_recording_details) {
        fun createRoute(recordingId: String) = "recording_details/$recordingId"
    }
}

val screensWithDrawer = listOf(
    Screen.Record.route,
    Screen.Library.route,
    Screen.Upload.route,
    Screen.Settings.route,
    Screen.Profile.route,
    Screen.About.route,
    Screen.ChangePassword.route
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: SharedPreferences

    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var navController: NavHostController

    private val onOnboardingCompleteAction: () -> Unit = {
        with(prefs.edit()) {
            putBoolean(SplashActivity.KEY_ONBOARDING_COMPLETE, true)
            apply()
        }
        if (::navController.isInitialized) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
                launchSingleTop = true
            }
            Log.d("MainActivity", "Onboarding complete. Navigating to Login screen.")
        } else {
            Log.e("MainActivity", "Onboarding complete called but NavController not ready.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called. Intent received: $intent")
        logIntentExtras("onCreate", intent)

        val startDestination = intent?.getStringExtra(SplashActivity.EXTRA_START_DESTINATION)
            ?: run {
                Log.w("MainActivity", "Missing EXTRA_START_DESTINATION from SplashActivity! Using fallback logic.")
                val isOnboardingComplete = prefs.getBoolean(SplashActivity.KEY_ONBOARDING_COMPLETE, false)
                val isLoggedIn = prefs.getBoolean(SplashActivity.KEY_IS_LOGGED_IN, false)
                when {
                    !isOnboardingComplete -> Screen.Onboarding.route
                    !isLoggedIn -> Screen.Login.route
                    else -> Screen.Record.route
                }
            }
        Log.d("MainActivity", "Using start destination determined by SplashActivity (or fallback): $startDestination")

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("MainActivity", "Manually fetched FCM token: $token")
        }

        setContent {
            val themeSetting by settingsViewModel.selectedTheme.collectAsStateWithLifecycle()
            val systemIsDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeSetting) {
                ThemeSetting.Light -> false
                ThemeSetting.Dark -> true
                ThemeSetting.System -> systemIsDark
            }

            AudioScholarTheme(darkTheme = useDarkTheme) {
                navController = rememberNavController()

                LaunchedEffect(intent) {
                    Log.d("MainActivity", "LaunchedEffect in onCreate triggered.")
                    handleNavigationIntent(intent, navController)
                }

                MainAppScreen(
                    navController = navController,
                    startDestination = startDestination,
                    onOnboardingComplete = onOnboardingCompleteAction,
                    prefs = prefs
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called. Intent received: $intent")
        logIntentExtras("onNewIntent", intent)

        setIntent(intent)
        if (::navController.isInitialized) {
            handleNavigationIntent(intent, navController)
        } else {
            Log.e("MainActivity", "onNewIntent called but navController not initialized yet.")
        }
    }

    @Suppress("DEPRECATION")
    private fun logIntentExtras(source: String, intent: Intent?) {
        if (intent == null) {
            Log.d("MainActivity", "[$source] Intent is null.")
            return
        }
        intent.extras?.let { bundle ->
            Log.d("MainActivity", "[$source] Intent extras:")
            for (key in bundle.keySet()) {
                Log.d("MainActivity", "  Key=$key, Value=${bundle.get(key)}")
            }
        } ?: Log.d("MainActivity", "[$source] Intent has no extras.")
    }

    private fun handleNavigationIntent(intent: Intent?, navController: NavHostController) {
        Log.d("MainActivity", "[handleNavigationIntent] Checking intent...")
        logIntentExtras("handleNavigationIntent", intent)

        val navigateTo = intent?.getStringExtra(NAVIGATE_TO_EXTRA)
        Log.d("MainActivity", "[handleNavigationIntent] Value from getExtra(NAVIGATE_TO_EXTRA): $navigateTo")

        val currentRoute = navController.currentDestination?.route
        val isAuthScreen = currentRoute == Screen.Login.route || currentRoute == Screen.Registration.route || currentRoute == Screen.Onboarding.route

        if (navigateTo == UPLOAD_SCREEN_VALUE && !isAuthScreen) {
            if (currentRoute != Screen.Upload.route) {
                Log.d("MainActivity", "Navigating to Upload screen via intent.")
                navController.navigate(Screen.Upload.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            } else {
                Log.d("MainActivity", "Already on Upload screen, no navigation needed from intent.")
            }
            intent?.removeExtra(NAVIGATE_TO_EXTRA)
        } else if (navigateTo == UPLOAD_SCREEN_VALUE && isAuthScreen) {
            Log.d("MainActivity", "Intent requests Upload screen, but currently on Auth/Onboarding. Ignoring.")
            intent?.removeExtra(NAVIGATE_TO_EXTRA)
        }
        else {
            Log.d("MainActivity", "[handleNavigationIntent] Intent does not specify navigation to Upload screen or currently on Auth.")
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    navController: NavHostController,
    startDestination: String,
    onOnboardingComplete: () -> Unit,
    prefs: SharedPreferences
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val gesturesEnabled = currentRoute in screensWithDrawer

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_navigation_logo),
                            contentDescription = stringResource(R.string.cd_app_logo),
                            modifier = Modifier.size(84.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { drawerState.close() }
                                if (currentRoute != Screen.Profile.route) {
                                    navController.navigate(Screen.Profile.route) {
                                        launchSingleTop = true
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        restoreState = true
                                    }
                                }
                                Log.d("DrawerHeader", "Profile section clicked")
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_navigation_profile_placeholder),
                            contentDescription = stringResource(R.string.cd_user_avatar),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.drawer_header_user_name),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.drawer_header_user_email),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                val mainNavItems = listOf(
                    Screen.Record,
                    Screen.Library,
                    Screen.Upload,
                    Screen.Settings,
                    Screen.About
                )

                Spacer(Modifier.height(12.dp))

                mainNavItems.forEach { screen ->
                    screen.icon?.let { icon ->
                        NavigationDrawerItem(
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(screen.labelResId)) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_logout)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        Log.d("DrawerFooter", "Logout clicked - Clearing login state and Navigating to Login")

                        with(prefs.edit()) {
                            putBoolean(SplashActivity.KEY_IS_LOGGED_IN, false)
                            apply()
                        }

                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOnboardingComplete = onOnboardingComplete
                    )
                }
                composable(Screen.Record.route) {
                    RecordingScreen(
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope
                    )
                }
                composable(Screen.Library.route) {
                    LibraryScreen(
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope
                    )
                }
                composable(Screen.Upload.route) {
                    UploadScreen(
                        onNavigateToRecording = {
                            navController.navigate(Screen.Record.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        drawerState = drawerState,
                        scope = scope
                    )
                }
                composable(Screen.Settings.route) {
                    edu.cit.audioscholar.ui.settings.SettingsScreen(
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope
                    )
                }
                composable(Screen.Profile.route) {
                    UserProfileScreen(
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope
                    )
                }
                composable(Screen.EditProfile.route) {
                    EditProfileScreen(
                        navController = navController
                    )
                }
                composable(Screen.About.route) {
                    AboutScreen(navController = navController)
                }
                composable(Screen.Login.route) {
                    LoginScreen(navController = navController)
                }
                composable(Screen.Registration.route) {
                    RegistrationScreen(navController = navController)
                }
                composable(Screen.ChangePassword.route) {
                    edu.cit.audioscholar.ui.settings.ChangePasswordScreen(
                        navController = navController
                    )
                }
                composable(
                    route = Screen.RecordingDetails.route,
                    arguments = listOf(navArgument("recordingId") { type = NavType.StringType })
                ) { backStackEntry ->
                    RecordingDetailsScreen(
                        navController = navController
                    )
                }
            }
        }
    }
}