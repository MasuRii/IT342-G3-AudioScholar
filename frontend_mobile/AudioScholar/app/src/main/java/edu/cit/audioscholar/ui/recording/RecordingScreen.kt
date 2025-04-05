package edu.cit.audioscholar.ui.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import edu.cit.audioscholar.R
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

    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsMap ->
            val audioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] ?: false
            viewModel.onPermissionResult(granted = audioGranted, permissionType = Manifest.permission.RECORD_AUDIO)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationGranted = permissionsMap[Manifest.permission.POST_NOTIFICATIONS] ?: false
                viewModel.onPermissionResult(granted = notificationGranted, permissionType = Manifest.permission.POST_NOTIFICATIONS)
                if (audioGranted && !notificationGranted) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.permission_denied_notifications_recording),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }

            if (!audioGranted) {
            } else if (audioGranted) {
                viewModel.startRecording()
            }
        }
    )

    LaunchedEffect(uiState.permissionGranted) {
    }


    LaunchedEffect(uiState.error, uiState.recordingSavedMessage) {
        uiState.error?.let { errorMessage ->
            val isPermanentDenial = errorMessage.contains("permission", ignoreCase = true)
            val result = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = if (isPermanentDenial) context.getString(R.string.action_open_settings) else null,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && isPermanentDenial) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("RecordingScreen", "Failed to open app settings", e)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Could not open settings. Please enable permissions manually.",
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            }
            viewModel.consumeErrorMessage()
        }

        uiState.recordingSavedMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.consumeSavedMessage()
        }
    }

    if (uiState.showTitleDialog) {
        RecordingTitleDialog(
            onConfirm = { title ->
                viewModel.finalizeRecording(title)
            },
            onDismiss = {
                viewModel.finalizeRecording(null)
            }
        )
    }

    if (uiState.showStopConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissStopConfirmation() },
            title = { Text(stringResource(R.string.dialog_stop_title)) },
            text = { Text(stringResource(R.string.dialog_stop_message)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmStopRecording() }
                ) {
                    Text(stringResource(R.string.dialog_action_stop_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissStopConfirmation() }) {
                    Text(stringResource(R.string.dialog_action_cancel))
                }
            }
        )
    }

    if (uiState.showCancelConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCancelConfirmation() },
            title = { Text(stringResource(R.string.dialog_cancel_title)) },
            text = { Text(stringResource(R.string.dialog_cancel_message)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmCancelRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dialog_action_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCancelConfirmation() }) {
                    Text(stringResource(R.string.dialog_action_keep_recording))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.recording_screen_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            Text(
                text = uiState.elapsedTimeFormatted,
                style = MaterialTheme.typography.displayMedium,
                color = when {
                    uiState.isRecording && !uiState.isPaused -> MaterialTheme.colorScheme.primary
                    uiState.isPaused -> MaterialTheme.colorScheme.secondary
                    uiState.showTitleDialog -> MaterialTheme.colorScheme.tertiary
                    else -> LocalContentColor.current.copy(alpha = 0.8f)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = when {
                    uiState.isRecording && !uiState.isPaused -> stringResource(R.string.status_recording)
                    uiState.isPaused -> stringResource(R.string.status_paused)
                    uiState.showTitleDialog -> stringResource(R.string.status_saving)
                    !uiState.permissionGranted -> stringResource(R.string.status_permission_needed)
                    else -> stringResource(R.string.status_tap_to_record)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            LargeFloatingActionButton(
                onClick = {
                    if (uiState.showTitleDialog || uiState.showStopConfirmationDialog || uiState.showCancelConfirmationDialog) return@LargeFloatingActionButton

                    if (uiState.isRecording) {
                        viewModel.requestStopConfirmation()
                    } else {
                        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (audioGranted) {
                            viewModel.startRecording()
                        } else {
                            multiplePermissionsLauncher.launch(permissionsToRequest)
                        }
                    }
                },
                modifier = Modifier.size(100.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (uiState.isRecording) {
                        stringResource(R.string.cd_stop_button)
                    } else {
                        stringResource(R.string.cd_record_button)
                    },
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isRecording && !uiState.isPaused && uiState.supportsPauseResume) {
                    Button(
                        onClick = { viewModel.pauseRecording() },
                        enabled = !uiState.showStopConfirmationDialog && !uiState.showCancelConfirmationDialog
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.cd_pause_button))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_pause))
                    }
                }

                if (uiState.isRecording && uiState.isPaused && uiState.supportsPauseResume) {
                    Button(
                        onClick = { viewModel.resumeRecording() },
                        enabled = !uiState.showStopConfirmationDialog && !uiState.showCancelConfirmationDialog
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_resume_button))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_resume))
                    }

                    Button(
                        onClick = { viewModel.requestCancelConfirmation() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !uiState.showStopConfirmationDialog && !uiState.showCancelConfirmationDialog
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.cd_cancel_button))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingTitleDialog(
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_prompt_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.dialog_title_prompt_label)) },
                placeholder = { Text(stringResource(R.string.dialog_title_prompt_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(title.trim().takeIf { it.isNotEmpty() })
            }) {
                Text(stringResource(R.string.dialog_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_action_skip))
            }
        }
    )
}