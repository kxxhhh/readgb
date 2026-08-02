package com.dutongjian.app.data.tts

class AudioHardwareException(
    val errorCode: String,
    override val message: String,
    val audioTrackState: Int = -1,
    val audioTrackPlayState: Int = -1,
    val writtenBytes: Long = 0L,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
