package com.dutongjian.app.domain.tts

import com.dutongjian.app.domain.model.TtsEngineType

data class AudioDiagnosticSnapshot(
    val event: String? = null,
    val errorCode: String? = null,
    val reason: String? = null,
    val audioTrackState: Int = -1,
    val audioTrackPlayState: Int = -1,
    val writtenBytes: Long = 0L,
    val consecutiveZeroWrites: Int = 0,
    val pcmFilePath: String? = null,
    val pcmBytes: Long = 0L,
    val pcmNonZero: Boolean = false,
)

interface TTSEngine {
    val type: TtsEngineType
    val audioDiagnostics: AudioDiagnosticSnapshot
        get() = AudioDiagnosticSnapshot()

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
