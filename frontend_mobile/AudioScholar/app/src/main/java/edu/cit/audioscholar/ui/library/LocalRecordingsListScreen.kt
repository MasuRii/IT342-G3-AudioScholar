package edu.cit.audioscholar.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun formatTimestamp(timestampMillis: Long): String {
    val date = Date(timestampMillis)
    val format = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    return format.format(date)
}

private fun formatDuration(durationMillis: Long): String {
    if (durationMillis <= 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun playRecordingExternally(
    context: Context,
    metadata: RecordingMetadata,
    scope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit
) {
    try {
        val file = File(metadata.filePath)
        if (!file.exists()) {
            Log.e("PlayRecording", "File not found: ${metadata.filePath}")
            scope.launch { showSnackbar("Error: Recording file not found.") }
            return
        }

        val authority = "${context.packageName}.provider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Log.d("PlayRecording", "Attempting to launch player for URI: $contentUri")
        context.startActivity(intent)

    } catch (e: ActivityNotFoundException) {
        Log.e("PlayRecording", "No activity found to handle audio intent.", e)
        scope.launch { showSnackbar("No app found to play this audio file.") }
    } catch (e: Exception) {
        Log.e("PlayRecording", "Error launching external player", e)
        scope.launch { showSnackbar("Error playing recording: ${e.localizedMessage}") }
    }
}


@Composable
fun LocalRecordingsListScreen(
    modifier: Modifier = Modifier,
    viewModel: LocalRecordingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val showSnackbar: suspend (String) -> Unit = { message ->
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadRecordings()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMsg ->
            scope.launch {
                showSnackbar(errorMsg)
            }
            viewModel.consumeError()
        }
    }

    uiState.recordingToDelete?.let { recording ->
        DeleteConfirmationDialog(
            metadata = recording,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading && uiState.recordings.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                !uiState.isLoading && uiState.recordings.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.library_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.recordings, key = { it.filePath }) { metadata ->
                            RecordingListItem(
                                metadata = metadata,
                                onItemClick = {
                                    playRecordingExternally(context, it, scope, showSnackbar)
                                },
                                onDeleteClick = { viewModel.requestDeleteConfirmation(it) }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                    if (uiState.isLoading && uiState.recordings.isNotEmpty()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingListItem(
    metadata: RecordingMetadata,
    onItemClick: (RecordingMetadata) -> Unit,
    onDeleteClick: (RecordingMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick(metadata) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = metadata.title ?: metadata.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(metadata.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatDuration(metadata.durationMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { onDeleteClick(metadata) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.cd_delete_recording, metadata.fileName),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    metadata: RecordingMetadata,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.dialog_delete_message,
                    metadata.title ?: metadata.fileName
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.dialog_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_action_cancel))
            }
        }
    )
}