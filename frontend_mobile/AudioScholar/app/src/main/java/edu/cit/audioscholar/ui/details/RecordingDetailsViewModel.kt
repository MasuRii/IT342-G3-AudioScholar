package edu.cit.audioscholar.ui.details

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto
import edu.cit.audioscholar.data.remote.dto.SummaryResponseDto
import edu.cit.audioscholar.domain.repository.*
import edu.cit.audioscholar.service.PlaybackManager
import edu.cit.audioscholar.service.PlaybackState
import edu.cit.audioscholar.ui.main.Screen
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


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
    private val savedStateHandle: SavedStateHandle,
    private val localAudioRepository: LocalAudioRepository,
    private val playbackManager: PlaybackManager,
    private val remoteAudioRepository: RemoteAudioRepository,
    private val cloudCacheRepository: CloudCacheRepository,
    private val gson: Gson,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingDetailsUiState())
    val uiState: StateFlow<RecordingDetailsUiState> = _uiState.asStateFlow()

    private val _triggerFilePicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerFilePicker: SharedFlow<Unit> = _triggerFilePicker.asSharedFlow()

    private val _openUrlEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    private val _errorEvent = Channel<String?>(Channel.BUFFERED)
    val errorEvent: Flow<String?> = _errorEvent.receiveAsFlow()
    private val _infoMessageEvent = Channel<String?>(Channel.BUFFERED)
    val infoMessageEvent: Flow<String?> = _infoMessageEvent.receiveAsFlow()

    private val localFilePath: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_LOCAL_FILE_PATH)
    private val cloudRecordingId: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_RECORDING_ID)

    private var pollingJob: Job? = null
    private var currentMetadata: RecordingMetadata? = null
    private var currentCloudMetadata: AudioMetadataDto? = null


    init {
        Log.d("DetailsViewModel", "Initializing. LocalPath: $localFilePath, CloudID: $cloudRecordingId")

        when {
            localFilePath != null -> {
                Log.d("DetailsViewModel", "Source: Local File")
                _uiState.update { it.copy(isCloudSource = false) }
                loadLocalRecordingDetails(localFilePath)
                observePlaybackState()
            }
            cloudRecordingId != null -> {
                Log.d("DetailsViewModel", "Source: Cloud Recording")
                _uiState.update { it.copy(isCloudSource = true) }
                loadInitialCloudDetailsFromArgsAndPoll(cloudRecordingId)
                observePlaybackState()
            }
            else -> {
                Log.e("DetailsViewModel", "Initialization error: No local path or cloud ID found in arguments.")
                _uiState.update { it.copy(isLoading = false, error = application.getString(R.string.error_unexpected_details_view)) }
                viewModelScope.launch { _errorEvent.send(application.getString(R.string.error_unexpected_details_view)) }
            }
        }
    }

    private fun isCacheValid(cacheTimestampMillis: Long?): Boolean {
        if (cacheTimestampMillis == null) return false
        return (System.currentTimeMillis() - cacheTimestampMillis) < CACHE_VALIDITY_MS
    }

    private fun loadLocalRecordingDetails(filePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            localAudioRepository.getRecordingMetadata(filePath)
                .catch { e ->
                    Log.e("DetailsViewModel", "Error loading local details for $filePath", e)
                    val errorMsg = mapErrorToUserFriendlyMessage(e)
                    _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                    _errorEvent.trySend(errorMsg)
                }
                .collect { result ->
                    result.onSuccess { metadata ->
                        currentMetadata = metadata
                        val currentTitle = metadata.title ?: metadata.fileName
                        val remoteId = metadata.remoteRecordingId
                        val cacheTimestamp = metadata.cacheTimestampMillis
                        val isSummaryCacheValid = isCacheValid(cacheTimestamp) && !metadata.cachedSummaryText.isNullOrBlank()
                        val areRecommendationsCacheValid = isCacheValid(cacheTimestamp) && !metadata.cachedRecommendations.isNullOrEmpty()

                        Log.d("DetailsViewModel", "Local metadata loaded. RemoteId: $remoteId, CacheTime: $cacheTimestamp, SummaryCacheValid: $isSummaryCacheValid, RecsCacheValid: $areRecommendationsCacheValid")

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
                                storageUrl = null,
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
                                youtubeRecommendations = if (areRecommendationsCacheValid) metadata.cachedRecommendations ?: emptyList() else emptyList(),
                                isCloudSource = false
                            )
                        }
                        configurePlayback(localMetadata = metadata)

                        val needsPolling = remoteId != null && (!isSummaryCacheValid || !areRecommendationsCacheValid)
                        if (needsPolling) {
                            Log.d("DetailsViewModel", "Local record with remote ID ($remoteId). Cache invalid/missing, starting polling for needed data.")
                            startPollingForSummaryAndRecommendations(remoteId, !isSummaryCacheValid, !areRecommendationsCacheValid)
                        } else {
                            Log.d("DetailsViewModel", "Local record. Cache is valid or no remote ID. No polling needed now.")
                        }

                    }.onFailure { e ->
                        Log.e("DetailsViewModel", "Failed result loading local details for $filePath", e)
                        val errorMsg = mapErrorToUserFriendlyMessage(e)
                        _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                        _errorEvent.trySend(errorMsg)
                    }
                }
        }
    }

    private fun loadInitialCloudDetailsFromArgsAndPoll(recId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val title: String = Uri.decode(savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_TITLE) ?: "Cloud Recording")
            val fileName: String = Uri.decode(savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_FILENAME) ?: "Unknown Filename")
            val timestampSeconds: Long = savedStateHandle.get<Long>(Screen.RecordingDetails.ARG_CLOUD_TIMESTAMP_SECONDS) ?: 0L
            val storageUrl: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_STORAGE_URL)?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }

            Log.d("DetailsViewModel", "Loading cloud details from args: ID='$recId', Title='$title', Filename='$fileName', TimestampSecs=$timestampSeconds, StorageUrl='$storageUrl'")

            if (recId.isBlank()) {
                Log.e("DetailsViewModel", "Cloud recording ID from args is blank!")
                val errorMsg = "Invalid Recording ID."
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                _errorEvent.trySend(errorMsg)
                return@launch
            }

            var needsSummaryFetch = true
            var needsRecsFetch = true
            var initialSummaryStatus = SummaryStatus.PROCESSING
            var initialRecsStatus = RecommendationsStatus.LOADING
            var cachedSummaryText: String? = null
            var cachedGlossary: List<GlossaryItemDto>? = null
            var cachedRecs: List<RecommendationDto>? = null

            cloudCacheRepository.getCache(recId)?.let { cloudCache ->
                Log.d("DetailsViewModel", "Found dedicated cloud cache entry for $recId.")
                val cacheTimestamp = cloudCache.cacheTimestampMillis
                val isSummaryCacheValid = isCacheValid(cacheTimestamp) && !cloudCache.summaryText.isNullOrBlank()
                val areRecsCacheValid = isCacheValid(cacheTimestamp) && !cloudCache.recommendationsJson.isNullOrBlank()

                Log.d("DetailsViewModel", "[CloudCache] Check for $recId: SummaryValid=$isSummaryCacheValid, RecsValid=$areRecsCacheValid")

                if (isSummaryCacheValid) {
                    needsSummaryFetch = false
                    initialSummaryStatus = SummaryStatus.READY
                    cachedSummaryText = cloudCache.summaryText
                    cachedGlossary = parseGlossary(cloudCache.glossaryJson)
                }
                if (areRecsCacheValid) {
                    needsRecsFetch = false
                    initialRecsStatus = RecommendationsStatus.READY
                    cachedRecs = parseRecommendations(cloudCache.recommendationsJson)
                }
            } ?: Log.d("DetailsViewModel", "No dedicated cloud cache entry found for $recId.")

            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = title,
                    editableTitle = title,
                    dateCreated = if (timestampSeconds > 0) formatTimestampMillis(TimeUnit.SECONDS.toMillis(timestampSeconds)) else "Unknown Date",
                    durationMillis = 0L,
                    durationFormatted = "--:--",
                    filePath = currentMetadata?.filePath ?: "",
                    remoteRecordingId = recId,
                    storageUrl = storageUrl,
                    error = null,
                    summaryStatus = initialSummaryStatus,
                    recommendationsStatus = initialRecsStatus,
                    summaryText = cachedSummaryText ?: "",
                    glossaryItems = cachedGlossary ?: emptyList(),
                    youtubeRecommendations = cachedRecs ?: emptyList(),
                    isCloudSource = true
                )
            }

            storageUrl?.let { configurePlayback(cloudUrl = it) }

            if (needsSummaryFetch || needsRecsFetch) {
                Log.d("DetailsViewModel", "Cache invalid or missing for cloud ID $recId. Starting polling.")
                startPollingForSummaryAndRecommendations(recId, needsSummaryFetch, needsRecsFetch)
            } else {
                Log.d("DetailsViewModel", "Cache is valid for cloud ID $recId. No polling needed.")
            }
        }
    }

    private fun configurePlayback(localMetadata: RecordingMetadata? = null, cloudUrl: String? = null) {
        if (localMetadata != null) {
            Log.d("DetailsViewModel", "Configuring playback for local file: ${localMetadata.filePath}")
            playbackManager.preparePlayer(localMetadata.filePath)
        } else if (!cloudUrl.isNullOrBlank()) {
            Log.d("DetailsViewModel", "Configuring playback for cloud streaming: $cloudUrl")
            playbackManager.preparePlayerForStreaming(cloudUrl)
        } else {
            Log.w("DetailsViewModel", "ConfigurePlayback called with no valid source.")
        }
    }

    private fun observePlaybackState() {
        playbackManager.playbackState
            .onEach { playbackState: PlaybackState ->
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
            Log.d("DetailsViewModel", "Polling requested but nothing to poll for.")
            return
        }

        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            Log.d("DetailsViewModel", "Polling check for remoteId: $remoteId. Request PollSummary: $pollForSummary, Request PollRecs: $pollForRecommendations")

            val needsSummaryFetch = pollForSummary && uiState.value.summaryStatus != SummaryStatus.READY
            val needsRecsFetch = pollForRecommendations && uiState.value.recommendationsStatus != RecommendationsStatus.READY

            Log.d("DetailsViewModel", "Actual polling needs: NeedSummary=$needsSummaryFetch, NeedRecs=$needsRecsFetch")

            _uiState.update {
                it.copy(
                    summaryStatus = if (needsSummaryFetch) SummaryStatus.PROCESSING else it.summaryStatus,
                    recommendationsStatus = if (needsRecsFetch) RecommendationsStatus.LOADING else it.recommendationsStatus,
                    error = null
                )
            }

            if (!needsSummaryFetch && !needsRecsFetch) {
                Log.d("DetailsViewModel", "Polling not needed as data is already ready (likely from cache).")
                pollingJob?.cancel()
                return@launch
            }

            var summaryFetchSuccess = !needsSummaryFetch

            if (needsSummaryFetch) {
                summaryFetchSuccess = pollForData<SummaryResponseDto>(
                    timeoutMs = POLLING_TIMEOUT_MS,
                    intervalMs = POLLING_INTERVAL_MS,
                    fetchAction = { remoteAudioRepository.getSummary(remoteId) },
                    onSuccess = { summaryDto ->
                        Log.i("DetailsViewModel", "Summary polling successful. Updating UI state & saving to cloud cache.")
                        _uiState.update {
                            it.copy(
                                summaryStatus = SummaryStatus.READY,
                                summaryText = summaryDto.formattedSummaryText ?: "",
                                glossaryItems = summaryDto.glossary ?: emptyList(),
                                error = null
                            )
                        }
                        viewModelScope.launch(Dispatchers.IO) {
                            cloudCacheRepository.saveSummaryToCache(remoteId, summaryDto)
                        }
                    },
                    onFailure = { errorMsg ->
                        Log.e("DetailsViewModel", "Summary polling failed: $errorMsg")
                        _uiState.update { it.copy(summaryStatus = SummaryStatus.FAILED, error = errorMsg) }
                        viewModelScope.launch { _errorEvent.send(errorMsg) }
                    },
                    onTimeout = {
                        Log.w("DetailsViewModel", "Summary polling timed out.")
                        val errorMsg = application.getString(R.string.error_processing_timeout)
                        _uiState.update { it.copy(summaryStatus = SummaryStatus.FAILED, error = errorMsg) }
                        viewModelScope.launch { _errorEvent.send(errorMsg) }
                    }
                )
            }

            val shouldPollRecs = summaryFetchSuccess && needsRecsFetch
            if (shouldPollRecs) {
                pollForData<List<RecommendationDto>>(
                    timeoutMs = POLLING_TIMEOUT_MS,
                    intervalMs = POLLING_INTERVAL_MS,
                    fetchAction = { remoteAudioRepository.getRecommendations(remoteId) },
                    onSuccess = { recommendationsList ->
                        Log.i("DetailsViewModel", "Recommendations polling successful. Updating UI state & saving to cloud cache.")
                        _uiState.update {
                            it.copy(
                                recommendationsStatus = RecommendationsStatus.READY,
                                youtubeRecommendations = recommendationsList,
                                error = null
                            )
                        }
                        viewModelScope.launch(Dispatchers.IO) {
                            cloudCacheRepository.saveRecommendationsToCache(remoteId, recommendationsList)
                        }
                    },
                    onFailure = { errorMsg ->
                        Log.e("DetailsViewModel", "Recommendations polling failed: $errorMsg")
                        _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = errorMsg) }
                        viewModelScope.launch { _errorEvent.send(errorMsg) }
                    },
                    onTimeout = {
                        Log.w("DetailsViewModel", "Recommendations polling timed out.")
                        val errorMsg = application.getString(R.string.error_processing_timeout)
                        _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = errorMsg) }
                        viewModelScope.launch { _errorEvent.send(errorMsg) }
                    }
                )
            } else if (!summaryFetchSuccess && needsRecsFetch) {
                Log.w("DetailsViewModel", "Skipping recommendations polling because summary polling failed.")
                _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = it.error ?: "Recommendations could not be fetched.") }
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
        val currentState = uiState.value
        val newIsPlaying = !currentState.isPlaying

        if (currentState.filePath.isEmpty() && currentState.storageUrl.isNullOrBlank()) {
            Log.w("DetailsViewModel", "Play/Pause toggle attempted but no valid playback source (local path or cloud url) is available.")
            _uiState.update { it.copy(error = "Cannot play: Recording source not loaded.") }
            viewModelScope.launch { _errorEvent.send("Cannot play: Recording source not loaded.")}
            return
        }

        if (newIsPlaying) {
            playbackManager.play()
        } else {
            playbackManager.pause()
        }
        Log.d("DetailsViewModel", "Toggled playback. Requested playing: $newIsPlaying")
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

    fun onPowerPointSelected(uri: Uri?) {
        if (uri != null) {
            Log.d("DetailsViewModel", "Setting attached PowerPoint: ${uri.toString()}")
            _uiState.update { it.copy(attachedPowerPoint = uri.toString(), infoMessage = "PowerPoint attached: ${uri.toString()}") }
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
            if (uiState.value.isCloudSource) {
                val idToDelete = uiState.value.remoteRecordingId
                if (idToDelete == null) {
                    Log.e("DetailsViewModel", "Cannot delete cloud recording: Remote ID is missing.")
                    _uiState.update { it.copy(isDeleting = false, error = "Cannot delete: Recording ID unknown.") }
                    _errorEvent.trySend("Cannot delete: Recording ID unknown.")
                    return@launch
                }
                Log.d("DetailsViewModel", "Attempting to delete cloud recording with ID: $idToDelete")
                Log.w("DetailsViewModel", "Cloud delete endpoint not implemented yet.")
                delay(1000)
                _uiState.update { it.copy(isDeleting = false, infoMessage = "Cloud recording delete requested (Not implemented).") }
                _infoMessageEvent.trySend("Cloud recording delete requested (Not implemented).")
                _uiState.update { it.copy( remoteRecordingId = null, title = "Deleted", storageUrl = null ) }
            } else {
                val metaToDelete = currentMetadata
                if (metaToDelete == null || metaToDelete.filePath.isEmpty()) {
                    Log.e("DetailsViewModel", "Cannot delete local recording: Metadata or file path is missing.")
                    _uiState.update { it.copy(isDeleting = false, error = "Cannot delete recording: file path unknown.") }
                    _errorEvent.trySend("Cannot delete recording: file path unknown.")
                    return@launch
                }

                val localDeleteSuccess = localAudioRepository.deleteLocalRecording(metaToDelete)

                if (localDeleteSuccess) {
                    Log.d("DetailsViewModel", "Local deletion successful for: ${metaToDelete.filePath}")
                    playbackManager.releasePlayer()
                    currentMetadata = null
                    _uiState.update { it.copy(filePath = "", isDeleting = false, infoMessage = "Recording deleted.") }
                    _infoMessageEvent.trySend("Recording deleted.")
                } else {
                    Log.e("DetailsViewModel", "Error deleting local file: ${metaToDelete.filePath}")
                    _uiState.update { it.copy( isDeleting = false, error = "Failed to delete recording file.") }
                    _errorEvent.trySend("Failed to delete recording file.")
                }
            }
        }
    }


    fun consumeError() {
        viewModelScope.launch { _errorEvent.send(null) }
        _uiState.update { it.copy(error = null) }
    }
    fun consumeInfoMessage() {
        viewModelScope.launch { _infoMessageEvent.send(null) }
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
        val newTitle = state.editableTitle.trim()

        if (newTitle.isEmpty()) {
            Log.w("DetailsViewModel", "Save requested with empty title.")
            _uiState.update { it.copy(isEditingTitle = false, editableTitle = state.title, error = "Title cannot be empty.") }
            viewModelScope.launch { _errorEvent.send("Title cannot be empty.")}
            return
        }

        if (newTitle == state.title) {
            Log.d("DetailsViewModel", "Title unchanged. Exiting edit mode.")
            _uiState.update { it.copy(isEditingTitle = false) }
            return
        }

        _uiState.update { it.copy(isEditingTitle = false, title = newTitle, isLoading = true) }

        viewModelScope.launch {
            val success: Boolean
            if (state.isCloudSource) {
                val remoteId = state.remoteRecordingId
                if (remoteId == null) {
                    Log.e("DetailsViewModel", "Cannot update cloud title: Remote ID is null.")
                    success = false
                } else {
                    Log.d("DetailsViewModel", "Attempting to update cloud title for ID: $remoteId to '$newTitle'")
                    Log.w("DetailsViewModel", "Cloud title update endpoint not implemented yet.")
                    delay(1000)
                    success = true
                }
            } else {
                val meta = currentMetadata
                if (meta == null) {
                    Log.e("DetailsViewModel", "Cannot save local title: Current metadata is null.")
                    success = false
                } else {
                    Log.d("DetailsViewModel", "Saving local title: $newTitle for path: ${meta.filePath}")
                    success = localAudioRepository.updateRecordingTitle(meta.filePath, newTitle)
                }
            }

            if (!success) {
                Log.e("DetailsViewModel", "Failed to update title in repository.")
                _uiState.update { it.copy(isLoading = false, title = state.title, editableTitle = state.title, error = application.getString(R.string.error_metadata_update_failed)) }
                viewModelScope.launch { _errorEvent.send(application.getString(R.string.error_metadata_update_failed)) }
            } else {
                Log.d("DetailsViewModel", "Title updated successfully in repository.")
                if (!state.isCloudSource) {
                    localAudioRepository.getRecordingMetadata(state.filePath).firstOrNull()?.getOrNull()?.let { updatedMeta ->
                        currentMetadata = updatedMeta
                    }
                }
                _uiState.update { it.copy(isLoading = false, infoMessage = "Title updated.") }
                viewModelScope.launch { _infoMessageEvent.send("Title updated.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        playbackManager.releasePlayer()
        Log.d("DetailsViewModel", "ViewModel cleared, polling stopped, player released.")
    }

    private fun parseRecommendations(json: String?): List<RecommendationDto>? {
        if (json == null) return null
        return try {
            val type = object : TypeToken<List<RecommendationDto>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e("DetailsViewModel", "Failed to parse Recommendations JSON from cache", e)
            null
        }
    }

    private fun parseGlossary(json: String?): List<GlossaryItemDto>? {
        if (json == null) return null
        return try {
            val type = object : TypeToken<List<GlossaryItemDto>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e("DetailsViewModel", "Failed to parse Glossary JSON from cache", e)
            null
        }
    }
}