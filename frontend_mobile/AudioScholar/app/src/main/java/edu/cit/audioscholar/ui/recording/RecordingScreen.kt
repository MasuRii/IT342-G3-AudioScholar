package edu.cit.audioscholar.ui.recording

// --- Imports ---
import android.Manifest // Needed for permission constant
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope // Needed for snackbar scope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // Needed for permission rationale check and opening settings
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.launch // Needed for snackbar scope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    navController: NavHostController,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current // Get context for checking rationale and opening settings
    val snackbarHostState = remember { SnackbarHostState() } // State for Snackbar messages
    val scope = rememberCoroutineScope() // Coroutine scope for launching snackbar

    // --- Permission Handling ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            // Determine if rationale should be shown (this check is often done *before* launching again)
            // For simplicity here, we pass 'false' as shouldShowRationale,
            // relying on the ViewModel's logic for permanent denial message.
            // A more robust implementation might check shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.RECORD_AUDIO)
            // *before* launching the request if permission was previously denied.
            viewModel.onPermissionResult(granted = isGranted, shouldShowRationale = false) // Let ViewModel handle the logic

            if (!isGranted) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.permission_denied_message), // Add this string resource
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    )

    // --- Effect for Snackbar Messages ---
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            val result = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = if (errorMessage.contains("permanently denied")) context.getString(R.string.action_open_settings) else null, // Add string resource
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                // User clicked "Open Settings"
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
            // Optional: Clear the error in the ViewModel after showing it
            // viewModel.clearError() // You would need to add this function to the ViewModel
        }
    }

    LaunchedEffect(uiState.recordingFilePath) {
        uiState.recordingFilePath?.let { path ->
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.recording_saved_message, path), // Add string resource
                duration = SnackbarDuration.Short
            )
            // Optional: Clear the path in the ViewModel after showing confirmation
            // viewModel.clearRecordingPath() // Add this function to ViewModel
        }
    }

    // --- UI ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, // Add SnackbarHost
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.recording_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Prevent navigating back while recording? Optional.
                        if (!uiState.isRecording) {
                            navController.navigateUp()
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.stop_recording_before_leaving), // Add string resource
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround // Pushes elements apart
        ) {
            Spacer(modifier = Modifier.height(64.dp)) // Pushes timer down a bit

            // Timer Display
            Text(
                text = uiState.elapsedTimeFormatted,
                style = MaterialTheme.typography.displayMedium,
                color = if (uiState.isRecording) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )

            // Status Text
            Text(
                text = when {
                    uiState.isRecording -> stringResource(R.string.status_recording)
                    uiState.recordingFilePath != null -> stringResource(R.string.status_saved) // Show "Saved" after stopping
                    !uiState.permissionGranted -> stringResource(R.string.status_permission_needed) // Prompt for permission
                    else -> stringResource(R.string.status_tap_to_record)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Record/Stop Button
            Button( // Using Button instead of IconButton for better visibility/accessibility
                onClick = {
                    if (uiState.isRecording) {
                        // Stop recording
                        viewModel.toggleRecording()
                    } else {
                        // Start recording or request permission
                        if (uiState.permissionGranted) {
                            viewModel.toggleRecording()
                        } else {
                            // Request permission
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier
                    .size(100.dp) // Larger button
                    .padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge, // Circular button
                contentPadding = PaddingValues(0.dp) // Remove default padding for icon
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (uiState.isRecording) {
                        stringResource(R.string.cd_stop_button)
                    } else {
                        stringResource(R.string.cd_record_button)
                    },
                    modifier = Modifier.size(48.dp), // Adjust icon size
                    tint = MaterialTheme.colorScheme.onPrimary // Icon color on button background
                )
            }

            // Placeholder for potential future elements like waveform visualizer
            Spacer(modifier = Modifier.height(64.dp)) // Pushes button up a bit
        }
    }
}

// --- Preview (Remains mostly the same, but add SnackbarHostState) ---
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Recording Screen Preview (Not Recording)")
@Composable
fun RecordingScreenPreviewNotRecording() {
    AudioScholarTheme {
        val previewState = RecordingUiState(
            isRecording = false,
            elapsedTimeFormatted = "00:00:00",
            permissionGranted = true // Assume permission granted for this preview state
        )
        val dummyNavController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = { /* ... TopAppBar as before ... */ }
        ) { paddingValues ->
            // Column layout as before, using previewState
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                Text(
                    text = previewState.elapsedTimeFormatted,
                    style = MaterialTheme.typography.displayMedium,
                    color = LocalContentColor.current // Default color when not recording
                )
                Text(
                    text = stringResource(R.string.status_tap_to_record),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = { }, // No action in preview
                    modifier = Modifier.size(100.dp).padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cd_record_button),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Recording Screen Preview (Recording)")
@Composable
fun RecordingScreenPreviewRecording() {
    AudioScholarTheme {
        val previewState = RecordingUiState(
            isRecording = true,
            elapsedTimeFormatted = "00:10:35",
            permissionGranted = true
        )
        val dummyNavController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = { /* ... TopAppBar as before ... */ }
        ) { paddingValues ->
            // Column layout as before, using previewState
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                Text(
                    text = previewState.elapsedTimeFormatted,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary // Primary color when recording
                )
                Text(
                    text = stringResource(R.string.status_recording),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = { }, // No action in preview
                    modifier = Modifier.size(100.dp).padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.cd_stop_button),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}