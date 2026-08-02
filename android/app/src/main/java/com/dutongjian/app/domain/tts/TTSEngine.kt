package com.dutongjian.app.domain.tts

import com.dutongjian.app.domain.model.TtsEngineType

interface TTSEngine {
    val type: TtsEngineType

    fun speak(
        text: String,
        onProgress: (Int) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    )

    fun pause()
    fun resume()
    fun stop()
    fun release()
}
