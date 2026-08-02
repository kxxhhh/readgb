package com.dutongjian.app.data.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.TTSEngine
import java.util.Locale
import java.util.UUID

class LocalTtsEngine(context: Context) : TTSEngine {
    override val type = TtsEngineType.LOCAL

    private val mainHandler = Handler(Looper.getMainLooper())
    private val applicationContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var initialized = false
    private var pending: Request? = null
    private var active: Request? = null
    private var activeUtteranceId: String? = null
    private var generation = 0L
    private var paused = false

    init {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            mainHandler.post {
                if (status != TextToSpeech.SUCCESS) {
                    initialized = false
                    val request = pending ?: active
                    pending = null
                    active = null
                    request?.onError?.invoke(IllegalStateException("Android 本地 TTS 初始化失败：$status"))
                    return@post
                }
                val speaker = textToSpeech ?: return@post
                val languageStatus = speaker.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (languageStatus < TextToSpeech.LANG_AVAILABLE) {
                    initialized = false
                    val request = pending ?: active
                    pending = null
                    active = null
                    request?.onError?.invoke(IllegalStateException("Android 本地 TTS 不支持中文语音数据"))
                    return@post
                }
                speaker.setOnUtteranceProgressListener(listener)
                initialized = true
                pending?.let { request ->
                    pending = null
                    speakNow(request)
                }
            }
        }
    }

    override fun speak(text: String, onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        stop()
        val request = Request(text, onProgress, onComplete, onError)
        active = request
        paused = false
        if (initialized) speakNow(request) else pending = request
    }

    override fun pause() {
        if (active == null || paused || !initialized) return
        activeUtteranceId = null
        textToSpeech?.stop()
        paused = true
    }

    override fun resume() {
        val request = active ?: return
        if (!paused) return
        paused = false
        if (initialized) speakNow(request) else pending = request
    }

    override fun stop() {
        generation += 1
        activeUtteranceId = null
        pending = null
        active = null
        paused = false
        if (initialized) textToSpeech?.stop()
    }

    override fun release() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        initialized = false
    }

    private fun speakNow(request: Request) {
        val speaker = textToSpeech ?: return
        val token = ++generation
        val utteranceId = "local-$token-${UUID.randomUUID()}"
        activeUtteranceId = utteranceId
        val result = speaker.speak(
            request.text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId,
        )
        if (result == TextToSpeech.ERROR && activeUtteranceId == utteranceId) {
            activeUtteranceId = null
            active = null
            request.onError(IllegalStateException("Android 本地 TTS 播放失败"))
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            if (activeUtteranceId == utteranceId) mainHandler.post { active?.onProgress?.invoke(0) }
        }

        override fun onDone(utteranceId: String) {
            if (activeUtteranceId != utteranceId) return
            val request = active ?: return
            activeUtteranceId = null
            active = null
            mainHandler.post {
                request.onProgress(100)
                request.onComplete()
            }
        }

        @Deprecated("Deprecated in Android API")
        override fun onError(utteranceId: String) {
            if (activeUtteranceId != utteranceId) return
            val request = active ?: return
            activeUtteranceId = null
            active = null
            mainHandler.post { request.onError(IllegalStateException("Android 本地 TTS 朗读失败")) }
        }
    }

    private data class Request(
        val text: String,
        val onProgress: (Int) -> Unit,
        val onComplete: () -> Unit,
        val onError: (Throwable) -> Unit,
    )
}
