package edu.cit.audioscholar.ui.recording

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import edu.cit.audioscholar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    navController: NavHostController,
    viewModel: RecordingViewModel = hiltViewModel(),
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
            val audioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] == true
            viewModel.onPermissionResult(granted = audioGranted, permissionType = Manifest.permission.RECORD_AUDIO)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationGranted = permissionsMap[Manifest.permission.POST_NOTIFICATIONS] == true
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
        }
    )


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
                title = { Text(stringResource(id = R.string.recording_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.cd_open_navigation_drawer)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (uiState.isRecording) {
                AudioWaveformVisualizer(
                    amplitude = uiState.currentAmplitude,
                    isPaused = uiState.isPaused,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
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
                            if (uiState.permissionGranted) {
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
                    if (uiState.isRecording && !uiState.isPaused) {
                        Button(
                            onClick = { viewModel.pauseRecording() },
                            enabled = !uiState.showStopConfirmationDialog && !uiState.showCancelConfirmationDialog
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.cd_pause_button))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.action_pause))
                        }
                    }

                    if (uiState.isRecording && uiState.isPaused) {
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

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    amplitude: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 50,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    minBarHeight: Dp = 4.dp,
    lowAmplitudeColor: Color = Color.Unspecified,
    highAmplitudeColor: Color = Color.Unspecified,
    barAlpha: Float = 0.4f,
    amplitudeScaleFactor: Float = 4.0f
) {
    val actualLowColor = lowAmplitudeColor.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.secondary
    val actualHighColor = highAmplitudeColor.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.error

    val amplitudeHistory = remember { mutableStateListOf<Float>().apply { addAll(List(barCount) { 0f }) } }

    LaunchedEffect(amplitude, isPaused) {
        if (!isPaused) {
            amplitudeHistory.add(amplitude)
            if (amplitudeHistory.size > barCount) {
                amplitudeHistory.removeAt(0)
            }
            while (amplitudeHistory.size < barCount) {
                amplitudeHistory.add(0, 0f)
            }
            while (amplitudeHistory.size > barCount) {
                amplitudeHistory.removeAt(0)
            }
        }
    }

    val currentHistory: List<Float> = amplitudeHistory.toList()

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val totalBarWidthPx = barWidth.toPx()
        val totalGapWidthPx = barGap.toPx()
        val segmentWidth = totalBarWidthPx + totalGapWidthPx
        val totalRequiredWidth = (segmentWidth * barCount) - totalGapWidthPx
        val startOffset = (canvasWidth - totalRequiredWidth) / 2f
        val minHeightPx = minBarHeight.toPx()

        drawContext.canvas.save()

        for (i in 0 until barCount) {
            val historicalAmplitude = currentHistory.getOrElse(i) { 0f }
            val visuallyScaledAmplitude = (historicalAmplitude * amplitudeScaleFactor).coerceIn(0f, 1f)

            val barHeight = (minHeightPx + (visuallyScaledAmplitude * (canvasHeight - minHeightPx)))
                .coerceIn(minHeightPx, canvasHeight)

            val barColor = lerp(actualLowColor, actualHighColor, visuallyScaledAmplitude)

            val barX = startOffset + i * segmentWidth
            val barY = (canvasHeight - barHeight) / 2f

            drawRoundRect(
                color = barColor.copy(alpha = barAlpha),
                topLeft = Offset(barX, barY),
                size = Size(totalBarWidthPx, barHeight),
                cornerRadius = CornerRadius(x = totalBarWidthPx / 2, y = totalBarWidthPx / 2)
            )
        }
        drawContext.canvas.restore()
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