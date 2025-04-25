package edu.cit.audioscholar.ui.details

import androidx.compose.runtime.Stable
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto

enum class SummaryStatus { IDLE, PROCESSING, READY, FAILED }
enum class RecommendationsStatus { IDLE, LOADING, READY, FAILED }


@Stable
data class RecordingDetailsUiState(
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,

    val title: String = "",
    val dateCreated: String = "",
    val durationMillis: Long = 0L,
    val durationFormatted: String = "00:00",
    val filePath: String = "",
    val remoteRecordingId: String? = null,
    val storageUrl: String? = null,
    val isCloudSource: Boolean = false,

    val isEditingTitle: Boolean = false,
    val editableTitle: String = "",

    val isPlaying: Boolean = false,
    val currentPositionMillis: Long = 0L,
    val currentPositionFormatted: String = "00:00",
    val playbackProgress: Float = 0f,

    val summaryStatus: SummaryStatus = SummaryStatus.IDLE,
    val summaryText: String = "",
    val glossaryItems: List<GlossaryItemDto> = emptyList(),

    val recommendationsStatus: RecommendationsStatus = RecommendationsStatus.IDLE,
    val youtubeRecommendations: List<RecommendationDto> = emptyList(),

    val attachedPowerPoint: String? = null,

    val showDeleteConfirmation: Boolean = false,

    val textToCopy: String? = null,

    val uploadProgressPercent: Int? = null
) {
    val isProcessing: Boolean
        get() = uploadProgressPercent != null ||
                summaryStatus == SummaryStatus.PROCESSING ||
                recommendationsStatus == RecommendationsStatus.LOADING

    val isPlaybackReady: Boolean
        get() = !isProcessing && !isDeleting && (filePath.isNotEmpty() || !storageUrl.isNullOrBlank())

    val showLocalActions: Boolean
        get() = !isCloudSource || filePath.isNotEmpty()

    val showCloudInfo: Boolean
        get() = isCloudSource || remoteRecordingId != null
}