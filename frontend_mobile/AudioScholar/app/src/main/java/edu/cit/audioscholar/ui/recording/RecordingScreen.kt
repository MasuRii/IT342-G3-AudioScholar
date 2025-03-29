package edu.cit.audioscholar.ui.recording

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    navController: NavHostController,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.recording_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
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
            verticalArrangement = Arrangement.SpaceAround
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = uiState.elapsedTimeFormatted,
                style = MaterialTheme.typography.displayMedium
            )

            Text(
                text = if (uiState.isRecording) {
                    stringResource(R.string.status_recording)
                } else {
                    stringResource(R.string.status_tap_to_record)
                },
                style = MaterialTheme.typography.bodyLarge
            )

            IconButton(
                onClick = { viewModel.toggleRecording() },
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (uiState.isRecording) {
                        stringResource(R.string.cd_stop_button)
                    } else {
                        stringResource(R.string.cd_record_button)
                    },
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Recording Screen Preview")
@Composable
fun RecordingScreenPreview() {
    AudioScholarTheme {
        val previewState = RecordingUiState(
            isRecording = false,
            elapsedTimeFormatted = "00:10:35"
        )
        val dummyNavController = rememberNavController()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.recording_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = { }) {
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
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                Text(
                    text = previewState.elapsedTimeFormatted,
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = if (previewState.isRecording) {
                        stringResource(R.string.status_recording)
                    } else {
                        stringResource(R.string.status_tap_to_record)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(
                    onClick = { },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = if (previewState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (previewState.isRecording) {
                            stringResource(R.string.cd_stop_button)
                        } else {
                            stringResource(R.string.cd_record_button)
                        },
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}