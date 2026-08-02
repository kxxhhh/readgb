package com.dutongjian.app.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.TTSEngine
import java.util.Locale
import java.util.UUID

internal class AndroidTextToSpeechEngine(
    context: Context,
    override val type: TtsEngineType,
) : TTSEngine {
    private var ready = false
    private var pending: PendingSpeech? = null
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.SIMPLIFIED_CHINESE
                pending?.let { speech ->
                    pending = null
                    speak(speech.text, speech.onProgress, speech.onComplete, speech.onError)
                }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = pending?.onProgress?.invoke(0) ?: Unit

            override fun onDone(utteranceId: String) {
                val speech = pending
                pending = null
                speech?.onProgress?.invoke(100)
                speech?.onComplete?.invoke()
            }

            override fun onError(utteranceId: String) {
                val speech = pending
                pending = null
                speech?.onError?.invoke(IllegalStateException("系统语音引擎播放失败"))
            }
        })
    }

    override fun speak(text: String, onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        if (!ready) {
            pending = PendingSpeech(text, onProgress, onComplete, onError)
            return
        }
        pending = PendingSpeech(text, onProgress, onComplete, onError)
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        if (result == TextToSpeech.ERROR) {
            val speech = pending
            pending = null
            speech?.onError?.invoke(IllegalStateException("系统语音引擎不可用"))
        }
    }

    override fun pause() {
        // Android TextToSpeech has no portable pause API. The sentence queue is stopped
        // here and resumed by the controller from the current sentence.
        tts.stop()
    }

    override fun resume() = Unit

    override fun stop() {
        pending = null
        tts.stop()
    }

    override fun release() {
        pending = null
        tts.stop()
        tts.shutdown()
    }

    private data class PendingSpeech(
        val text: String,
        val onProgress: (Int) -> Unit,
        val onComplete: () -> Unit,
        val onError: (Throwable) -> Unit,
    )
}
