package edu.cit.audioscholar.ui.details

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import dev.jeziellago.compose.markdowntext.MarkdownText
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import edu.cit.audioscholar.ui.details.NavigationEvent
import edu.cit.audioscholar.ui.main.Screen

private fun getFileNameFromUri(contentResolver: ContentResolver, uri: Uri): String? {
    var fileName: String? = null
    try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FileNameHelper", "Error getting filename from URI: $uri", e)
        fileName = "Error_Fetching_Name"
    }
    return fileName
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("UrlLauncher", "No activity found to handle URL: $url", e)
        Toast.makeText(context, "Could not open link.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("UrlLauncher", "Error opening URL: $url", e)
        Toast.makeText(context, "Error opening link.", Toast.LENGTH_SHORT).show()
    }
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

    val powerPointLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            viewModel.onPowerPointSelected(uri)
        }
    )
    LaunchedEffect(Unit) {
        viewModel.triggerFilePicker.collect {
            try {
                powerPointLauncher.launch(arrayOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
            } catch (e: ActivityNotFoundException) {
                Log.e("RecordingDetailsScreen", "No activity found to handle PowerPoint selection", e)
                Toast.makeText(context, "No app found to select PowerPoint files.", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.infoMessageEvent.collectLatest { message: String? ->
            message?.let {
                scope.launch {
                    snackbarHostState.showSnackbar(it)
                }
                viewModel.consumeInfoMessage()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collectLatest { error: String? ->
            error?.let {
                scope.launch {
                    snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
                }
                viewModel.consumeError()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openUrlEvent.collect { url ->
            openUrl(context, url)
        }
    }

    LaunchedEffect(uiState.textToCopy) {
        uiState.textToCopy?.let { text ->
            Log.d("RecordingDetailsScreen", "textToCopy state observed with text. Copying to clipboard.")
            clipboardManager.setText(AnnotatedString(text))
            scope.launch {
                snackbarHostState.showSnackbar(uiState.infoMessage ?: "Copied to clipboard!")
            }
            viewModel.consumeTextToCopy()
            viewModel.consumeInfoMessage()
        }
    }

    LaunchedEffect(key1 = navController, key2 = viewModel) {
        viewModel.navigationEvent.collectLatest { event ->
            when (event) {
                is NavigationEvent.NavigateToLibrary -> {
                    Log.d("RecordingDetailsScreen", "Received NavigateToLibrary event. Navigating...")
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.RecordingDetails.ROUTE_PATTERN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
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
                    if ((uiState.filePath.isNotEmpty() || uiState.remoteRecordingId != null) && !uiState.isDeleting) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.cd_delete_recording_action),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            when {
                uiState.isLoading && uiState.filePath.isEmpty() && uiState.remoteRecordingId == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Log.d("DetailsScreen", "Showing initial loading indicator (no path/ID yet)")
                    }
                }

                uiState.filePath.isEmpty() && uiState.remoteRecordingId == null && !uiState.isLoading && uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.details_error_loading),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Log.d("DetailsScreen", "Showing critical error loading details: ${uiState.error}")
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        val focusRequester = remember { FocusRequester() }
                        val focusManager = LocalFocusManager.current

                        Box {
                            if (uiState.isEditingTitle) {
                                BasicTextField(
                                    value = uiState.editableTitle,
                                    onValueChange = viewModel::onTitleChanged,
                                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = LocalContentColor.current
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            viewModel.onTitleSaveRequested()
                                            focusManager.clearFocus()
                                        }
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { }
                                )
                                LaunchedEffect(Unit) {
                                    focusRequester.requestFocus()
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = uiState.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!uiState.isProcessing && !uiState.isDeleting) {
                                        if (!uiState.isCloudSource) {
                                            IconButton(
                                                onClick = viewModel::onTitleEditRequested,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Edit,
                                                    contentDescription = "Edit title",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))
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

                        Text(stringResource(R.string.details_playback_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        val isPlaybackEnabled = uiState.isPlaybackReady
                        if (uiState.filePath.isNotEmpty() || uiState.storageUrl != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = viewModel::onPlayPauseToggle,
                                    enabled = isPlaybackEnabled
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                        contentDescription = if (uiState.isPlaying) stringResource(R.string.cd_pause_playback) else stringResource(R.string.cd_play_playback),
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Slider(
                                    value = uiState.playbackProgress,
                                    onValueChange = viewModel::onSeek,
                                    modifier = Modifier.weight(1f),
                                    enabled = isPlaybackEnabled
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${uiState.currentPositionFormatted} / ${uiState.durationFormatted}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Text("Playback unavailable.", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.filePath.isNotEmpty() && uiState.remoteRecordingId == null && !uiState.isProcessing) {
                            Button(
                                onClick = viewModel::onProcessRecordingClicked,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isProcessing
                            ) {
                                Icon(
                                    Icons.Filled.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.details_process_recording_button))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (uiState.showCloudInfo || uiState.summaryStatus != SummaryStatus.IDLE) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.details_summary_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.summaryStatus != SummaryStatus.IDLE) {
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
                                                SummaryStatus.PROCESSING -> {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
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
                                                SummaryStatus.IDLE -> {}
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (uiState.summaryStatus == SummaryStatus.READY) {
                                            MarkdownText(
                                                markdown = uiState.summaryText.ifBlank { stringResource(R.string.details_summary_placeholder) },
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (uiState.summaryText.isNotEmpty() || uiState.glossaryItems.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = viewModel::onCopySummaryAndNotes,
                                                    modifier = Modifier.align(Alignment.End),
                                                    enabled = !uiState.isProcessing
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
                                        } else if (uiState.summaryStatus == SummaryStatus.FAILED) {
                                            Text(
                                                text = uiState.error ?: "Failed to load summary.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (uiState.showCloudInfo || uiState.summaryStatus != SummaryStatus.IDLE) {
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.details_notes_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(modifier = Modifier.padding(16.dp).fillMaxWidth().defaultMinSize(minHeight = 50.dp)) {
                                    when (uiState.summaryStatus) {
                                        SummaryStatus.PROCESSING -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Generating notes...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = LocalContentColor.current.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        SummaryStatus.READY -> {
                                            if (uiState.glossaryItems.isNotEmpty()) {
                                                Column {
                                                    uiState.glossaryItems.forEachIndexed { index, item ->
                                                        GlossaryItemView(item = item)
                                                        if (index < uiState.glossaryItems.lastIndex) {
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                        }
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.details_notes_placeholder),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = LocalContentColor.current.copy(alpha = 0.5f),
                                                    modifier = Modifier.align(Alignment.Center)
                                                )
                                            }
                                        }
                                        SummaryStatus.FAILED -> {
                                            Text(
                                                text = stringResource(R.string.details_notes_failed),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                        SummaryStatus.IDLE -> {
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.details_powerpoint_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentAttachment = uiState.attachedPowerPoint
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = currentAttachment ?: stringResource(R.string.details_powerpoint_none_attached),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (currentAttachment == null) LocalContentColor.current.copy(alpha = 0.7f) else LocalContentColor.current
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                val buttonsEnabled = !uiState.isProcessing && !uiState.isDeleting && !uiState.isCloudSource
                                Button(
                                    onClick = {
                                        if (currentAttachment == null) {
                                            viewModel.requestAttachPowerPoint()
                                        } else {
                                            viewModel.detachPowerPoint()
                                        }
                                    },
                                    enabled = buttonsEnabled
                                ) {
                                    val icon = if (currentAttachment == null) Icons.Filled.AttachFile else Icons.Filled.LinkOff
                                    val textRes = if (currentAttachment == null) R.string.details_powerpoint_attach_button else R.string.details_powerpoint_detach_button
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(textRes))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.showCloudInfo || uiState.recommendationsStatus != RecommendationsStatus.IDLE) {
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.details_youtube_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            when(uiState.recommendationsStatus) {
                                RecommendationsStatus.LOADING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Loading recommendations...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LocalContentColor.current.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                RecommendationsStatus.READY -> {
                                    if (uiState.youtubeRecommendations.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            items(items = uiState.youtubeRecommendations, key = { it.recommendationId ?: it.videoId ?: it.hashCode() }) { video ->
                                                YouTubeRecommendationCard(video = video, onClick = { viewModel.onWatchYouTubeVideo(video) })
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "No relevant videos found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LocalContentColor.current.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                RecommendationsStatus.FAILED -> {
                                    Text(
                                        text = if (uiState.error?.contains("Recommendations Error") == true) uiState.error!! else "Failed to load recommendations.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                RecommendationsStatus.IDLE -> {
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(enabled = false, onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.uploadProgressPercent != null) {
                            LinearProgressIndicator(
                                progress = { (uiState.uploadProgressPercent ?: 0) / 100f },
                                modifier = Modifier.width(150.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "Uploading ${uiState.uploadProgressPercent}%",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = when {
                                    uiState.summaryStatus == SummaryStatus.PROCESSING -> stringResource(R.string.details_processing_data)
                                    uiState.recommendationsStatus == RecommendationsStatus.LOADING -> "Fetching recommendations..."
                                    else -> "Processing..."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { if (!uiState.isDeleting) viewModel.cancelDelete() },
                title = { Text(stringResource(R.string.dialog_delete_title)) },
                text = { Text(stringResource(R.string.dialog_delete_message_details, uiState.title)) },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !uiState.isDeleting
                    ) {
                        if (uiState.isDeleting) {
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
                    TextButton(
                        onClick = viewModel::cancelDelete,
                        enabled = !uiState.isDeleting
                    ) {
                        Text(stringResource(R.string.dialog_action_cancel))
                    }
                }
            )
        }

    }
}


@Composable
fun GlossaryItemView(item: GlossaryItemDto) {
    Column {
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(item.term ?: "Unknown Term")
                }
                append(": ")
                append(item.definition ?: "No definition available.")
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun YouTubeRecommendationCard(
    video: RecommendationDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title ?: "YouTube video thumbnail",
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_youtubeplaceholder_quantum),
                error = painterResource(id = R.drawable.ic_youtubeplaceholder_quantum)
            )
            Column(modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = video.title ?: "No Title",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}