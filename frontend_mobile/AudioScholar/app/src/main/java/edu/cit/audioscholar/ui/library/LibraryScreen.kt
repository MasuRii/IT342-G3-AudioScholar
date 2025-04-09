package edu.cit.audioscholar.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

private fun formatTimestampMillis(timestampMillis: Long): String {
    if (timestampMillis <= 0) return "Unknown date"
    val date = Date(timestampMillis)
    val format = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    return format.format(date)
}

private fun formatTimestampDto(timestampDto: TimestampDto?): String {
    if (timestampDto?.seconds == null || timestampDto.seconds <= 0) return "Unknown date"
    val timestampMillis = TimeUnit.SECONDS.toMillis(timestampDto.seconds) + TimeUnit.NANOSECONDS.toMillis(timestampDto.nanos ?: 0)
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


private fun playLocalRecordingExternally(
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

        Log.d("PlayRecording", "Attempting to launch player for local URI: $contentUri")
        context.startActivity(intent)

    } catch (e: ActivityNotFoundException) {
        Log.e("PlayRecording", "No activity found to handle audio intent.", e)
        scope.launch { showSnackbar("No app found to play this audio file.") }
    } catch (e: Exception) {
        Log.e("PlayRecording", "Error launching external player for local file", e)
        scope.launch { showSnackbar("Error playing recording: ${e.localizedMessage}") }
    }
}

private fun playCloudRecording(
    context: Context,
    metadata: AudioMetadataDto,
    scope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit
) {
    val url = metadata.storageUrl
    if (url.isNullOrBlank()) {
        scope.launch { showSnackbar("Error: Recording URL is missing.") }
        return
    }

    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
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
    viewModel: LocalRecordingsViewModel = hiltViewModel(),
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val showSnackbar: suspend (String) -> Unit = { message ->
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
    }

    val tabTitles = listOf(stringResource(R.string.library_tab_local), stringResource(R.string.library_tab_cloud))
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

    uiState.recordingToDelete?.let { recording ->
        DeleteConfirmationDialog(
            metadata = recording,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    LaunchedEffect(pagerState.currentPage, uiState.hasAttemptedCloudLoad) {
        if (pagerState.currentPage == 1 && !uiState.hasAttemptedCloudLoad) {
            Log.d("LibraryScreen", "Cloud tab selected (page 1) and cloud load not attempted yet. Triggering initial load.")
            viewModel.triggerCloudLoadIfNeeded()
        } else {
            Log.d("LibraryScreen", "Pager changed to ${pagerState.currentPage} or cloud load already attempted (${uiState.hasAttemptedCloudLoad}). Skipping initial trigger.")
        }
    }


    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_library)) },
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
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            val showLoadingIndicator = (pagerState.currentPage == 0 && uiState.isLoadingLocal) ||
                    (pagerState.currentPage == 1 && uiState.isLoadingCloud)

            if (showLoadingIndicator) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }


            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                when (page) {
                    0 -> LocalRecordingsTabPage(
                        uiState = uiState,
                        context = context,
                        scope = scope,
                        showSnackbar = showSnackbar,
                        onDeleteClick = viewModel::requestDeleteConfirmation,
                        navController = navController
                    )
                    1 -> CloudRecordingsTabPage(
                        uiState = uiState,
                        context = context,
                        scope = scope,
                        showSnackbar = showSnackbar
                    )
                }
            }
        }
    }
}

@Composable
fun LocalRecordingsTabPage(
    uiState: LibraryUiState,
    context: Context,
    scope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit,
    onDeleteClick: (RecordingMetadata) -> Unit,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!uiState.isLoadingLocal && uiState.localRecordings.isEmpty()) {
            Text(
                text = stringResource(R.string.library_empty_state_local),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        } else if (uiState.localRecordings.isNotEmpty()){
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.localRecordings, key = { it.filePath }) { metadata ->
                    LocalRecordingListItem(
                        metadata = metadata,
                        onDeleteClick = onDeleteClick,
                        navController = navController
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun CloudRecordingsTabPage(
    uiState: LibraryUiState,
    context: Context,
    scope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!uiState.isLoadingCloud && uiState.cloudRecordings.isEmpty() && uiState.hasAttemptedCloudLoad) {
            Text(
                text = stringResource(R.string.library_empty_state_cloud),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        } else if (uiState.cloudRecordings.isNotEmpty()){
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.cloudRecordings, key = { it.id ?: it.fileName ?: it.hashCode() }) { metadata ->
                    CloudRecordingListItem(
                        metadata = metadata,
                        onItemClick = {
                            playCloudRecording(context, it, scope, showSnackbar)
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}


@Composable
fun LocalRecordingListItem(
    metadata: RecordingMetadata,
    onDeleteClick: (RecordingMetadata) -> Unit,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val encodedFilePath = Uri.encode(metadata.filePath)
                navController.navigate(Screen.RecordingDetails.createRoute(encodedFilePath))
                Log.d("LocalRecordingListItem", "Navigating to details for: $encodedFilePath")
            }
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
                    text = formatTimestampMillis(metadata.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatDurationMillis(metadata.durationMillis),
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
fun CloudRecordingListItem(
    metadata: AudioMetadataDto,
    onItemClick: (AudioMetadataDto) -> Unit,
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
                text = metadata.title ?: metadata.fileName ?: "Uploaded Recording",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = stringResource(R.string.cd_cloud_recording_indicator),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = formatTimestampDto(metadata.uploadTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                formatFileSize(metadata.fileSize).takeIf { it.isNotEmpty() }?.let { size ->
                    Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    overflow = TextOverflow.Ellipsis
                )
            }
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