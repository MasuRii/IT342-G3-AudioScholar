package edu.cit.audioscholar.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessingEventBus @Inject constructor() {

    private val _processingCompleteEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)

    val processingCompleteEvent = _processingCompleteEvent.asSharedFlow()

    suspend fun signalProcessingComplete(recordingId: String): Boolean {
        _processingCompleteEvent.emit(recordingId)
        return true
    }
} 