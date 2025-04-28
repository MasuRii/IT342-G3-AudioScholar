package edu.cit.audioscholar.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TokenEventBus {

    private val _tokenFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val tokenFlow: Flow<String> = _tokenFlow.asSharedFlow()

    fun postNewToken(token: String) {
        val emitted = _tokenFlow.tryEmit(token)
        if (!emitted) {
            println("Error: TokenEventBus failed to emit new FCM token.")
        }
    }
} 