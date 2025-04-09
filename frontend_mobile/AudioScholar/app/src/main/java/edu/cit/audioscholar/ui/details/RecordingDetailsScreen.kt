package edu.cit.audioscholar.ui.details

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items // Ensure this import is present
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import edu.cit.audioscholar.R
import kotlinx.coroutines.launch
import android.util.Log // <-- Added this import

// Helper function to get filename from Uri
private fun getFileNameFromUri(contentResolver: ContentResolver, uri: Uri): String? {
    var fileName: String? = null
    try { // Add try-catch for potential SecurityException or other issues
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FileNameHelper", "Error getting filename from URI: $uri", e) // Log usage
        // Optionally return a default error name or null
        fileName = "Error_Fetching_Name"
    }
    return fileName
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailsScreen(
    navController: NavHostController,
    viewModel: RecordingDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    // --- Activity Result Launcher for File Picker ---
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val fileName = getFileNameFromUri(contentResolver, uri)
                viewModel.setAttachedPowerPointFile(fileName) // Pass filename (or null/error) to VM
            } else {
                // Optional: Show a message if the user cancelled
                scope.launch { snackbarHostState.showSnackbar("File selection cancelled.") }
                viewModel.setAttachedPowerPointFile(null) // Ensure state is cleared if cancelled
            }
        }
    )

    // --- Effect to Launch File Picker ---
    LaunchedEffect(Unit) { // Re-triggers if ViewModel is recreated, which is usually fine
        viewModel.triggerFilePicker.collect {
            // Define the MIME types for PowerPoint files
            val mimeTypes = arrayOf(
                "application/vnd.ms-powerpoint", // .ppt
                "application/vnd.openxmlformats-officedocument.presentationml.presentation" // .pptx
            )
            try {
                filePickerLauncher.launch(mimeTypes)
            } catch (e: Exception) {
                Log.e("FilePickerLaunch", "Error launching file picker", e) // Log usage
                scope.launch { snackbarHostState.showSnackbar("Could not open file picker.") }
            }
        }
    }

    // --- Effects for Snackbar and Clipboard ---
    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                viewModel.consumeError()
            }
        }
    }
    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                viewModel.consumeInfoMessage()
            }
        }
    }
    LaunchedEffect(uiState.textToCopy) {
        uiState.textToCopy?.let { text ->
            clipboardManager.setText(AnnotatedString(text))
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.details_copy_success_message),
                    duration = SnackbarDuration.Short
                )
            }
            viewModel.consumeTextToCopy()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_recording_details)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::requestDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.cd_delete_recording_action),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && !uiState.showDeleteConfirmation && uiState.filePath.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.filePath.isEmpty() && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.details_error_loading), color = MaterialTheme.colorScheme.error)
            }
        }
        else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // --- Recording Title and Metadata ---
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = LocalContentColor.current.copy(alpha = 0.7f))
                    Text(
                        text = uiState.dateCreated,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = LocalContentColor.current.copy(alpha = 0.7f))
                    Text(
                        text = uiState.durationFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- Playback Controls ---
                Text(stringResource(R.string.details_playback_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = viewModel::onPlayPauseToggle) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = if (uiState.isPlaying) stringResource(R.string.cd_pause_playback) else stringResource(R.string.cd_play_playback),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = uiState.playbackProgress,
                        onValueChange = viewModel::onSeek,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${uiState.currentPositionFormatted} / ${uiState.durationFormatted}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- Summary Section ---
                Text(stringResource(R.string.details_summary_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.details_summary_status_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            when (uiState.summaryStatus) {
                                SummaryStatus.IDLE -> {
                                    Text("Not started", style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
                                }
                                SummaryStatus.PROCESSING -> {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Processing...", style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
                                }
                                SummaryStatus.READY -> {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.cd_summary_ready), tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ready", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                                }
                                SummaryStatus.FAILED -> {
                                    Icon(Icons.Filled.Error, contentDescription = stringResource(R.string.cd_summary_failed), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Failed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.summaryText.ifEmpty { stringResource(R.string.details_summary_placeholder) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.summaryText.isEmpty() && uiState.summaryStatus != SummaryStatus.PROCESSING) LocalContentColor.current.copy(alpha = 0.5f) else LocalContentColor.current
                        )
                        if (uiState.summaryStatus == SummaryStatus.READY) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = viewModel::onCopySummaryAndNotes,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_summary_notes),
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.details_summary_copy_button))
                            }
                        }
                    }
                }

                // --- AI Notes Section (Conditional) ---
                if (uiState.summaryStatus == SummaryStatus.READY && uiState.aiNotesText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.details_ai_notes_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = uiState.aiNotesText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- PowerPoint Section ---
                Text(stringResource(R.string.details_powerpoint_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentAttachment = uiState.attachedPowerPoint
                    if (currentAttachment != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                            Icon(Icons.Filled.Attachment, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentAttachment,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.details_powerpoint_none_attached),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                        )
                    }
                    Button(onClick = {
                        if (currentAttachment == null) {
                            viewModel.requestAttachPowerPoint() // Trigger the file picker flow
                        } else {
                            viewModel.detachPowerPoint() // Detach if already attached
                        }
                    }) {
                        val icon = if (currentAttachment == null) Icons.Filled.AttachFile else Icons.Filled.LinkOff
                        val textRes = if (currentAttachment == null) R.string.details_powerpoint_attach_button else R.string.details_powerpoint_detach_button
                        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(textRes))
                    }
                }

                // --- YouTube Recommendations Section (Conditional) ---
                if (uiState.summaryStatus == SummaryStatus.READY && uiState.youtubeRecommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.details_youtube_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(items = uiState.youtubeRecommendations, key = { it.id }) { video ->
                            YouTubeRecommendationCard(video = video)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp)) // Bottom padding
            }
        }


        // --- Delete Confirmation Dialog ---
        if (uiState.showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text(stringResource(R.string.dialog_delete_title)) },
                text = { Text(stringResource(R.string.dialog_delete_message_details, uiState.title)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.confirmDelete()
                            navController.navigateUp()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (uiState.isLoading && uiState.showDeleteConfirmation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.dialog_action_delete))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) {
                        Text(stringResource(R.string.dialog_action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun YouTubeRecommendationCard(
    video: MockYouTubeVideo
) {
    Card(
        modifier = Modifier.width(180.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Image(
                painter = painterResource(id = video.thumbnailUrl),
                contentDescription = video.title,
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    minLines = 2
                )
            }
        }
    }
}