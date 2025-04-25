package edu.cit.audioscholar.ui.details

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto
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

private const val POLLING_INTERVAL_MS = 10000L
private const val POLLING_TIMEOUT_MS = 120000L
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
                        val cachedSummary = metadata.cachedSummaryText
                        val cachedGlossary = metadata.cachedGlossaryItems
                        val cachedRecs = metadata.cachedRecommendations

                        val isSummaryCacheValid = isCacheValid(cacheTimestamp) && !cachedSummary.isNullOrBlank()
                        val areRecommendationsCacheValid = isCacheValid(cacheTimestamp) && !cachedRecs.isNullOrEmpty()

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
                                summaryText = if (isSummaryCacheValid) cachedSummary ?: "" else "",
                                glossaryItems = if (isSummaryCacheValid) cachedGlossary ?: emptyList() else emptyList(),
                                youtubeRecommendations = if (areRecommendationsCacheValid) cachedRecs ?: emptyList() else emptyList(),
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
                    filePath = "",
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
        if (localMetadata != null && File(localMetadata.filePath).exists()) {
            Log.d("DetailsViewModel", "Configuring playback for local file: ${localMetadata.filePath}")
            playbackManager.preparePlayer(localMetadata.filePath)
        } else if (!cloudUrl.isNullOrBlank()) {
            Log.d("DetailsViewModel", "Configuring playback for cloud streaming: $cloudUrl")
            playbackManager.preparePlayerForStreaming(cloudUrl)
        } else {
            Log.w("DetailsViewModel", "ConfigurePlayback called with no valid source. Local path: ${localMetadata?.filePath}, Cloud URL: $cloudUrl")
            _uiState.update { it.copy(error = "Cannot prepare playback: Recording source missing or invalid.") }
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
                summaryFetchSuccess = pollForData(
                    tag = "Summary",
                    timeoutMs = POLLING_TIMEOUT_MS,
                    intervalMs = POLLING_INTERVAL_MS,
                    fetchAction = { remoteAudioRepository.getSummary(remoteId) },
                    onSuccess = { summaryDto ->
                        Log.i("DetailsViewModel", "Summary polling successful for $remoteId.")
                        val updatedState = _uiState.updateAndGet {
                            it.copy(
                                summaryStatus = SummaryStatus.READY,
                                summaryText = summaryDto.formattedSummaryText ?: "",
                                glossaryItems = summaryDto.glossary ?: emptyList(),
                                error = null
                            )
                        }
                        val cacheTimestamp = System.currentTimeMillis()
                        if (!updatedState.isCloudSource) {
                            currentMetadata?.let { localMeta ->
                                val updatedMeta = localMeta.copy(
                                    cachedSummaryText = summaryDto.formattedSummaryText,
                                    cachedGlossaryItems = summaryDto.glossary,
                                    cacheTimestampMillis = cacheTimestamp
                                )
                                Log.d("DetailsViewModel", "Saving fetched summary data to local cache for ${localMeta.filePath}")
                                viewModelScope.launch(Dispatchers.IO) {
                                    val saveSuccess = localAudioRepository.saveMetadata(updatedMeta)
                                    if (saveSuccess) {
                                        currentMetadata = updatedMeta
                                        Log.i("DetailsViewModel", "Local summary cache saved successfully.")
                                    } else {
                                        Log.e("DetailsViewModel", "Failed to save summary data to local cache.")
                                    }
                                }
                            } ?: Log.w("DetailsViewModel", "Summary fetched for local source, but currentMetadata is null!")
                        } else {
                            Log.d("DetailsViewModel", "Saving fetched summary data to cloud cache for $remoteId")
                            viewModelScope.launch(Dispatchers.IO) {
                                cloudCacheRepository.saveSummaryToCache(remoteId, summaryDto, cacheTimestamp)
                            }
                        }
                    },
                    onFailure = { errorMsg ->
                        Log.e("DetailsViewModel", "Summary polling failed for $remoteId: $errorMsg")
                        _uiState.update { it.copy(summaryStatus = SummaryStatus.FAILED, error = errorMsg) }
                    },
                    onTimeout = {
                        Log.w("DetailsViewModel", "Summary polling timed out for $remoteId.")
                        val errorMsg = application.getString(R.string.error_processing_timeout)
                        _uiState.update { it.copy(summaryStatus = SummaryStatus.FAILED, error = errorMsg) }
                        viewModelScope.launch { _errorEvent.send(errorMsg) }
                    }
                )
            }

            val shouldPollRecs = summaryFetchSuccess && needsRecsFetch
            if (shouldPollRecs) {
                pollForData(
                    tag = "Recommendations",
                    timeoutMs = POLLING_TIMEOUT_MS,
                    intervalMs = POLLING_INTERVAL_MS,
                    fetchAction = { remoteAudioRepository.getRecommendations(remoteId) },
                    onSuccess = { recommendationsList ->
                        Log.i("DetailsViewModel", "Recommendations polling successful for $remoteId.")
                        val updatedState = _uiState.updateAndGet {
                            it.copy(
                                recommendationsStatus = RecommendationsStatus.READY,
                                youtubeRecommendations = recommendationsList,
                                error = null
                            )
                        }
                        val cacheTimestamp = System.currentTimeMillis()
                        if (!updatedState.isCloudSource) {
                            currentMetadata?.let { localMeta ->
                                val updatedMeta = localMeta.copy(
                                    cachedRecommendations = recommendationsList,
                                    cacheTimestampMillis = if (needsSummaryFetch) localMeta.cacheTimestampMillis else cacheTimestamp
                                )
                                Log.d("DetailsViewModel", "Saving fetched recommendations data to local cache for ${localMeta.filePath}")
                                viewModelScope.launch(Dispatchers.IO) {
                                    val saveSuccess = localAudioRepository.saveMetadata(updatedMeta)
                                    if (saveSuccess) {
                                        currentMetadata = updatedMeta
                                        Log.i("DetailsViewModel", "Local recommendations cache saved successfully.")
                                    } else {
                                        Log.e("DetailsViewModel", "Failed to save recommendations data to local cache.")
                                    }
                                }
                            } ?: Log.w("DetailsViewModel", "Recommendations fetched for local source, but currentMetadata is null!")
                        } else {
                            Log.d("DetailsViewModel", "Saving fetched recommendations data to cloud cache for $remoteId")
                            viewModelScope.launch(Dispatchers.IO) {
                                cloudCacheRepository.saveRecommendationsToCache(remoteId, recommendationsList, cacheTimestamp)
                            }
                        }
                    },
                    onFailure = { errorMsg ->
                        Log.e("DetailsViewModel", "Recommendations polling failed for $remoteId: $errorMsg")
                        _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = errorMsg) }
                    },
                    onTimeout = {
                        Log.w("DetailsViewModel", "Recommendations polling timed out for $remoteId.")
                        val errorMsg = application.getString(R.string.error_processing_timeout)
                        _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = errorMsg) }
                        viewModelScope.launch { _errorEvent.send(errorMsg) }
                    }
                )
            } else if (!summaryFetchSuccess && needsRecsFetch) {
                Log.w("DetailsViewModel", "Skipping recommendations polling for $remoteId because summary polling failed.")
                _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = it.error ?: "Recommendations could not be fetched.") }
            }
        }
    }


    private suspend fun <T> pollForData(
        tag: String,
        timeoutMs: Long,
        intervalMs: Long,
        fetchAction: () -> Flow<Result<T>>,
        onSuccess: (T) -> Unit,
        onFailure: (String) -> Unit,
        onTimeout: () -> Unit
    ): Boolean {
        Log.d("DetailsViewModelPolling", "[$tag] Starting polling...")
        return withTimeoutOrNull(timeoutMs) {
            var lastError: Throwable? = null
            var attemptCounter = 0

            while (isActive) {
                attemptCounter++
                var attemptSuccessful = false
                var shouldRetry = false
                var mappedErrorMessage: String? = null

                Log.d("DetailsViewModelPolling", "[$tag] Polling attempt #$attemptCounter")

                try {
                    fetchAction()
                        .catch { e ->
                            Log.w("DetailsViewModelPolling", "[$tag] Exception during fetch flow for attempt #$attemptCounter", e)
                            lastError = e
                            mappedErrorMessage = mapErrorToUserFriendlyMessage(e)
                            shouldRetry = e is IOException
                        }
                        .collect { result ->
                            result.onSuccess { data ->
                                Log.d("DetailsViewModelPolling", "[$tag] Polling attempt #$attemptCounter successful.")
                                attemptSuccessful = true
                                onSuccess(data)
                            }.onFailure { e ->
                                lastError = e
                                if (e is HttpException && (e.code() == 202 || e.code() == 404)) {
                                    Log.d("DetailsViewModelPolling", "[$tag] Received ${e.code()} on attempt #$attemptCounter, resource not ready yet. Will retry...")
                                    shouldRetry = true
                                } else {
                                    Log.w("DetailsViewModelPolling", "[$tag] Polling attempt #$attemptCounter failed with non-retryable error: ${e.message}")
                                    mappedErrorMessage = mapErrorToUserFriendlyMessage(e)
                                    shouldRetry = e is IOException
                                }
                            }
                        }
                } catch (e: CancellationException) {
                    Log.d("DetailsViewModelPolling", "[$tag] Polling cancelled.")
                    throw e
                } catch (e: Exception) {
                    Log.e("DetailsViewModelPolling", "[$tag] Unexpected exception during fetch attempt #$attemptCounter: ${e.message}", e)
                    lastError = e
                    mappedErrorMessage = mapErrorToUserFriendlyMessage(e)
                    shouldRetry = false
                }

                if (attemptSuccessful) {
                    Log.d("DetailsViewModelPolling", "[$tag] Polling finished successfully.")
                    return@withTimeoutOrNull true
                }

                if (shouldRetry && isActive) {
                    Log.d("DetailsViewModelPolling", "[$tag] Retrying polling after ${intervalMs}ms delay...")
                    delay(intervalMs)
                } else {
                    Log.w("DetailsViewModelPolling", "[$tag] Polling failed permanently. Last error: ${lastError?.message}")
                    onFailure(mappedErrorMessage ?: application.getString(R.string.error_unexpected_details_view))
                    return@withTimeoutOrNull false
                }
            }
            Log.w("DetailsViewModelPolling", "[$tag] Polling loop exited unexpectedly.")
            false
        } ?: run {
            Log.w("DetailsViewModelPolling", "[$tag] Polling timed out after ${timeoutMs}ms.")
            onTimeout()
            false
        }
    }


    fun onProcessRecordingClicked() {
        Log.d("DetailsViewModel", "Process Recording button clicked for: ${uiState.value.filePath}")
        val currentState = uiState.value
        val meta = currentMetadata

        if (currentState.isProcessing) {
            Log.w("DetailsViewModel", "Process Recording clicked, but already processing. Ignoring.")
            return
        }

        if (meta == null || meta.remoteRecordingId != null || meta.filePath.isEmpty()) {
            Log.w("DetailsViewModel", "Process Recording clicked but state is invalid. Meta: $meta, RemoteId: ${meta?.remoteRecordingId}, Path: ${meta?.filePath}")
            val errorMsg = when {
                meta?.remoteRecordingId != null -> "Recording has already been processed."
                meta?.filePath.isNullOrEmpty() -> "Recording file path is missing."
                else -> "Cannot process recording."
            }
            _uiState.update { it.copy(error = errorMsg) }
            viewModelScope.launch { _errorEvent.send(errorMsg) }
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
            val errorMsg = application.getString(R.string.error_local_storage_issue)
            _uiState.update { it.copy(
                summaryStatus = SummaryStatus.FAILED,
                recommendationsStatus = RecommendationsStatus.FAILED,
                error = errorMsg,
                uploadProgressPercent = null
            ) }
            viewModelScope.launch { _errorEvent.send(errorMsg) }
            return
        }

        val titleToUpload = (if (currentState.isEditingTitle) currentState.editableTitle else meta.title)
            ?.takeIf { it.isNotBlank() && it != fileToUpload.name }

        viewModelScope.launch {
            Log.d("DetailsViewModel", "Starting upload process via repository for File: ${fileToUpload.absolutePath}, Title: $titleToUpload")
            remoteAudioRepository.uploadAudioFile(
                audioFile = fileToUpload,
                title = titleToUpload,
                description = null
            )
                .catch { e ->
                    Log.e("DetailsViewModel", "Exception during upload flow collection", e)
                    val errorMsg = mapErrorToUserFriendlyMessage(e)
                    _uiState.update { it.copy(
                        uploadProgressPercent = null,
                        summaryStatus = SummaryStatus.FAILED,
                        recommendationsStatus = RecommendationsStatus.FAILED,
                        error = errorMsg)
                    }
                    viewModelScope.launch { _errorEvent.send(errorMsg) }
                }
                .collect { result ->
                    when (result) {
                        is UploadResult.Loading -> {
                            Log.d("DetailsViewModel", "Upload status: Loading")
                        }
                        is UploadResult.Progress -> {
                            Log.d("DetailsViewModel", "Upload status: Progress ${result.percentage}%")
                            _uiState.update { it.copy(uploadProgressPercent = result.percentage) }
                        }
                        is UploadResult.Success -> {
                            Log.d("DetailsViewModel", "Upload status: Success")
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
                                    val errorMsg = application.getString(R.string.error_metadata_update_failed)
                                    _uiState.update { it.copy(
                                        summaryStatus = SummaryStatus.FAILED,
                                        recommendationsStatus = RecommendationsStatus.FAILED,
                                        error = errorMsg
                                    ) }
                                    viewModelScope.launch { _errorEvent.send(errorMsg) }
                                }
                            } else {
                                Log.e("DetailsViewModel", "Upload successful but recordingId was missing in the response.")
                                val errorMsg = application.getString(R.string.error_server_generic)
                                _uiState.update { it.copy(
                                    summaryStatus = SummaryStatus.FAILED,
                                    recommendationsStatus = RecommendationsStatus.FAILED,
                                    error = errorMsg
                                ) }
                                viewModelScope.launch { _errorEvent.send(errorMsg) }
                            }
                        }
                        is UploadResult.Error -> {
                            Log.e("DetailsViewModel", "Upload status: Error - ${result.message}")
                            val userFriendlyError = result.message ?: application.getString(R.string.error_upload_failed_generic)

                            _uiState.update { it.copy(
                                uploadProgressPercent = null,
                                summaryStatus = SummaryStatus.FAILED,
                                recommendationsStatus = RecommendationsStatus.FAILED,
                                error = userFriendlyError )
                            }
                            viewModelScope.launch { _errorEvent.send(userFriendlyError) }
                        }
                    }
                }
        }
    }

    private fun mapErrorToUserFriendlyMessage(e: Throwable): String {
        Log.w("DetailsViewModel", "Mapping error: ${e::class.java.simpleName} - ${e.message}")
        return when (e) {
            is IOException -> application.getString(R.string.error_network_connection_generic)
            is HttpException -> {
                when (e.code()) {
                    400 -> application.getString(R.string.error_upload_failed_generic) + " (Code: 400)"
                    401 -> application.getString(R.string.error_unauthorized)
                    403 -> application.getString(R.string.error_upload_failed_generic) + " (Code: 403)"
                    404 -> application.getString(R.string.error_upload_failed_generic) + " (Code: 404)"
                    415 -> application.getString(R.string.error_upload_failed_generic) + " (Code: 415)"
                    in 500..599 -> application.getString(R.string.error_server_generic)
                    else -> application.getString(R.string.error_upload_failed_generic) + " (Code: ${e.code()})"
                }
            }
            is CancellationException -> "Operation cancelled."
            else -> application.getString(R.string.error_unexpected_details_view)
        }
    }


    fun onPlayPauseToggle() {
        val currentState = uiState.value
        val newIsPlaying = !currentState.isPlaying

        if (!currentState.isPlaybackReady) {
            Log.w("DetailsViewModel", "Play/Pause toggle attempted but playback not ready. State: $currentState")
            val errorMsg = "Cannot play: Recording source not loaded or processing."
            _uiState.update { it.copy(error = errorMsg) }
            viewModelScope.launch { _errorEvent.send(errorMsg)}
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
            viewModelScope.launch { _infoMessageEvent.send("Summary & Notes copied!") }
            _uiState.update { it.copy(textToCopy = combinedText) }
        } else {
            Log.w("DetailsViewModel", "Copy attempt failed: Summary not ready or empty.")
            val errorMsg = "Summary and notes are not available to copy."
            _uiState.update { it.copy(error = errorMsg) }
            viewModelScope.launch { _errorEvent.send(errorMsg) }
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
            val filename = getFileNameFromUri(uri)
            Log.d("DetailsViewModel", "Setting attached PowerPoint: $uri")
            val infoMsg = "PowerPoint attached: $filename"
            _uiState.update { it.copy(attachedPowerPoint = uri.toString(), infoMessage = infoMsg) }
            viewModelScope.launch { _infoMessageEvent.send(infoMsg) }
        } else {
            val infoMsg = "PowerPoint selection cancelled."
            _uiState.update { it.copy(infoMessage = infoMsg) }
            viewModelScope.launch { _infoMessageEvent.send(infoMsg) }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "Attached File"
        try {
            application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DetailsViewModel", "Error getting filename from URI: $uri", e)
            name = uri.lastPathSegment ?: "Attached File"
        }
        return name
    }


    fun detachPowerPoint() {
        Log.d("DetailsViewModel", "Detach PowerPoint clicked.")
        val infoMsg = "PowerPoint detached."
        _uiState.update { it.copy(attachedPowerPoint = null, infoMessage = infoMsg) }
        viewModelScope.launch { _infoMessageEvent.send(infoMsg) }
    }

    fun onWatchYouTubeVideo(video: RecommendationDto) {
        val videoId = video.videoId
        if (!videoId.isNullOrBlank()) {
            Log.d("DetailsViewModel", "Watch YouTube clicked: ${video.title} (ID: $videoId)")
            val url = "https://www.youtube.com/watch?v=$videoId"
            viewModelScope.launch {
                _openUrlEvent.emit(url)
            }
        } else {
            Log.w("DetailsViewModel", "Watch YouTube clicked, but videoId is null or blank for: ${video.title}")
            val errorMsg = "Cannot open video: Missing video ID."
            _uiState.update { it.copy(error = errorMsg) }
            viewModelScope.launch { _errorEvent.send(errorMsg) }
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
            var deleteSuccess = false
            var infoMsg: String? = null
            var errorMsg: String? = null

            try {
                if (uiState.value.isCloudSource) {
                    val idToDelete = uiState.value.remoteRecordingId
                    if (idToDelete == null) {
                        Log.e("DetailsViewModel", "Cannot delete cloud recording: Remote ID is missing.")
                        errorMsg = "Cannot delete: Recording ID unknown."
                    } else {
                        Log.d("DetailsViewModel", "Attempting to delete cloud recording with ID: $idToDelete")
                        Log.w("DetailsViewModel", "Cloud delete endpoint not implemented yet.")
                        delay(1000)
                        deleteSuccess = true
                        if (deleteSuccess) infoMsg = "Cloud recording deleted."
                        else errorMsg = "Failed to delete cloud recording (Not implemented)."
                    }
                } else {
                    val metaToDelete = currentMetadata
                    if (metaToDelete == null || metaToDelete.filePath.isEmpty()) {
                        Log.e("DetailsViewModel", "Cannot delete local recording: Metadata or file path is missing.")
                        errorMsg = "Cannot delete recording: file path unknown."
                    } else {
                        Log.d("DetailsViewModel", "Attempting to delete local recording: ${metaToDelete.filePath}")
                        deleteSuccess = localAudioRepository.deleteLocalRecording(metaToDelete)
                        if (deleteSuccess) {
                            Log.d("DetailsViewModel", "Local deletion successful for: ${metaToDelete.filePath}")
                            playbackManager.releasePlayer()
                            currentMetadata = null
                            infoMsg = "Recording deleted."
                        } else {
                            Log.e("DetailsViewModel", "Error deleting local file: ${metaToDelete.filePath}")
                            errorMsg = "Failed to delete recording file."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailsViewModel", "Exception during delete operation", e)
                errorMsg = mapErrorToUserFriendlyMessage(e)
                deleteSuccess = false
            }

            if (deleteSuccess) {
                _uiState.update { it.copy(
                    isDeleting = false,
                    infoMessage = infoMsg,
                    filePath = if (!it.isCloudSource) "" else it.filePath,
                    remoteRecordingId = if (it.isCloudSource) null else it.remoteRecordingId,
                    title = "Deleted",
                    editableTitle = "Deleted",
                    storageUrl = null,
                    summaryStatus = SummaryStatus.IDLE,
                    recommendationsStatus = RecommendationsStatus.IDLE,
                    summaryText = "",
                    glossaryItems = emptyList(),
                    youtubeRecommendations = emptyList(),
                    durationMillis = 0L,
                    durationFormatted = "00:00",
                    currentPositionMillis = 0L,
                    currentPositionFormatted = "00:00",
                    playbackProgress = 0f,
                    isPlaying = false
                ) }
                _infoMessageEvent.trySend(infoMsg)
            } else {
                _uiState.update { it.copy(isDeleting = false, error = errorMsg) }
                _errorEvent.trySend(errorMsg)
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
        _uiState.update { it.copy(isEditingTitle = true, editableTitle = it.title) }
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
            val errorMsg = "Title cannot be empty."
            _uiState.update { it.copy(isEditingTitle = false, editableTitle = state.title, error = errorMsg) }
            viewModelScope.launch { _errorEvent.send(errorMsg)}
            return
        }

        if (newTitle == state.title) {
            Log.d("DetailsViewModel", "Title unchanged. Exiting edit mode.")
            _uiState.update { it.copy(isEditingTitle = false) }
            return
        }

        _uiState.update { it.copy(isEditingTitle = false, title = newTitle, isLoading = true) }

        viewModelScope.launch {
            var success = false
            var errorMsg: String? = null

            try {
                if (state.isCloudSource) {
                    val remoteId = state.remoteRecordingId
                    if (remoteId == null) {
                        Log.e("DetailsViewModel", "Cannot update cloud title: Remote ID is null.")
                        errorMsg = "Cannot update title: Recording ID unknown."
                    } else {
                        Log.d("DetailsViewModel", "Attempting to update cloud title for ID: $remoteId to '$newTitle'")
                        Log.w("DetailsViewModel", "Cloud title update endpoint not implemented yet.")
                        delay(1000)
                        success = true
                    }
                } else {
                    val meta = currentMetadata
                    if (meta == null || meta.filePath.isEmpty()) {
                        Log.e("DetailsViewModel", "Cannot save local title: Current metadata or path is null.")
                        errorMsg = "Cannot update title: Recording metadata missing."
                    } else {
                        Log.d("DetailsViewModel", "Saving local title: '$newTitle' for path: ${meta.filePath}")
                        success = localAudioRepository.updateRecordingTitle(meta.filePath, newTitle)
                        if (success) {
                            currentMetadata = meta.copy(title = newTitle)
                        } else {
                            errorMsg = application.getString(R.string.error_metadata_update_failed)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailsViewModel", "Exception during title update", e)
                errorMsg = mapErrorToUserFriendlyMessage(e)
                success = false
            }

            if (!success) {
                Log.e("DetailsViewModel", "Failed to update title. Reverting UI.")
                _uiState.update { it.copy(isLoading = false, title = state.title, editableTitle = state.title, error = errorMsg) }
                viewModelScope.launch { _errorEvent.send(errorMsg ?: application.getString(R.string.error_metadata_update_failed)) }
            } else {
                Log.d("DetailsViewModel", "Title updated successfully.")
                val infoMsg = "Title updated."
                _uiState.update { it.copy(isLoading = false, infoMessage = infoMsg) }
                viewModelScope.launch { _infoMessageEvent.send(infoMsg) }
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
        if (json.isNullOrBlank()) return null
        return try {
            val type = object : TypeToken<List<RecommendationDto>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e("DetailsViewModel", "Failed to parse Recommendations JSON from cache", e)
            null
        }
    }

    private fun parseGlossary(json: String?): List<GlossaryItemDto>? {
        if (json.isNullOrBlank()) return null
        return try {
            val type = object : TypeToken<List<GlossaryItemDto>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e("DetailsViewModel", "Failed to parse Glossary JSON from cache", e)
            null
        }
    }
}