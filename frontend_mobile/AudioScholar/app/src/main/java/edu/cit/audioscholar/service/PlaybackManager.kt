package edu.cit.audioscholar.service

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
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
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

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
            val currentDuration = if (isReady) player?.duration?.takeIf { it > 0 } ?: 0L else 0L
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
        Log.d(TAG, "play called. Player exists: ${player != null}, Is ready: ${playbackState.value.isReady}")
        if (player?.playbackState == Player.STATE_ENDED) {
            player?.seekTo(0)
            player?.playWhenReady = true
        } else {
            player?.play()
        }
    }

    fun pause() {
        Log.d(TAG, "pause called. Player exists: ${player != null}")
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo called: $positionMs ms. Player exists: ${player != null}")
        player?.let {
            val duration = it.duration.takeIf { d -> d > 0 } ?: 0L
            val clampedPosition = positionMs.coerceIn(0, duration)
            it.seekTo(clampedPosition)
            _playbackState.update { state -> state.copy(currentPositionMs = clampedPosition) }
        }
    }

    fun releasePlayer() {
        Log.d(TAG, "releasePlayer called.")
        stopProgressUpdates()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        _playbackState.value = PlaybackState()
    }

    fun consumeError() {
        if (_playbackState.value.error != null) {
            Log.d(TAG, "Consuming error state.")
            _playbackState.update { it.copy(error = null) }
        }
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        stopProgressUpdates()

        progressJob = coroutineScope.launch {
            Log.d(TAG, "Progress updates coroutine started.")
            while (isActive) {
                player?.let { currentPlayer ->
                    if (currentPlayer.isPlaying) {
                        val currentPosition = currentPlayer.currentPosition
                        if (currentPosition != _playbackState.value.currentPositionMs) {
                            _playbackState.update { state ->
                                state.copy(currentPositionMs = currentPosition)
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
        if (progressJob?.isActive == true) {
            progressJob?.cancel()
            Log.d(TAG, "Progress updates job cancelled.")
        }
        progressJob = null
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
}