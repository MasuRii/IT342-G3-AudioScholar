package edu.cit.audioscholar.service

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.compose.runtime.Stable
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Stable
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isReady: Boolean = false,
    val error: String? = null
)

private const val TAG = "PlaybackManager"
private const val PROGRESS_UPDATE_INTERVAL_MS = 100L

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + scopeJob)
    private var positionUpdateJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
                _playbackState.update { it.copy(currentPositionMs = player?.currentPosition ?: it.currentPositionMs) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "onPlaybackStateChanged: ${playbackStateToString(playbackState)}")
            val isReady = playbackState == Player.STATE_READY
            val currentDuration = player?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            Log.d(TAG, "Playback state changed to ${playbackStateToString(playbackState)}, isReady=$isReady, duration=$currentDuration")
            _playbackState.update {
                it.copy(
                    isReady = isReady,
                    totalDurationMs = if (currentDuration > 0) currentDuration else it.totalDurationMs
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                Log.d(TAG, "Playback ended.")
                _playbackState.update { it.copy(isPlaying = false, currentPositionMs = it.totalDurationMs) }
                stopProgressUpdates()
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            val duration = player?.duration?.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            Log.d(TAG, "onTimelineChanged (reason: $reason), duration=$duration")
            if (duration > 0 && duration != _playbackState.value.totalDurationMs) {
                Log.d(TAG, "Timeline changed, updating duration from ${_playbackState.value.totalDurationMs} to $duration")
                _playbackState.update { it.copy(totalDurationMs = duration) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player Error: ${error.errorCodeName} - ${error.message}", error)
            _playbackState.update { it.copy(error = "Playback Error: ${error.message}", isPlaying = false) }
            stopProgressUpdates()
        }
    }

    fun preparePlayer(filePath: String) {
        Log.d(TAG, "preparePlayer called for: $filePath")
        if (player != null && player?.currentMediaItem?.mediaId == filePath && playbackState.value.isReady) {
            Log.d(TAG, "Player already prepared for this file.")
            return
        }

        releasePlayer()

        try {
            player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(filePath.toUri())
                    .setMediaId(filePath)
                    .build()
                setMediaItem(mediaItem)
                addListener(playerListener)
                playWhenReady = false
                prepare()
            }
            _playbackState.value = PlaybackState()
            Log.d(TAG, "Player prepared for $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing player for $filePath", e)
            _playbackState.update { it.copy(error = "Failed to prepare player: ${e.message}") }
            releasePlayer()
        }
    }

    fun prepareAndPlay(filePath: String) {
        Log.d(TAG, "prepareAndPlay called for: $filePath")
        releasePlayer()

        try {
            player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(filePath.toUri())
                    .setMediaId(filePath)
                    .build()
                setMediaItem(mediaItem)
                addListener(playerListener)
                playWhenReady = true
                prepare()
            }
            _playbackState.value = PlaybackState()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player for $filePath", e)
            _playbackState.update { it.copy(error = "Failed to initialize player: ${e.message}") }
            releasePlayer()
        }
    }

    fun play() {
        if (player != null) {
            player?.play()
            Log.d(TAG, "play() called on existing player instance.")
        } else {
            Log.w(TAG, "play() called but player instance is null.")
            _playbackState.update { it.copy(error = "Player not ready.") }
        }
    }

    fun pause() {
        if (player != null) {
            player?.pause()
            Log.d(TAG, "pause() called on existing player instance.")
        } else {
            Log.w(TAG, "pause() called but player instance is null.")
        }
    }

    fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo called: $positionMs ms. Player exists: ${player != null}")
        player?.let {
            val requestedPosition = positionMs.coerceAtLeast(0)
            Log.d(TAG, "Requesting seek to $requestedPosition ms (original request: $positionMs)")
            it.seekTo(requestedPosition)
            _playbackState.update { state -> state.copy(currentPositionMs = requestedPosition) }
        }
    }

    fun releasePlayer() {
        releasePlayerInternal()
    }

    fun consumeError() {
        if (_playbackState.value.error != null) {
            Log.d(TAG, "Consuming error state.")
            _playbackState.update { it.copy(error = null) }
        }
    }

    private fun startProgressUpdates() {
        if (positionUpdateJob?.isActive == true) return
        stopProgressUpdates()

        positionUpdateJob = scope.launch {
            Log.d(TAG, "Progress updates coroutine started.")
            while (isActive) {
                player?.let { currentPlayer ->
                    if (currentPlayer.isPlaying) {
                        val currentPosition = currentPlayer.currentPosition
                        var durationChanged = false
                        var newDuration = _playbackState.value.totalDurationMs

                        if (_playbackState.value.totalDurationMs <= 0L) {
                            val potentialDuration = currentPlayer.duration.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
                            if (potentialDuration > 0L) {
                                Log.d(TAG, "Found valid duration ($potentialDuration) during progress update.")
                                newDuration = potentialDuration
                                durationChanged = true
                            }
                        }

                        if (currentPosition != _playbackState.value.currentPositionMs || durationChanged) {
                            _playbackState.update { state ->
                                state.copy(
                                    currentPositionMs = currentPosition,
                                    totalDurationMs = newDuration
                                )
                            }
                        }
                    } else {
                        Log.d(TAG, "Player not playing, stopping progress updates from within loop.")
                        stopProgressUpdates()
                    }
                } ?: run {
                    Log.d(TAG, "Player is null, stopping progress updates from within loop.")
                    stopProgressUpdates()
                }
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
            Log.d(TAG, "Progress updates coroutine finished.")
        }
        Log.d(TAG, "Progress updates job launched.")
    }

    private fun stopProgressUpdates() {
        if (positionUpdateJob?.isActive == true) {
            positionUpdateJob?.cancel()
            Log.d(TAG, "Progress updates job cancelled.")
        }
        positionUpdateJob = null
    }

    private fun playbackStateToString(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN ($state)"
        }
    }

    fun preparePlayerForStreaming(url: String) {
        releasePlayerInternal()
        Log.d(TAG, "Creating new ExoPlayer instance for streaming URL: $url")
        try {
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                addListener(playerListener)
                prepare()
                playWhenReady = false
                Log.d(TAG, "ExoPlayer instance created and prepare() called for streaming.")
            }
            _playbackState.update { it.copy(isPlaying = false, isReady = false, currentPositionMs = 0L, totalDurationMs = 0L, error = null) }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ExoPlayer for streaming", e)
            _playbackState.update { it.copy(error = "Failed to initialize player.") }
            player = null
        }
    }

    fun cleanup() {
        releasePlayer()
        scopeJob.cancel()
        Log.d(TAG, "PlaybackManager cleaned up and scope cancelled.")
    }

    private fun releasePlayerInternal() {
        stopProgressUpdates()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        _playbackState.update { PlaybackState() }
        Log.d(TAG, "Player released.")
    }
}