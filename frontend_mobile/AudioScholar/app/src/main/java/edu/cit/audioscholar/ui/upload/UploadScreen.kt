package edu.cit.audioscholar.ui.upload

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import edu.cit.audioscholar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToRecording: () -> Unit,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            viewModel.onFileSelected(uri)
        }
    )

    LaunchedEffect(uiState.uploadMessage) {
        uiState.uploadMessage?.let { message ->
            val isError = message.startsWith("Error:", ignoreCase = true) || message.contains("failed", ignoreCase = true)
            snackbarHostState.showSnackbar(
                message = message,
                duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
            viewModel.consumeUploadMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.upload_screen_title)) },
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
        UploadScreenContent(
            state = uiState,
            paddingValues = paddingValues,
            onSelectFileClick = {
                if (!uiState.isUploading) {
                    try {
                        filePickerLauncher.launch("audio/*")
                    } catch (e: Exception) {
                        Log.e("UploadScreen", "Error launching file picker: ${e.message}")
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Could not open file picker.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            },
            onUploadClick = {
                if (!uiState.isUploading) {
                    viewModel.onUploadClicked()
                }
            },
            onTitleChange = viewModel::onTitleChanged,
            onDescriptionChange = viewModel::onDescriptionChanged
        )
    }
}

@Composable
fun UploadScreenContent(
    state: UploadScreenState,
    paddingValues: PaddingValues,
    onSelectFileClick: () -> Unit,
    onUploadClick: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSelectFileClick,
            enabled = !state.isUploading
        ) {
            Text(stringResource(id = R.string.button_select_file))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.selectedFileName ?: stringResource(id = R.string.text_no_file_selected),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(id = R.string.upload_label_title)) },
            placeholder = { Text(stringResource(id = R.string.upload_placeholder_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isUploading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(id = R.string.upload_label_description)) },
            placeholder = { Text(stringResource(id = R.string.upload_placeholder_description)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            maxLines = 4,
            enabled = !state.isUploading
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onUploadClick,
            enabled = state.isUploadEnabled && !state.isUploading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (state.isUploading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (state.progress > 0) "${state.progress}%" else stringResource(id = R.string.text_uploading),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Text(stringResource(id = R.string.button_upload))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}