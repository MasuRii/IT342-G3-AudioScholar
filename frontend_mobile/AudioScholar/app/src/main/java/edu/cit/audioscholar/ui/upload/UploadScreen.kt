package edu.cit.audioscholar.ui.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import edu.cit.audioscholar.R

@Composable
fun UploadScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToRecording: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            viewModel.onFileSelected(uri)
        }
    )

    UploadScreenContent(
        state = uiState,
        onSelectFileClick = {
            try {
                viewModel.onSelectFileClicked()
                filePickerLauncher.launch("audio/*")
            } catch (e: Exception) {
                println("Error launching file picker: ${e.message}")
            }
        },
        onUploadClick = viewModel::onUploadClicked,
        onNavigateToRecording = onNavigateToRecording
    )
}

@Composable
fun UploadScreenContent(
    state: UploadScreenState,
    onSelectFileClick: () -> Unit,
    onUploadClick: () -> Unit,
    onNavigateToRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.upload_screen_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSelectFileClick,
            enabled = !state.isUploading
        ) {
            Text(stringResource(R.string.button_select_file))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.selectedFileName ?: stringResource(R.string.text_no_file_selected),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onUploadClick,
            enabled = state.selectedFileUri != null && !state.isUploading,
            modifier = Modifier.height(48.dp)
        ) {
            if (state.isUploading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${state.progress}%",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                Text(stringResource(R.string.button_upload))
            }
        }


        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = state.uploadMessage ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.uploadMessage?.startsWith("Error:") == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 18.dp)
        )


        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNavigateToRecording,
            enabled = !state.isUploading
        ) {
            Text(stringResource(R.string.button_back_to_recording))
        }
    }
}