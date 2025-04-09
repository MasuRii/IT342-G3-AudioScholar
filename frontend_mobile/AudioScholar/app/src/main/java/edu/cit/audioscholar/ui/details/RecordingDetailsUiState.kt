package edu.cit.audioscholar.ui.details

import androidx.compose.runtime.Stable

data class MockYouTubeVideo(
    val id: String,
    val thumbnailUrl: Int,
    val title: String
)

enum class SummaryStatus { IDLE, PROCESSING, READY, FAILED }


@Stable
data class RecordingDetailsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val infoMessage: String? = null,

    val title: String = "",
    val dateCreated: String = "",
    val durationMillis: Long = 0L,
    val durationFormatted: String = "00:00",
    val filePath: String = "",

    val isPlaying: Boolean = false,
    val currentPositionMillis: Long = 0L,
    val currentPositionFormatted: String = "00:00",
    val playbackProgress: Float = 0f,

    val summaryStatus: SummaryStatus = SummaryStatus.IDLE,
    val summaryText: String = "",
    val aiNotesText: String = "",

    val attachedPowerPoint: String? = null,

    val youtubeRecommendations: List<MockYouTubeVideo> = emptyList(),

    val showDeleteConfirmation: Boolean = false,

    val textToCopy: String? = null
)