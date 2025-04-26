package edu.cit.audioscholar.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.data.remote.dto.TimestampDto
import edu.cit.audioscholar.ui.main.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.runtime.rememberCoroutineScope

private fun formatTimestampMillis(timestampMillis: Long): String {
    if (timestampMillis <= 0) return "Unknown date"
    val date = Date(timestampMillis)
    val format = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    return format.format(date)
}

private fun formatTimestampDto(timestampDto: TimestampDto?): String {
    if (timestampDto?.seconds == null || timestampDto.seconds <= 0) return "Unknown date"
    val timestampMillis =
        TimeUnit.SECONDS.toMillis(timestampDto.seconds) + TimeUnit.NANOSECONDS.toMillis(
            timestampDto.nanos ?: 0
        )
    val date = Date(timestampMillis)
    val format = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    return format.format(date)
}

private fun formatDurationMillis(durationMillis: Long): String {
    if (durationMillis <= 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", size, units[unitIndex])
}

private fun playCloudRecording(
    context: Context,
    metadata: AudioMetadataDto,
    scope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit,
) {
    val url = metadata.storageUrl
    if (url.isNullOrBlank()) {
        scope.launch { showSnackbar("Error: Recording URL is missing.") }
        return
    }

    try {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
            }
        Log.d("PlayRecording", "Attempting to launch player for cloud URL: $url")
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("PlayRecording", "No activity found to handle audio URL intent.", e)
        scope.launch { showSnackbar("No app found to stream or play this audio URL.") }
    } catch (e: Exception) {
        Log.e("PlayRecording", "Error launching player for cloud URL", e)
        scope.launch { showSnackbar("Error playing recording: ${e.localizedMessage}") }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    navController: NavHostController,
    drawerState: DrawerState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val areAllSelected by viewModel.areAllLocalRecordingsSelected.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val audioPickerLauncher: ActivityResultLauncher<Array<String>> = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri?> ->
            Log.d("LibraryScreen", "Audio picker result (multiple): ${uris.size} items")
            viewModel.onAudioFilesSelected(uris)
        }
    )

    val showSnackbar: suspend (String) -> Unit = { message ->
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
    }

    val tabTitles = listOf(
        stringResource(R.string.library_tab_local),
        stringResource(R.string.library_tab_cloud)
    )
    val pagerState = rememberPagerState { tabTitles.size }

    LaunchedEffect(lifecycleOwner, pagerState) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            Log.d("LibraryScreen", "Resumed. Current tab: ${pagerState.currentPage}")
            viewModel.loadLocalRecordingsOnResume()

            if (pagerState.currentPage == 1) {
                Log.d("LibraryScreen", "Cloud tab is active on resume, forcing cloud refresh.")
                viewModel.forceRefreshCloudRecordings()
            }
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

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is LibraryViewEvent.LaunchMultiFilePicker -> {
                    Log.d("LibraryScreen", "Received LaunchMultiFilePicker event, launching picker...")
                    try {
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    } catch (e: ActivityNotFoundException) {
                        Log.e("LibraryScreen", "No activity found to handle picking audio files.", e)
                        scope.launch { showSnackbar("Cannot open file picker. No suitable app found.") }
                    }
                }
                is LibraryViewEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is LibraryViewEvent.NavigateToLocalDetails -> {
                    navController.navigate(Screen.RecordingDetails.createLocalRoute(filePath = event.filePath))
                }
                is LibraryViewEvent.NavigateToCloudDetails -> {
                    val metadataDto = AudioMetadataDto(
                        recordingId = event.recordingId,
                        title = event.title,
                        fileName = event.fileName,
                        storageUrl = event.storageUrl,
                        uploadTimestamp = event.timestampSeconds?.let { TimestampDto(it, 0) }
                    )
                    val route = Screen.RecordingDetails.createCloudRoute(metadata = metadataDto)
                    if (route != "recording_details/error") {
                        navController.navigate(route)
                    } else {
                        scope.launch { showSnackbar("Error: Cannot navigate, recording ID missing.") }
                    }
                }
            }
        }
    }

    if (uiState.showMultiDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelMultiDelete,
            title = { Text(stringResource(R.string.dialog_multi_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_multi_delete_message,
                        uiState.selectedRecordingIds.size
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmMultiDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dialog_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMultiDelete) {
                    Text(stringResource(R.string.dialog_action_cancel))
                }
            }
        )
    }

    uiState.importDialogState?.let { dialogState ->
        ImportAudioDialog(
            dialogState = dialogState,
            onDismiss = viewModel::cancelImportDialog,
            onConfirm = { title, description ->
                viewModel.importFiles(listOf(dialogState.fileUri), title, description)
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (uiState.isMultiSelectActive) {
                MultiSelectTopAppBar(
                    selectedCount = uiState.selectedRecordingIds.size,
                    areAllSelected = areAllSelected,
                    onCloseClick = viewModel::exitMultiSelectMode,
                    onSelectAllClick = viewModel::selectAllLocal,
                    onDeselectAllClick = viewModel::deselectAllLocal,
                    onDeleteClick = viewModel::requestMultiDeleteConfirmation,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.nav_library)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_open_navigation_drawer),
                            )
                        }
                    },
                    actions = {
                        if (pagerState.currentPage == 0 && !uiState.isMultiSelectActive) {
                            IconButton(onClick = {
                                Log.d("LibraryScreen", "Upload/Import Icon Clicked - Calling ViewModel")
                                viewModel.onImportAudioClicked()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.UploadFile,
                                    contentDescription = stringResource(R.string.cd_import_local_audio)
                                )
                            }
                        }
                    }
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                                if (index == 1) {
                                    Log.d("LibraryScreen", "Cloud tab clicked, forcing refresh.")
                                    viewModel.forceRefreshCloudRecordings()
                                }
                            }
                        },
                        text = { Text(text = title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) { page ->
                when (page) {
                    0 -> LocalRecordingsTabPage(
                        uiState = uiState,
                        selectedIds = uiState.selectedRecordingIds,
                        isMultiSelectActive = uiState.isMultiSelectActive,
                        onItemClick = { metadata -> viewModel.onRecordingClicked(metadata) },
                        onItemLongClick = viewModel::enterMultiSelectMode,
                        isLoading = uiState.isLoadingLocal && !uiState.isImporting,
                        navController = navController
                    )
                    1 -> CloudRecordingsTabPage(
                        uiState = uiState,
                        onItemClick = { metadataDto -> viewModel.onRecordingClicked(metadataDto) },
                        isLoading = uiState.isLoadingCloud,
                        navController = navController,
                        context = context,
                        scope = scope,
                        showSnackbar = showSnackbar
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopAppBar(
    selectedCount: Int,
    areAllSelected: Boolean,
    onCloseClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onDeselectAllClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topAppBarState)

    TopAppBar(
        modifier = modifier,
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_multi_select),
                )
            }
        },
        actions = {
            if (areAllSelected) {
                IconButton(onClick = onDeselectAllClick) {
                    Icon(
                        imageVector = Icons.Filled.Deselect,
                        contentDescription = stringResource(R.string.cd_deselect_all),
                    )
                }
            } else {
                IconButton(onClick = onSelectAllClick) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = stringResource(R.string.cd_select_all),
                    )
                }
            }

            IconButton(onClick = onDeleteClick, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete_selected),
                    tint =
                        if (selectedCount >
                            0
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun LocalRecordingsTabPage(
    uiState: LibraryUiState,
    selectedIds: Set<String>,
    isMultiSelectActive: Boolean,
    onItemClick: (RecordingMetadata) -> Unit,
    onItemLongClick: (String) -> Unit,
    isLoading: Boolean,
    navController: NavHostController
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isLoading && uiState.localRecordings.isEmpty()) {
            Text(
                text = stringResource(R.string.library_empty_state_local),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            )
        } else if (uiState.localRecordings.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.localRecordings, key = { it.filePath }) { metadata ->
                    val isSelected = selectedIds.contains(metadata.filePath)
                    LocalRecordingListItem(
                        metadata = metadata,
                        isMultiSelectActive = isMultiSelectActive,
                        isSelected = isSelected,
                        onLongClick = { onItemLongClick(metadata.filePath) },
                        onClick = { onItemClick(metadata) },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun CloudRecordingsTabPage(
    uiState: LibraryUiState,
    onItemClick: (AudioMetadataDto) -> Unit,
    isLoading: Boolean,
    navController: NavHostController,
    context: Context,
    scope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isLoading && uiState.cloudRecordings.isEmpty() && uiState.hasAttemptedCloudLoad) {
            Text(
                text = stringResource(R.string.library_empty_state_cloud),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            )
        } else if (uiState.cloudRecordings.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(
                    uiState.cloudRecordings,
                    key = { it.recordingId ?: it.id ?: it.fileName ?: it.hashCode() }
                ) { metadata ->
                    CloudRecordingListItem(
                        metadata = metadata,
                        onItemClick = { clickedMetadata ->
                            val route = Screen.RecordingDetails.createCloudRoute(clickedMetadata)
                            if (route != "recording_details/error") {
                                navController.navigate(route)
                                Log.d("CloudRecordingListItem", "Navigating route: $route")
                            } else {
                                Log.w("CloudRecordingListItem", "Cloud recording ID is missing, cannot navigate.")
                                scope.launch { showSnackbar("Cannot open details: Recording ID missing.") }
                            }
                        },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalRecordingListItem(
    metadata: RecordingMetadata,
    isMultiSelectActive: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val checkboxAreaWidth = 48.dp
    val listItemHeight = 72.dp

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(listItemHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (!isMultiSelectActive) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    },
                )
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(checkboxAreaWidth)
                    .padding(end = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isMultiSelectActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metadata.title ?: metadata.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestampMillis(metadata.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDurationMillis(metadata.durationMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CloudRecordingListItem(
    metadata: AudioMetadataDto,
    onItemClick: (metadata: AudioMetadataDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick(metadata) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(end = 16.dp)) {
            Text(
                text = metadata.title ?: metadata.fileName ?: "Uploaded Recording",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = stringResource(R.string.cd_cloud_recording_indicator),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = formatTimestampDto(metadata.uploadTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                formatFileSize(metadata.fileSize).takeIf { it.isNotEmpty() }?.let { size ->
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            metadata.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ImportAudioDialog(
    dialogState: ImportDialogState,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit,
) {
    var title by rememberSaveable(dialogState.fileUri) { mutableStateOf("") }
    var description by rememberSaveable(dialogState.fileUri) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_import_details_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.dialog_import_selected_file, dialogState.originalFileName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dialog_import_field_title)) },
                    placeholder = { Text(stringResource(R.string.dialog_import_field_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.dialog_import_field_description)) },
                    placeholder = { Text(stringResource(R.string.dialog_import_field_description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title.trim(), description.trim()) }) {
                Text(stringResource(R.string.dialog_action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_action_cancel))
            }
        }
    )
}