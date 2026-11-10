package com.auralis.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exposes the ExoPlayer audio session to the in-app visualizer only.
 * This does not capture audio from other apps or the microphone.
 */
object AudioSessionBridge {
    private val mutableSessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = mutableSessionId.asStateFlow()

    fun publish(id: Int) {
        mutableSessionId.value = id.coerceAtLeast(0)
    }
}
