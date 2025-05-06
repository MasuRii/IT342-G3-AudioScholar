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
import edu.cit.audioscholar.data.remote.dto.SummaryResponseDto
import edu.cit.audioscholar.domain.repository.*
import edu.cit.audioscholar.service.PlaybackManager
import edu.cit.audioscholar.service.PlaybackState
import edu.cit.audioscholar.ui.main.Screen
import edu.cit.audioscholar.ui.details.RecordingDetailsUiState
import edu.cit.audioscholar.ui.details.SummaryStatus
import edu.cit.audioscholar.ui.details.RecommendationsStatus
import edu.cit.audioscholar.util.ProcessingEventBus
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

sealed class NavigationEvent {
    object NavigateToLibrary : NavigationEvent()
}

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
    private val application: Application,
    private val processingEventBus: ProcessingEventBus
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

    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationEvent> = _navigationEvent.receiveAsFlow()

    private val localFilePath: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_LOCAL_FILE_PATH)
    private val cloudId: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_ID)
    private val cloudRecordingId: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_RECORDING_ID)

    private var pollingJob: Job? = null
    private var eventBusListenerJob: Job? = null
    private var currentMetadata: RecordingMetadata? = null

    init {
        Log.d("DetailsViewModel", "Initializing. LocalPath: $localFilePath, PrimaryCloudID: $cloudId, OtherCloudID: $cloudRecordingId")

        when {
            localFilePath != null -> {
                Log.d("DetailsViewModel", "Source: Local File")
                _uiState.update { it.copy(isCloudSource = false) }
                observePlaybackState()
                loadLocalRecordingDetailsAndListen(localFilePath)
            }
            cloudId != null -> {
                Log.d("DetailsViewModel", "Source: Cloud Recording (Primary ID: $cloudId, Other ID: $cloudRecordingId)")
                _uiState.update { it.copy(isCloudSource = true) }
                observePlaybackState()
                loadInitialCloudDetailsAndListen(cloudId, cloudRecordingId)
            }
            else -> {
                Log.e("DetailsViewModel", "Initialization error: No local path or cloud ID found in arguments.")
                _uiState.update { it.copy(isLoading = false, error = application.getString(R.string.error_unexpected_details_view)) }
                viewModelScope.launch { _errorEvent.trySend(application.getString(R.string.error_unexpected_details_view)) }
            }
        }
    }

    private fun isCacheValid(cacheTimestampMillis: Long?): Boolean {
        if (cacheTimestampMillis == null) return false
        return (System.currentTimeMillis() - cacheTimestampMillis) < CACHE_VALIDITY_MS
    }

    private fun loadLocalRecordingDetailsAndListen(filePath: String) {
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

                        val nextSummaryStatus = when {
                            isSummaryCacheValid -> SummaryStatus.READY
                            remoteId != null -> SummaryStatus.PROCESSING
                            else -> SummaryStatus.IDLE
                        }
                        val nextRecsStatus = when {
                            areRecommendationsCacheValid -> RecommendationsStatus.READY
                            remoteId != null -> RecommendationsStatus.LOADING
                            else -> RecommendationsStatus.IDLE
                        }
                        Log.i("DetailsViewModel", "[Local Load] Setting initial state. Summary Status: $nextSummaryStatus, Recs Status: $nextRecsStatus, Has Summary Text: ${!cachedSummary.isNullOrBlank()}")

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
                                cloudId = null,
                                storageUrl = null,
                                error = null,
                                summaryStatus = nextSummaryStatus,
                                recommendationsStatus = nextRecsStatus,
                                summaryText = if (isSummaryCacheValid) cachedSummary ?: "" else "",
                                glossaryItems = if (isSummaryCacheValid) cachedGlossary ?: emptyList() else emptyList(),
                                youtubeRecommendations = if (areRecommendationsCacheValid) cachedRecs ?: emptyList() else emptyList(),
                                isCloudSource = false
                            )
                        }
                        configurePlayback(localMetadata = metadata)

                        if (remoteId != null && (!isSummaryCacheValid || !areRecommendationsCacheValid)) {
                            Log.d("DetailsViewModel", "[Local Load] Remote ID exists and cache is invalid/incomplete. Starting listener and attempting immediate fetch.")
                            listenForProcessingCompletion(remoteId, !isSummaryCacheValid, !areRecommendationsCacheValid)

                            if (!isSummaryCacheValid) {
                                Log.d("DetailsViewModel", "[Local Load] Summary cache invalid, launching immediate fetch for $remoteId")
                                launch { fetchSummary(remoteId) }
                            }
                            if (!areRecommendationsCacheValid) {
                                Log.d("DetailsViewModel", "[Local Load] Recommendations cache invalid, launching immediate fetch for $remoteId")
                                launch { fetchRecommendations(remoteId) }
                            }
                        } else {
                            Log.d("DetailsViewModel", "Local record. Cache is valid or no remote ID. No direct fetch/listener needed now.")
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

    private fun loadInitialCloudDetailsAndListen(primaryId: String, recordingId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val title: String = Uri.decode(savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_TITLE) ?: "Cloud Recording")
            val fileName: String = Uri.decode(savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_FILENAME) ?: "Unknown Filename")
            val timestampSeconds: Long = savedStateHandle.get<Long>(Screen.RecordingDetails.ARG_CLOUD_TIMESTAMP_SECONDS) ?: 0L
            val storageUrl: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_STORAGE_URL)?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
            val audioUrl: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_AUDIO_URL)?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
            val generatedPdfUrl: String? = savedStateHandle.get<String>(Screen.RecordingDetails.ARG_CLOUD_PDF_URL)?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }

            Log.d("DetailsViewModel", "Loading cloud details from args: PrimaryID='$primaryId', RecordingID='$recordingId', Title='$title', Filename='$fileName', TimestampSecs=$timestampSeconds, StorageUrl='$storageUrl', AudioUrl='$audioUrl', PdfUrl='$generatedPdfUrl'")

            if (primaryId.isBlank()) {
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

            val idForCacheCheck = recordingId?.takeIf { it.isNotBlank() } ?: primaryId
            Log.d("DetailsViewModel", "Using ID for cache check: $idForCacheCheck")

            cloudCacheRepository.getCache(idForCacheCheck)?.let { cloudCache ->
                Log.d("DetailsViewModel", "Found dedicated cloud cache entry for $idForCacheCheck.")
                val cacheTimestamp = cloudCache.cacheTimestampMillis
                val isSummaryCacheValid = isCacheValid(cacheTimestamp) && !cloudCache.summaryText.isNullOrBlank()
                val areRecsCacheValid = isCacheValid(cacheTimestamp) && !cloudCache.recommendationsJson.isNullOrBlank()
                Log.d("DetailsViewModel", "[CloudCache] Check for $idForCacheCheck: SummaryValid=$isSummaryCacheValid, RecsValid=$areRecsCacheValid")

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
            } ?: Log.d("DetailsViewModel", "No dedicated cloud cache entry found for $idForCacheCheck.")

            Log.i("DetailsViewModel", "[Cloud Load] Setting initial state. Summary Status: $initialSummaryStatus, Recs Status: $initialRecsStatus, Has Summary Text: ${!cachedSummaryText.isNullOrBlank()}")

            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = title,
                    editableTitle = title,
                    dateCreated = if (timestampSeconds > 0) formatTimestampMillis(TimeUnit.SECONDS.toMillis(timestampSeconds)) else "Unknown Date",
                    durationMillis = 0L,
                    durationFormatted = "--:--",
                    filePath = "",
                    cloudId = primaryId,
                    remoteRecordingId = recordingId,
                    storageUrl = storageUrl,
                    audioUrl = audioUrl,
                    generatedPdfUrl = generatedPdfUrl,
                    error = null,
                    summaryStatus = initialSummaryStatus,
                    recommendationsStatus = initialRecsStatus,
                    summaryText = cachedSummaryText ?: "",
                    glossaryItems = cachedGlossary ?: emptyList(),
                    youtubeRecommendations = cachedRecs ?: emptyList(),
                    isCloudSource = true
                )
            }

            val playbackUrl = audioUrl?.takeIf { it.isNotBlank() } ?: storageUrl
            playbackUrl?.let { configurePlayback(cloudUrl = it) }

            val idToUseForFetch = recordingId?.takeIf { it.isNotBlank() } ?: primaryId
            if ((needsSummaryFetch || needsRecsFetch) && idToUseForFetch.isNotBlank()) {
                Log.d("DetailsViewModel", "[Cloud Load] Cache invalid/missing for $idToUseForFetch. Triggering direct fetches.")
                 if (needsSummaryFetch) {
                     launch { fetchSummary(idToUseForFetch) }
                 }
                 if (needsRecsFetch) {
                     launch { fetchRecommendations(idToUseForFetch) }
                 }
            } else {
                Log.d("DetailsViewModel", "[Cloud Load] Cache is valid for $idToUseForFetch or fetch ID missing. No fetch needed.")
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

    private fun listenForProcessingCompletion(targetId: String, fetchSummary: Boolean, fetchRecs: Boolean) {
        eventBusListenerJob?.cancel()
        Log.d("DetailsViewModel", "Starting FCM event listener for ID: $targetId. NeedSummary=$fetchSummary, NeedRecs=$fetchRecs")
        eventBusListenerJob = viewModelScope.launch {
            processingEventBus.processingCompleteEvent
                .filter { completedId ->
                    Log.d("DetailsViewModel", "[FCM LISTEN] EventBus received ID: $completedId (Filtering for: $targetId)")
                    completedId == targetId
                 }
                .collectLatest { completedId ->
                    Log.i("DetailsViewModel", "[FCM COLLECT] Received matching processing complete signal via EventBus for ID: $completedId")
                    pollingJob?.cancel()

                    val currentState = uiState.value
                    val stillNeedsSummary = fetchSummary && currentState.summaryStatus != SummaryStatus.READY
                    val stillNeedsRecs = fetchRecs && currentState.recommendationsStatus != RecommendationsStatus.READY

                    if (stillNeedsSummary || stillNeedsRecs) {
                        Log.d("DetailsViewModel", "Triggering direct fetch based on FCM signal. NeedSummary=$stillNeedsSummary, NeedRecs=$stillNeedsRecs")
                        if (stillNeedsSummary) {
                            launch { fetchSummary(targetId) }
                        }
                        if (stillNeedsRecs) {
                            launch { fetchRecommendations(targetId) }
                        }
                    } else {
                         Log.d("DetailsViewModel", "FCM signal received, but data seems already loaded. Ignoring signal.")
                    }

                    Log.d("DetailsViewModel", "Processed signal for $targetId. Fetches launched if needed.")
                }
        }
    }

    private suspend fun fetchSummary(remoteId: String) {
        Log.i("DetailsViewModel", "[DirectFetch START] Fetching summary for $remoteId")
        _uiState.update { it.copy(summaryStatus = SummaryStatus.PROCESSING) }
        try {
            remoteAudioRepository.getSummary(remoteId).collect { result ->
                result.onSuccess { summaryDto ->
                    Log.i("DetailsViewModel", "[DirectFetch SUCCESS] Summary fetch OK for $remoteId.")
                    val updatedState = _uiState.updateAndGet {
                        it.copy(
                            summaryStatus = SummaryStatus.READY,
                            summaryText = summaryDto.formattedSummaryText ?: "",
                            glossaryItems = summaryDto.glossary ?: emptyList(),
                            error = null
                        )
                    }
                    Log.d("DetailsViewModel", "[DirectFetch SUCCESS] UI State updated. New status: ${updatedState.summaryStatus}")
                    cacheSummary(remoteId, summaryDto)
                }.onFailure { e ->
                     Log.e("DetailsViewModel", "[DirectFetch FAILURE] Summary fetch inner fail for $remoteId", e)
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e("DetailsViewModel", "[DirectFetch CATCH] Summary fetch outer fail for $remoteId", e)
            val errorMsg = mapErrorToUserFriendlyMessage(e, default = "Failed to load summary.")
            _uiState.update { it.copy(summaryStatus = SummaryStatus.FAILED, error = errorMsg) }
            _errorEvent.trySend(errorMsg)
        }
    }

    private suspend fun fetchRecommendations(remoteId: String) {
        Log.i("DetailsViewModel", "[DirectFetch START] Fetching recommendations for $remoteId")
         _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.LOADING) }
        try {
            remoteAudioRepository.getRecommendations(remoteId).collect { result ->
                result.onSuccess { recommendationsList ->
                    Log.i("DetailsViewModel", "[DirectFetch SUCCESS] Recommendations fetch OK for $remoteId.")
                     val updatedState = _uiState.updateAndGet {
                         it.copy(
                            recommendationsStatus = RecommendationsStatus.READY,
                            youtubeRecommendations = recommendationsList,
                            error = null
                         )
                    }
                    Log.d("DetailsViewModel", "[DirectFetch SUCCESS] UI State updated. New status: ${updatedState.recommendationsStatus}")
                    cacheRecommendations(remoteId, recommendationsList)
                 }.onFailure { e ->
                    Log.e("DetailsViewModel", "[DirectFetch FAILURE] Recommendations fetch inner fail for $remoteId", e)
                    throw e
                 }
            }
        } catch (e: Exception) {
            Log.e("DetailsViewModel", "[DirectFetch CATCH] Recommendations fetch outer fail for $remoteId", e)
            val errorMsg = mapErrorToUserFriendlyMessage(e, default = "Failed to load recommendations.")
            _uiState.update { it.copy(recommendationsStatus = RecommendationsStatus.FAILED, error = errorMsg) }
             _errorEvent.trySend(errorMsg)
        }
    }

    private fun cacheSummary(remoteId: String, summaryDto: SummaryResponseDto) {
        val cacheTimestamp = System.currentTimeMillis()
        val currentState = uiState.value
        if (!currentState.isCloudSource) {
            currentMetadata?.let { localMeta ->
                val updatedMeta = localMeta.copy(
                    cachedSummaryText = summaryDto.formattedSummaryText,
                    cachedGlossaryItems = summaryDto.glossary,
                    cacheTimestampMillis = cacheTimestamp
                )
                Log.d("DetailsViewModel", "[Cache] Saving fetched summary data to local cache for ${localMeta.filePath}")
                viewModelScope.launch(Dispatchers.IO) {
                    val saveSuccess = localAudioRepository.saveMetadata(updatedMeta)
                    if (saveSuccess) {
                        currentMetadata = updatedMeta
                        Log.i("DetailsViewModel", "[Cache] Local summary cache saved successfully.")
                    } else {
                        Log.e("DetailsViewModel", "[Cache] Failed to save summary data to local cache.")
                    }
                }
            } ?: Log.w("DetailsViewModel", "[Cache] Summary fetched for local source, but currentMetadata is null!")
        } else {
            Log.d("DetailsViewModel", "[Cache] Saving fetched summary data to cloud cache for Recording ID: $remoteId")
            viewModelScope.launch(Dispatchers.IO) {
                cloudCacheRepository.saveSummaryToCache(remoteId, summaryDto, cacheTimestamp)
            }
        }
    }

    private fun cacheRecommendations(remoteId: String, recommendationsList: List<RecommendationDto>) {
         val cacheTimestamp = System.currentTimeMillis()
         val currentState = uiState.value
         if (!currentState.isCloudSource) {
            currentMetadata?.let { localMeta ->
                val finalTimestamp = if(currentState.summaryStatus == SummaryStatus.READY && localMeta.cacheTimestampMillis != null && localMeta.cacheTimestampMillis > cacheTimestamp - 5000) localMeta.cacheTimestampMillis else cacheTimestamp
                val updatedMeta = localMeta.copy(
                    cachedRecommendations = recommendationsList,
                    cacheTimestampMillis = finalTimestamp
                )
                Log.d("DetailsViewModel", "[Cache] Saving fetched recommendations data to local cache for ${localMeta.filePath}")
                viewModelScope.launch(Dispatchers.IO) {
                    val saveSuccess = localAudioRepository.saveMetadata(updatedMeta)
                    if (saveSuccess) {
                        currentMetadata = updatedMeta
                        Log.i("DetailsViewModel", "[Cache] Local recommendations cache saved successfully.")
                    } else {
                        Log.e("DetailsViewModel", "[Cache] Failed to save recommendations data to local cache.")
                    }
                }
            } ?: Log.w("DetailsViewModel", "[Cache] Recommendations fetched for local source, but currentMetadata is null!")
        } else {
            Log.d("DetailsViewModel", "[Cache] Saving fetched recommendations data to cloud cache for Recording ID: $remoteId")
            viewModelScope.launch(Dispatchers.IO) {
                cloudCacheRepository.saveRecommendationsToCache(remoteId, recommendationsList, cacheTimestamp)
            }
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
            viewModelScope.launch { _errorEvent.trySend(errorMsg) }
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
            viewModelScope.launch { _errorEvent.trySend(errorMsg) }
            return
        }

        val titleToUpload = (if (currentState.isEditingTitle) currentState.editableTitle else meta.title)
            ?.takeIf { it.isNotBlank() && it != fileToUpload.name }

        var powerpointFile: File? = null
        val attachedPowerPointUri = currentState.attachedPowerPoint
        if (!attachedPowerPointUri.isNullOrBlank()) {
            try {
                val uri = Uri.parse(attachedPowerPointUri)
                val extension = getFileExtensionFromUri(uri) ?: "pptx"
                powerpointFile = File(application.cacheDir, "temp_ppt_upload_${System.currentTimeMillis()}.$extension")
                
                Log.d("DetailsViewModel", "Creating temp file for PowerPoint: ${powerpointFile.absolutePath}")
                
                application.contentResolver.openInputStream(uri)?.use { inputStream ->
                    powerpointFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                if (!powerpointFile.exists() || powerpointFile.length() == 0L) {
                    Log.e("DetailsViewModel", "Failed to create temporary PowerPoint file or file is empty")
                    powerpointFile = null
                } else {
                    Log.d("DetailsViewModel", "PowerPoint temp file created successfully: ${powerpointFile.length()} bytes")
                }
                
            } catch (e: Exception) {
                Log.e("DetailsViewModel", "Error preparing PowerPoint file for upload", e)
                powerpointFile = null
                val errorMsg = "Failed to prepare PowerPoint file. Proceeding with audio upload only."
                viewModelScope.launch { _infoMessageEvent.trySend(errorMsg) }
            }
        }

        viewModelScope.launch {
            Log.d("DetailsViewModel", "Starting upload process via repository for File: ${fileToUpload.absolutePath}, PowerPoint: ${powerpointFile?.absolutePath}, Title: $titleToUpload")

            remoteAudioRepository.uploadAudioFile(
                audioFile = fileToUpload,
                powerpointFile = powerpointFile,
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
                    viewModelScope.launch { _errorEvent.trySend(errorMsg) }
                    
                    if (powerpointFile != null && powerpointFile.exists()) {
                        try {
                            if (powerpointFile.delete()) {
                                Log.d("DetailsViewModel", "Temporary PowerPoint file deleted after upload error")
                            }
                        } catch (ex: Exception) {
                            Log.w("DetailsViewModel", "Failed to delete temporary PowerPoint file", ex)
                        }
                    }
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

                            if (powerpointFile != null && powerpointFile.exists()) {
                                try {
                                    if (powerpointFile.delete()) {
                                        Log.d("DetailsViewModel", "Temporary PowerPoint file deleted after successful upload")
                                    }
                                } catch (ex: Exception) {
                                    Log.w("DetailsViewModel", "Failed to delete temporary PowerPoint file", ex)
                                }
                            }

                            if (remoteMetadataDto?.recordingId != null) {
                                val newRemoteId = remoteMetadataDto.recordingId
                                Log.i("DetailsViewModel", "Upload successful. Received remote recordingId: $newRemoteId")

                                val updatedMetaWithId = meta.copy(remoteRecordingId = newRemoteId)
                                val idSaveSuccess = localAudioRepository.saveMetadata(updatedMetaWithId)

                                if (idSaveSuccess) {
                                    currentMetadata = updatedMetaWithId
                                    Log.i("DetailsViewModel", "Successfully saved remoteRecordingId to local metadata.")
                                    _uiState.update { it.copy(remoteRecordingId = newRemoteId) }
                                    listenForProcessingCompletion(newRemoteId, fetchSummary = true, fetchRecs = true)
                                } else {
                                    Log.e("DetailsViewModel", "Failed to save remoteRecordingId to local metadata.")
                                    val errorMsg = application.getString(R.string.error_metadata_update_failed)
                                    _uiState.update { it.copy(
                                        summaryStatus = SummaryStatus.FAILED,
                                        recommendationsStatus = RecommendationsStatus.FAILED,
                                        error = errorMsg
                                    ) }
                                    viewModelScope.launch { _errorEvent.trySend(errorMsg) }
                                }
                            } else {
                                Log.e("DetailsViewModel", "Upload successful but recordingId was missing in the response.")
                                val errorMsg = application.getString(R.string.error_server_generic)
                                _uiState.update { it.copy(
                                    summaryStatus = SummaryStatus.FAILED,
                                    recommendationsStatus = RecommendationsStatus.FAILED,
                                    error = errorMsg
                                ) }
                                viewModelScope.launch { _errorEvent.trySend(errorMsg) }
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
                            viewModelScope.launch { _errorEvent.trySend(userFriendlyError) }
                            
                            if (powerpointFile != null && powerpointFile.exists()) {
                                try {
                                    if (powerpointFile.delete()) {
                                        Log.d("DetailsViewModel", "Temporary PowerPoint file deleted after upload error")
                                    }
                                } catch (ex: Exception) {
                                    Log.w("DetailsViewModel", "Failed to delete temporary PowerPoint file", ex)
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun getFileExtensionFromUri(uri: Uri): String? {
        val fileName = getFileNameFromUri(uri)
        return fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    }

    private fun mapErrorToUserFriendlyMessage(e: Throwable, default: String = "An error occurred."): String {
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
            else -> default
        }
    }

    fun onPlayPauseToggle() {
        val currentState = uiState.value
        val newIsPlaying = !currentState.isPlaying

        if (!currentState.isPlaybackReady) {
            Log.w("DetailsViewModel", "Play/Pause toggle attempted but playback not ready. State: $currentState")
            val errorMsg = "Cannot play: Recording source not loaded or processing."
            _uiState.update { it.copy(error = errorMsg) }
            viewModelScope.launch { _errorEvent.trySend(errorMsg)}
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
            viewModelScope.launch { _infoMessageEvent.trySend("Summary & Notes copied!") }
            _uiState.update { it.copy(textToCopy = combinedText) }
        } else {
            Log.w("DetailsViewModel", "Copy attempt failed: Summary not ready or empty.")
            val errorMsg = "Summary and notes are not available to copy."
            _uiState.update { it.copy(error = errorMsg) }
            viewModelScope.launch { _errorEvent.trySend(errorMsg) }
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
            viewModelScope.launch { _infoMessageEvent.trySend(infoMsg) }
        } else {
            val infoMsg = "PowerPoint selection cancelled."
            _uiState.update { it.copy(infoMessage = infoMsg) }
            viewModelScope.launch { _infoMessageEvent.trySend(infoMsg) }
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
        viewModelScope.launch { _infoMessageEvent.trySend(infoMsg) }
    }

    fun onWatchYouTubeVideo(video: RecommendationDto) {
        val videoId = video.videoId
        if (videoId.isNullOrBlank()) {
            val error = application.getString(R.string.error_youtube_link_missing)
            _uiState.update { it.copy(error = error) }
            viewModelScope.launch { _errorEvent.trySend(error) }
            return
        }
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
        viewModelScope.launch {
            Log.d("RecordingDetailsViewModel", "Opening YouTube video URL: $youtubeUrl")
            _openUrlEvent.emit(youtubeUrl)
        }
    }

    fun onOpenUrl(url: String) {
        if (url.isBlank()) {
            val error = "URL is empty or invalid."
            _uiState.update { it.copy(error = error) }
            viewModelScope.launch { _errorEvent.trySend(error) }
            return
        }
        viewModelScope.launch {
            Log.d("RecordingDetailsViewModel", "Opening URL: $url")
            _openUrlEvent.emit(url)
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
                    val idToDelete = uiState.value.cloudId
                    if (idToDelete == null) {
                        Log.e("DetailsViewModel", "Cannot delete cloud recording: Primary Cloud ID is missing.")
                        errorMsg = "Cannot delete: Recording ID unknown."
                    } else {
                        Log.d("DetailsViewModel", "Attempting to delete cloud recording with Primary ID: $idToDelete")
                        remoteAudioRepository.deleteCloudRecording(idToDelete)
                            .collect { result ->
                                result.onSuccess {
                                    Log.i("DetailsViewModel", "Successfully deleted cloud recording metadata with ID: $idToDelete")
                                    deleteSuccess = true
                                    infoMsg = application.getString(R.string.info_cloud_recording_deleted)
                                }.onFailure { e ->
                                    Log.e("DetailsViewModel", "Failed to delete cloud recording metadata (ID: $idToDelete)", e)
                                    errorMsg = mapErrorToUserFriendlyMessage(e)
                                    deleteSuccess = false
                                }
                            }
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
                            infoMsg = application.getString(R.string.info_local_recording_deleted)
                        } else {
                            Log.e("DetailsViewModel", "Error deleting local file: ${metaToDelete.filePath}")
                            errorMsg = application.getString(R.string.error_delete_failed_local)
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
                    cloudId = if (it.isCloudSource && deleteSuccess) null else it.cloudId,
                    remoteRecordingId = if (it.isCloudSource && deleteSuccess) null else it.remoteRecordingId,
                    title = application.getString(R.string.details_title_deleted),
                    editableTitle = application.getString(R.string.details_title_deleted),
                    storageUrl = null,
                    audioUrl = null,
                    generatedPdfUrl = null,
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
                _navigationEvent.trySend(NavigationEvent.NavigateToLibrary)
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
            viewModelScope.launch { _errorEvent.trySend(errorMsg)}
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
                    val primaryCloudId = state.cloudId
                    if (primaryCloudId == null) {
                        Log.e("DetailsViewModel", "Cannot update cloud title: Primary Cloud ID is null.")
                        errorMsg = "Cannot update title: Recording ID unknown."
                    } else {
                        Log.d("DetailsViewModel", "Attempting to update cloud title for Primary ID: $primaryCloudId to '$newTitle'")
                        Log.w("DetailsViewModel", "Cloud title update endpoint call is commented out.")
                        success = false
                        if (!success) errorMsg = "Failed to update cloud title (Not implemented)."
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
                viewModelScope.launch { _errorEvent.trySend(errorMsg ?: application.getString(R.string.error_metadata_update_failed)) }
            } else {
                Log.d("DetailsViewModel", "Title updated successfully.")
                val infoMsg = "Title updated."
                _uiState.update { it.copy(isLoading = false, infoMessage = infoMsg) }
                viewModelScope.launch { _infoMessageEvent.trySend(infoMsg) }
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