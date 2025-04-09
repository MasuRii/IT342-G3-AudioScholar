package edu.cit.audioscholar.service

import android.content.Context
import android.net.Uri
import android.util.Log
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
import androidx.core.net.toUri

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
                _playbackState.update { it.copy(currentPositionMs = player?.currentPosition ?: 0L) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "onPlaybackStateChanged: ${playbackStateToString(playbackState)}")
            val isReady = playbackState == Player.STATE_READY
            val currentDuration = if (isReady) player?.duration ?: 0L else 0L
            _playbackState.update {
                it.copy(
                    isReady = isReady,
                    totalDurationMs = if (currentDuration > 0) currentDuration else it.totalDurationMs
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                _playbackState.update { it.copy(isPlaying = false, currentPositionMs = it.totalDurationMs) }
                stopProgressUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player Error: ${error.message}", error)
            _playbackState.update { it.copy(error = "Playback Error: ${error.message}", isPlaying = false) }
            stopProgressUpdates()
        }
    }

    fun prepareAndPlay(filePath: String) {
        Log.d(TAG, "prepareAndPlay called for: $filePath")
        releasePlayer()

        try {
            player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(filePath.toUri())
                setMediaItem(mediaItem)
                addListener(playerListener)
                prepare()
                playWhenReady = true
            }
            _playbackState.value = PlaybackState()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player for $filePath", e)
            _playbackState.update { it.copy(error = "Failed to initialize player: ${e.message}") }
            releasePlayer()
        }
    }

    fun play() {
        Log.d(TAG, "play called. Player exists: ${player != null}")
        player?.play()
    }

    fun pause() {
        Log.d(TAG, "pause called. Player exists: ${player != null}")
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo called: $positionMs ms. Player exists: ${player != null}")
        player?.let {
            val clampedPosition = positionMs.coerceIn(0, it.duration)
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
        _playbackState.update { it.copy(error = null) }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = coroutineScope.launch {
            while (isActive) {
                player?.let {
                    if (it.isPlaying) {
                        _playbackState.update { state ->
                            state.copy(currentPositionMs = it.currentPosition)
                        }
                    }
                }
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
        Log.d(TAG, "Progress updates started.")
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
        Log.d(TAG, "Progress updates stopped.")
    }

    private fun playbackStateToString(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN"
        }
    }
}