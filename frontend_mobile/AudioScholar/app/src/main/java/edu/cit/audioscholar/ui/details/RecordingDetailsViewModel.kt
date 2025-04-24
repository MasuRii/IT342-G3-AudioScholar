package edu.cit.audioscholar.ui.details

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.RecommendationDto
import edu.cit.audioscholar.data.remote.dto.SummaryResponseDto
import edu.cit.audioscholar.domain.repository.*
import edu.cit.audioscholar.service.PlaybackManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject


private fun formatDurationMillis(durationMillis: Long): String {
    if (durationMillis <= 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun formatTimestampMillis(timestampMillis: Long): String {
    if (timestampMillis <= 0) return "Unknown date"
    val date = Date(timestampMillis)
    val format = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    return format.format(date)
}

private const val POLLING_INTERVAL_MS = 3000L
private const val POLLING_TIMEOUT_MS = 25000L
private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000

@HiltViewModel
class RecordingDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val localAudioRepository: LocalAudioRepository,
    private val playbackManager: PlaybackManager,
    private val remoteAudioRepository: RemoteAudioRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingDetailsUiState())
    val uiState: StateFlow<RecordingDetailsUiState> = _uiState.asStateFlow()

    private val _triggerFilePicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerFilePicker: SharedFlow<Unit> = _triggerFilePicker.asSharedFlow()

    private val _openUrlEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    private val recordingId: String = savedStateHandle.get<String>("recordingId") ?: ""
    private val decodedFilePath: String = Uri.decode(recordingId)

    private var pollingJob: Job? = null
    private var currentMetadata: RecordingMetadata? = null


    init {
        Log.d("DetailsViewModel", "Initializing for recordingId: $recordingId, decodedPath: $decodedFilePath")
        if (decodedFilePath.isNotEmpty()) {
            loadRecordingDetails()
            observePlaybackState()
        } else {
            _uiState.update { it.copy(isLoading = false, error = application.getString(R.string.error_unexpected_details_view)) }
        }
    }

    private fun isCacheValid(cacheTimestampMillis: Long?): Boolean {
        if (cacheTimestampMillis == null) return false
        return (System.currentTimeMillis() - cacheTimestampMillis) < CACHE_VALIDITY_MS
    }

    private fun loadRecordingDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            localAudioRepository.getRecordingMetadata(decodedFilePath)
                .catch { e ->
                    Log.e("DetailsViewModel", "Error loading details for $decodedFilePath", e)
                    val errorMsg = when (e) {
                        is IOException -> application.getString(R.string.error_local_storage_issue)
                        else -> application.getString(R.string.error_unexpected_details_view)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = errorMsg
                        )
                    }
                }
                .collect { result ->
                    result.onSuccess { metadata ->
                        currentMetadata = metadata
                        val currentTitle = metadata.title ?: metadata.fileName
                        val remoteId = metadata.remoteRecordingId
                        val cacheTimestamp = metadata.cacheTimestampMillis
                        val isSummaryCacheValid = isCacheValid(cacheTimestamp) && metadata.cachedSummaryText != null
                        val areRecommendationsCacheValid = isCacheValid(cacheTimestamp) && metadata.cachedRecommendations != null

                        Log.d("DetailsViewModel", "Metadata loaded. RemoteId: $remoteId, CacheTime: $cacheTimestamp, SummaryCacheValid: $isSummaryCacheValid, RecsCacheValid: $areRecommendationsCacheValid")

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                title = currentTitle,
                                editableTitle = currentTitle,
                                dateCreated = formatTimestampMillis(metadata.timestampMillis),
                                durationMillis = metadata.durationMillis,
                                durationFormatted = formatDurationMillis(metadata.durationMillis),
                                filePath = metadata.filePath,
                                remoteRecordingId = remoteId,
                                error = null,
                                summaryStatus = when {
                                    isSummaryCacheValid -> SummaryStatus.READY
                                    remoteId != null -> SummaryStatus.PROCESSING
                                    else -> SummaryStatus.IDLE
                                },
                                recommendationsStatus = when {
                                    areRecommendationsCacheValid -> RecommendationsStatus.READY
                                    remoteId != null -> RecommendationsStatus.LOADING
                                    else -> RecommendationsStatus.IDLE
                                },
                                summaryText = if (isSummaryCacheValid) metadata.cachedSummaryText ?: "" else "",
                                glossaryItems = if (isSummaryCacheValid) metadata.cachedGlossaryItems ?: emptyList() else emptyList(),
                                youtubeRecommendations = if (areRecommendationsCacheValid) metadata.cachedRecommendations ?: emptyList() else emptyList()
                            )
                        }
                        playbackManager.preparePlayer(metadata.filePath)

                        if (remoteId != null && (!isSummaryCacheValid || !areRecommendationsCacheValid)) {
                            Log.d("DetailsViewModel", "Remote ID found ($remoteId) and cache is invalid/missing, starting polling.")
                            startPollingForSummaryAndRecommendations(remoteId, !isSummaryCacheValid, !areRecommendationsCacheValid)
                        } else if (remoteId == null) {
                            Log.d("DetailsViewModel", "No remote ID found, waiting for 'Process' button.")
                            if (!isSummaryCacheValid) _uiState.update { it.copy(summaryStatus = SummaryStatus.IDLE) }
                            if (!areRecommendationsCacheValid) _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.IDLE) }
                        } else {
                            Log.d("DetailsViewModel", "Remote ID found ($remoteId) but cache is valid. No polling needed initially.")
                        }

                    }.onFailure { e ->
                        Log.e("DetailsViewModel", "Failed result loading details for $decodedFilePath", e)
                        val errorMsg = when (e) {
                            is IOException -> application.getString(R.string.error_local_storage_issue)
                            else -> application.getString(R.string.error_unexpected_details_view)
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = errorMsg
                            )
                        }
                    }
                }
        }
    }

    private fun observePlaybackState() {
        playbackManager.playbackState
            .onEach { playbackState ->
                _uiState.update { currentState ->
                    val currentDuration = if (playbackState.totalDurationMs > 0) playbackState.totalDurationMs else currentState.durationMillis
                    val progress = if (currentDuration > 0) (playbackState.currentPositionMs.toFloat() / currentDuration).coerceIn(0f, 1f) else 0f

                    currentState.copy(
                        isPlaying = playbackState.isPlaying,
                        currentPositionMillis = playbackState.currentPositionMs,
                        durationMillis = currentDuration,
                        durationFormatted = formatDurationMillis(currentDuration),
                        currentPositionFormatted = formatDurationMillis(playbackState.currentPositionMs),
                        playbackProgress = progress,
                        error = playbackState.error ?: currentState.error
                    )
                }
                if(playbackState.error != null) {
                    playbackManager.consumeError()
                }
            }.launchIn(viewModelScope)
    }

    private fun startPollingForSummaryAndRecommendations(remoteId: String, pollForSummary: Boolean, pollForRecommendations: Boolean) {
        if (!pollForSummary && !pollForRecommendations) {
            Log.d("DetailsViewModel", "Polling requested but both summary and recommendations cache are valid.")
            return
        }

        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            Log.d("DetailsViewModel", "Polling started for remoteId: $remoteId. PollSummary: $pollForSummary, PollRecs: $pollForRecommendations")

            _uiState.update {
                it.copy(
                    summaryStatus = if (pollForSummary) SummaryStatus.PROCESSING else it.summaryStatus,
                    recommendationsStatus = if (pollForRecommendations) RecommendationsStatus.LOADING else it.recommendationsStatus,
                    error = null
                )
            }

            var summaryFetchSuccess = !pollForSummary

            if (pollForSummary) {
                summaryFetchSuccess = pollForData(
                    timeoutMs = POLLING_TIMEOUT_MS,
                    intervalMs = POLLING_INTERVAL_MS,
                    fetchAction = { remoteAudioRepository.getSummary(remoteId) },
                    onSuccess = { summaryDto ->
                        Log.i("DetailsViewModel", "Summary polling successful.")
                        updateCacheAndUi(summary = summaryDto)
                    },
                    onFailure = { errorMsg ->
                        Log.e("DetailsViewModel", "Summary polling failed: $errorMsg")
                        _uiState.update { it.copy(summaryStatus = SummaryStatus.FAILED, error = errorMsg) }
                    },
                    onTimeout = {
                        Log.w("DetailsViewModel", "Summary polling timed out.")
                        _uiState.update { it.copy(summaryStatus = SummaryStatus.FAILED, error = application.getString(R.string.error_processing_timeout)) }
                    }
                )
            }

            if (summaryFetchSuccess && pollForRecommendations) {
                _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.LOADING, error = null) }
                pollForData(
                    timeoutMs = POLLING_TIMEOUT_MS,
                    intervalMs = POLLING_INTERVAL_MS,
                    fetchAction = { remoteAudioRepository.getRecommendations(remoteId) },
                    onSuccess = { recommendations ->
                        Log.i("DetailsViewModel", "Recommendations polling successful.")
                        updateCacheAndUi(recommendations = recommendations)
                    },
                    onFailure = { errorMsg ->
                        Log.e("DetailsViewModel", "Recommendations polling failed: $errorMsg")
                        _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = errorMsg) }
                    },
                    onTimeout = {
                        Log.w("DetailsViewModel", "Recommendations polling timed out.")
                        _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = application.getString(R.string.error_processing_timeout)) }
                    }
                )
            } else if (!summaryFetchSuccess && pollForRecommendations) {
                Log.w("DetailsViewModel", "Skipping recommendations polling because summary failed.")
                if (_uiState.value.error == null) {
                    _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = application.getString(R.string.error_recommendations_fetch_failed)) }
                } else {
                    _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED) }
                }
            }

            if (_uiState.value.summaryStatus != SummaryStatus.PROCESSING && _uiState.value.recommendationsStatus != RecommendationsStatus.LOADING) {
                Log.d("DetailsViewModel", "Polling finished.")
            }
        }
    }

    private fun updateCacheAndUi(
        summary: SummaryResponseDto? = null,
        recommendations: List<RecommendationDto>? = null
    ) {
        viewModelScope.launch {
            val meta = currentMetadata ?: return@launch

            val updatedMetadata = meta.copy(
                cachedSummaryText = summary?.formattedSummaryText ?: meta.cachedSummaryText,
                cachedGlossaryItems = summary?.glossary ?: meta.cachedGlossaryItems,
                cachedRecommendations = recommendations ?: meta.cachedRecommendations,
                cacheTimestampMillis = System.currentTimeMillis()
            )

            val saved = localAudioRepository.saveMetadata(updatedMetadata)
            if (saved) {
                currentMetadata = updatedMetadata
                Log.i("DetailsViewModel", "Successfully saved updated metadata cache.")
                _uiState.update {
                    it.copy(
                        summaryStatus = if (summary != null || it.summaryStatus == SummaryStatus.READY) SummaryStatus.READY else it.summaryStatus,
                        summaryText = updatedMetadata.cachedSummaryText ?: it.summaryText,
                        glossaryItems = updatedMetadata.cachedGlossaryItems ?: it.glossaryItems,
                        recommendationsStatus = if (recommendations != null || it.recommendationsStatus == RecommendationsStatus.READY) RecommendationsStatus.READY else it.recommendationsStatus,
                        youtubeRecommendations = updatedMetadata.cachedRecommendations ?: it.youtubeRecommendations
                    )
                }
            } else {
                Log.e("DetailsViewModel", "Failed to save updated metadata cache.")
                _uiState.update { it.copy(error = application.getString(R.string.error_metadata_update_failed)) }
            }
        }
    }

    private suspend fun <T> pollForData(
        timeoutMs: Long,
        intervalMs: Long,
        fetchAction: () -> Flow<Result<T>>,
        onSuccess: (T) -> Unit,
        onFailure: (String) -> Unit,
        onTimeout: () -> Unit
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            var lastError: Throwable? = null

            while (isActive) {
                var attemptSuccessful = false
                var shouldRetry = false
                var mappedErrorMessage: String? = null

                try {
                    fetchAction()
                        .catch { e ->
                            Log.e("DetailsViewModelPolling", "Exception during fetch flow collection", e)
                            lastError = e
                            mappedErrorMessage = mapErrorToUserFriendlyMessage(e)
                            shouldRetry = e is IOException
                        }
                        .collect { result ->
                            result.onSuccess { data ->
                                Log.d("DetailsViewModelPolling", "Polling attempt successful.")
                                attemptSuccessful = true
                                onSuccess(data)
                            }.onFailure { e ->
                                lastError = e
                                if (e is HttpException && e.code() == 404) {
                                    Log.d("DetailsViewModelPolling", "Received 404, resource not ready yet. Will retry...")
                                    shouldRetry = true
                                } else {
                                    Log.w("DetailsViewModelPolling", "Polling attempt failed with non-retryable error: ${e.message}")
                                    mappedErrorMessage = mapErrorToUserFriendlyMessage(e)
                                    shouldRetry = false
                                }
                            }
                        }
                } catch (e: CancellationException) {
                    Log.d("DetailsViewModelPolling", "Polling cancelled.")
                    throw e
                } catch (e: Exception) {
                    Log.e("DetailsViewModelPolling", "Unexpected exception during fetch attempt: ${e.message}", e)
                    lastError = e
                    mappedErrorMessage = mapErrorToUserFriendlyMessage(e)
                    shouldRetry = false
                }

                if (attemptSuccessful) {
                    return@withTimeoutOrNull true
                }

                if (shouldRetry) {
                    Log.d("DetailsViewModelPolling", "Retrying polling after ${intervalMs}ms delay...")
                    delay(intervalMs)
                } else {
                    onFailure(mappedErrorMessage ?: application.getString(R.string.error_unexpected_details_view))
                    return@withTimeoutOrNull false
                }
            }
            false
        } ?: run {
            Log.w("DetailsViewModelPolling", "Polling timed out after ${timeoutMs}ms.")
            onTimeout()
            false
        }
    }

    fun onProcessRecordingClicked() {
        Log.d("DetailsViewModel", "Process Recording button clicked for: ${uiState.value.filePath}")
        val currentState = uiState.value
        val meta = currentMetadata

        if (meta == null || meta.remoteRecordingId != null || meta.filePath.isEmpty() || currentState.isProcessing) {
            Log.w("DetailsViewModel", "Process Recording clicked but state is invalid. RemoteId: ${meta?.remoteRecordingId}, Path: ${meta?.filePath}, Processing: ${currentState.isProcessing}")
            _uiState.update { it.copy(error = "Recording cannot be processed or is already processed/processing.") }
            return
        }

        _uiState.update { it.copy(
            summaryStatus = SummaryStatus.PROCESSING,
            recommendationsStatus = RecommendationsStatus.LOADING,
            uploadProgressPercent = 0,
            error = null
        ) }

        val fileToUpload = File(meta.filePath)
        if (!fileToUpload.exists() || !fileToUpload.canRead()) {
            Log.e("DetailsViewModel", "File does not exist or cannot be read: ${meta.filePath}")
            _uiState.update { it.copy(
                summaryStatus = SummaryStatus.FAILED,
                recommendationsStatus = RecommendationsStatus.FAILED,
                error = application.getString(R.string.error_local_storage_issue),
                uploadProgressPercent = null
            ) }
            return
        }

        val fileUri = fileToUpload.toUri()
        val titleToUpload = (if (currentState.isEditingTitle) currentState.editableTitle else meta.title)
            ?.takeIf { it.isNotBlank() && it != fileToUpload.name }

        viewModelScope.launch {
            Log.d("DetailsViewModel", "Starting upload process via repository for URI: $fileUri, Title: $titleToUpload")
            remoteAudioRepository.uploadAudioFile(fileUri, titleToUpload, null)
                .catch { e ->
                    Log.e("DetailsViewModel", "Exception during upload flow collection", e)
                    _uiState.update { it.copy(
                        uploadProgressPercent = null,
                        summaryStatus = SummaryStatus.FAILED,
                        recommendationsStatus = RecommendationsStatus.FAILED,
                        error = mapErrorToUserFriendlyMessage(e))
                    }
                }
                .collect { result ->
                    when (result) {
                        is UploadResult.Loading -> {}
                        is UploadResult.Progress -> {
                            _uiState.update { it.copy(uploadProgressPercent = result.percentage) }
                        }
                        is UploadResult.Success -> {
                            val remoteMetadataDto = result.metadata
                            _uiState.update { it.copy(
                                uploadProgressPercent = null,
                                summaryStatus = SummaryStatus.PROCESSING,
                                recommendationsStatus = RecommendationsStatus.LOADING
                            ) }

                            if (remoteMetadataDto?.recordingId != null) {
                                val newRemoteId = remoteMetadataDto.recordingId
                                Log.i("DetailsViewModel", "Upload successful. Received remote recordingId: $newRemoteId")

                                val updatedMetaWithId = meta.copy(remoteRecordingId = newRemoteId)
                                val idSaveSuccess = localAudioRepository.saveMetadata(updatedMetaWithId)

                                if (idSaveSuccess) {
                                    currentMetadata = updatedMetaWithId
                                    Log.i("DetailsViewModel", "Successfully saved remoteRecordingId to local metadata.")
                                    _uiState.update { it.copy(remoteRecordingId = newRemoteId) }
                                    startPollingForSummaryAndRecommendations(newRemoteId, pollForSummary = true, pollForRecommendations = true)
                                } else {
                                    Log.e("DetailsViewModel", "Failed to save remoteRecordingId to local metadata.")
                                    _uiState.update { it.copy(
                                        summaryStatus = SummaryStatus.FAILED,
                                        recommendationsStatus = RecommendationsStatus.FAILED,
                                        error = application.getString(R.string.error_metadata_update_failed)
                                    ) }
                                }
                            } else {
                                Log.e("DetailsViewModel", "Upload successful but recordingId was missing in the response.")
                                _uiState.update { it.copy(
                                    summaryStatus = SummaryStatus.FAILED,
                                    recommendationsStatus = RecommendationsStatus.FAILED,
                                    error = application.getString(R.string.error_server_generic)
                                ) }
                            }
                        }
                        is UploadResult.Error -> {
                            Log.e("DetailsViewModel", "Upload failed: ${result.message}")
                            val userFriendlyError = result.message?.let {
                                application.getString(R.string.error_upload_failed_detailed, it)
                            } ?: application.getString(R.string.error_upload_failed_generic)

                            _uiState.update { it.copy(
                                uploadProgressPercent = null,
                                summaryStatus = SummaryStatus.FAILED,
                                recommendationsStatus = RecommendationsStatus.FAILED,
                                error = userFriendlyError )
                            }
                        }
                    }
                }
        }
    }

    private fun mapErrorToUserFriendlyMessage(e: Throwable): String {
        return when (e) {
            is IOException -> application.getString(R.string.error_network_connection_generic)
            is HttpException -> {
                when (e.code()) {
                    in 500..599 -> application.getString(R.string.error_server_generic)
                    else -> application.getString(R.string.error_unexpected_details_view) + " (Code: ${e.code()})"
                }
            }
            else -> application.getString(R.string.error_unexpected_details_view)
        }
    }


    fun onPlayPauseToggle() {
        val currentState = _uiState.value
        val newIsPlaying = !currentState.isPlaying

        if (currentState.filePath.isEmpty()) {
            Log.w("DetailsViewModel", "Play/Pause toggle attempted but file path is empty.")
            _uiState.update { it.copy(error = "Cannot play: File path not loaded.") }
            return
        }

        if (newIsPlaying) {
            if (playbackManager.playbackState.value.totalDurationMs <= 0) {
                Log.d("DetailsViewModel", "Player not ready or needs preparation. Calling prepareAndPlay.")
                playbackManager.prepareAndPlay(currentState.filePath)
            } else {
                Log.d("DetailsViewModel", "Player seems ready. Calling play.")
                playbackManager.play()
            }
        } else {
            Log.d("DetailsViewModel", "Calling pause.")
            playbackManager.pause()
        }
    }

    fun onSeek(progress: Float) {
        val currentDuration = playbackManager.playbackState.value.totalDurationMs.takeIf { it > 0 } ?: _uiState.value.durationMillis
        if (currentDuration <= 0) {
            Log.w("DetailsViewModel", "Seek attempted but duration is unknown.")
            return
        }
        val newPositionMillis = (progress * currentDuration).toLong()
        playbackManager.seekTo(newPositionMillis)
        _uiState.update {
            it.copy(
                currentPositionMillis = newPositionMillis,
                currentPositionFormatted = formatDurationMillis(newPositionMillis),
                playbackProgress = progress
            )
        }
    }

    fun onCopySummaryAndNotes() {
        val state = _uiState.value
        if (state.summaryStatus == SummaryStatus.READY && (state.summaryText.isNotEmpty() || state.glossaryItems.isNotEmpty())) {
            val summaryPart = "Summary:\n${state.summaryText.ifBlank { "Not available" }}\n\n"
            val notesPart = "AI Notes (Glossary):\n" +
                    state.glossaryItems.joinToString("\n") { "- ${it.term ?: "Term N/A"}: ${it.definition ?: "Definition N/A"}" }.ifEmpty { "Not available" }

            val combinedText = summaryPart + notesPart
            Log.d("DetailsViewModel", "Copy Summary & Notes clicked. Text prepared.")
            _uiState.update { it.copy(textToCopy = combinedText, infoMessage = "Summary & Notes copied!") }
        } else {
            Log.w("DetailsViewModel", "Copy attempt failed: Summary not ready or empty.")
            _uiState.update { it.copy(error = "Summary and notes are not available to copy.") }
        }
    }

    fun consumeTextToCopy() {
        _uiState.update { it.copy(textToCopy = null) }
    }

    fun requestAttachPowerPoint() {
        Log.d("DetailsViewModel", "Attach PowerPoint requested. Triggering file picker.")
        viewModelScope.launch {
            _triggerFilePicker.tryEmit(Unit)
        }
    }

    fun setAttachedPowerPointFile(fileName: String?) {
        if (fileName != null) {
            Log.d("DetailsViewModel", "Setting attached PowerPoint: $fileName")
            _uiState.update { it.copy(attachedPowerPoint = fileName, infoMessage = "PowerPoint attached: $fileName") }
        } else {
            _uiState.update { it.copy(infoMessage = "PowerPoint selection cancelled.") }
        }
    }

    fun detachPowerPoint() {
        Log.d("DetailsViewModel", "Detach PowerPoint clicked.")
        _uiState.update { it.copy(attachedPowerPoint = null, infoMessage = "PowerPoint detached.") }
    }

    fun onWatchYouTubeVideo(video: RecommendationDto) {
        val videoId = video.videoId
        if (videoId != null) {
            Log.d("DetailsViewModel", "Watch YouTube clicked: ${video.title} (ID: $videoId)")
            val url = "https://www.youtube.com/watch?v=$videoId"
            viewModelScope.launch {
                _openUrlEvent.emit(url)
            }
        } else {
            Log.w("DetailsViewModel", "Watch YouTube clicked, but videoId is null for: ${video.title}")
            _uiState.update { it.copy(error = "Cannot open video: Missing video ID.") }
        }
    }

    fun requestDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        pollingJob?.cancel()
        playbackManager.pause()
        _uiState.update { it.copy(showDeleteConfirmation = false, isDeleting = true) }
        viewModelScope.launch {
            val metaToDelete = currentMetadata
            if (metaToDelete == null || metaToDelete.filePath.isEmpty()) {
                Log.e("DetailsViewModel", "Cannot delete: Metadata or file path is missing.")
                _uiState.update { it.copy(isDeleting = false, error = "Cannot delete recording: file path unknown.") }
                return@launch
            }

            val localDeleteSuccess = localAudioRepository.deleteLocalRecording(metaToDelete)

            if (localDeleteSuccess) {
                Log.d("DetailsViewModel", "Deletion successful for: ${metaToDelete.filePath}")
                playbackManager.releasePlayer()
                currentMetadata = null
                _uiState.update { it.copy(filePath = "", isDeleting = false, infoMessage = "Recording deleted.") }
            } else {
                Log.e("DetailsViewModel", "Error deleting file: ${metaToDelete.filePath}")
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        error = "Failed to delete recording."
                    )
                }
            }
        }
    }


    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
    fun consumeInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun onTitleEditRequested() {
        _uiState.update { it.copy(isEditingTitle = true) }
        Log.d("DetailsViewModel", "Title edit requested.")
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(editableTitle = newTitle) }
    }

    fun onTitleSaveRequested() {
        val state = _uiState.value
        val meta = currentMetadata
        val newTitle = state.editableTitle.trim()

        if (meta == null) {
            Log.e("DetailsViewModel", "Cannot save title: Current metadata is null.")
            _uiState.update { it.copy(isEditingTitle = false, error = application.getString(R.string.error_metadata_update_failed)) }
            return
        }

        if (newTitle.isEmpty()) {
            Log.w("DetailsViewModel", "Save requested with empty title. Reverting.")
            _uiState.update { it.copy(isEditingTitle = false, editableTitle = meta.title ?: "", error = "Title cannot be empty.") }
            return
        }

        if (newTitle != meta.title) {
            Log.d("DetailsViewModel", "Saving new title: $newTitle for path: ${meta.filePath}")
            _uiState.update { it.copy(isEditingTitle = false, title = newTitle, isLoading = true) }

            viewModelScope.launch {
                val success = localAudioRepository.updateRecordingTitle(meta.filePath, newTitle)

                if (!success) {
                    Log.e("DetailsViewModel", "Failed to update title in repository for ${meta.filePath}")
                    _uiState.update { it.copy(isLoading = false, title = meta.title ?: "", error = application.getString(R.string.error_metadata_update_failed)) }
                } else {
                    Log.d("DetailsViewModel", "Title updated successfully in repository.")
                    localAudioRepository.getRecordingMetadata(meta.filePath).firstOrNull()?.getOrNull()?.let { updatedMeta ->
                        currentMetadata = updatedMeta
                    }
                    _uiState.update { it.copy(isLoading = false, infoMessage = "Title updated.") }
                }
            }
        } else {
            Log.d("DetailsViewModel", "Title unchanged. Exiting edit mode.")
            _uiState.update { it.copy(isEditingTitle = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        playbackManager.releasePlayer()
        Log.d("DetailsViewModel", "ViewModel cleared, polling stopped, player released.")
    }
}