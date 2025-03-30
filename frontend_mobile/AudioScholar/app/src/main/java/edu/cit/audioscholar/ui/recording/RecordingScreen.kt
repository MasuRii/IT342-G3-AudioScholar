package edu.cit.audioscholar.ui.recording

import android.Manifest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.UploadScreenRoute
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    navController: NavHostController,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            viewModel.onPermissionResult(granted = isGranted, shouldShowRationale = false)

            if (!isGranted) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.permission_denied_message),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    )

    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            val isPermanentDenial = errorMessage.contains("permanently denied")
            val result = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = if (isPermanentDenial) context.getString(R.string.action_open_settings) else null,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && isPermanentDenial) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
            viewModel.consumeErrorMessage()
        }
    }

    LaunchedEffect(uiState.recordingFilePath) {
        uiState.recordingFilePath?.let { path ->
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.recording_saved_message, path),
                duration = SnackbarDuration.Short
            )
            viewModel.consumeSavedMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.recording_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!uiState.isRecording) {
                            navController.navigateUp()
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.stop_recording_before_leaving),
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = uiState.elapsedTimeFormatted,
                    style = MaterialTheme.typography.displayMedium,
                    color = if (uiState.isRecording) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )

                Text(
                    text = when {
                        uiState.isRecording -> stringResource(R.string.status_recording)
                        !uiState.permissionGranted -> stringResource(R.string.status_permission_needed)
                        uiState.recordingFilePath != null && !uiState.isRecording -> stringResource(R.string.status_saved)
                        else -> stringResource(R.string.status_tap_to_record)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (uiState.isRecording) {
                        viewModel.toggleRecording()
                    } else {
                        if (uiState.permissionGranted) {
                            viewModel.toggleRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier
                    .size(100.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (uiState.isRecording) {
                        stringResource(R.string.cd_stop_button)
                    } else {
                        stringResource(R.string.cd_record_button)
                    },
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Button(
                onClick = {
                    if (!uiState.isRecording) {
                        navController.navigate(UploadScreenRoute)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.stop_recording_before_navigating),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                enabled = !uiState.isRecording,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.button_go_to_upload))
            }
        }
    }
}