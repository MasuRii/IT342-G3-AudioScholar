package edu.cit.audioscholar.ui.recording

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
// Import the specific AutoMirrored icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview // Keep Preview import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
// Import rememberNavController for the preview
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R // Import your R class
import edu.cit.audioscholar.ui.theme.AudioScholarTheme // Import your theme
import androidx.compose.material3.ExperimentalMaterial3Api

// Remove @Preview from the main composable function
// @Preview // <-- REMOVED
@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar
@Composable
fun RecordingScreen(
    navController: NavHostController, // To handle back navigation
    // Use hiltViewModel for actual runtime injection
    viewModel: RecordingViewModel = hiltViewModel()
) {
    // Observe the UI state from the ViewModel in a lifecycle-aware manner
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.recording_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // TODO: Add confirmation dialog if uiState.isRecording is true
                        navController.navigateUp() // Use NavController to go back
                    }) {
                        Icon(
                            // Use the AutoMirrored version of the icon
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, // <-- UPDATED
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                }
                // Add actions here if needed later
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .padding(16.dp), // Add overall screen padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround // Arrange elements vertically
        ) {
            Spacer(modifier = Modifier.height(64.dp)) // Add some space at the top

            // Timer Display
            Text(
                text = uiState.elapsedTimeFormatted,
                style = MaterialTheme.typography.displayMedium
            )

            // Recording Status Indicator
            Text(
                text = if (uiState.isRecording) {
                    stringResource(R.string.status_recording)
                } else {
                    stringResource(R.string.status_tap_to_record)
                },
                style = MaterialTheme.typography.bodyLarge
            )

            // Record/Stop Button
            IconButton(
                onClick = { viewModel.toggleRecording() },
                modifier = Modifier.size(80.dp) // Set button size
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (uiState.isRecording) {
                        stringResource(R.string.cd_stop_button)
                    } else {
                        stringResource(R.string.cd_record_button)
                    },
                    modifier = Modifier.fillMaxSize(), // Icon fills the IconButton
                    tint = MaterialTheme.colorScheme.primary // Use theme color
                )
            }

            Spacer(modifier = Modifier.height(64.dp)) // Add some space at the bottom
        }
    }
}

// --- Add a dedicated Preview function ---
@OptIn(ExperimentalMaterial3Api::class) // <-- ADD THIS LINE
@Preview(showBackground = true, name = "Recording Screen Preview")
@Composable
fun RecordingScreenPreview() {
    AudioScholarTheme {
        // Create a fake state for the preview
        val previewState = RecordingUiState(
            isRecording = false,
            elapsedTimeFormatted = "00:10:35" // Example time
        )
        // Create a dummy NavController for the preview
        val dummyNavController = rememberNavController()

        Scaffold( // This requires the OptIn
            topBar = {
                TopAppBar( // This requires the OptIn
                    title = { Text(stringResource(id = R.string.recording_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = { /* No action in preview */ }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Use updated icon
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
                    text = previewState.elapsedTimeFormatted, // Use preview state
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = if (previewState.isRecording) { // Use preview state
                        stringResource(R.string.status_recording)
                    } else {
                        stringResource(R.string.status_tap_to_record)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(
                    onClick = { /* No action in preview */ },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = if (previewState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic, // Use preview state
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