package com.dutongjian.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.TTSEngine
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class SherpaOnnxEngine(context: Context) : TTSEngine {
    override val type = TtsEngineType.SHERPA_ONNX
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong(0L)
    private val context = context.applicationContext
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null

    override fun speak(text: String, onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        stop()
        val token = generation.incrementAndGet()
        executor.execute {
            runCatching {
                val synthesizer = ensureTts()
                val audio = synthesizer.generateWithConfig(text, GenerationConfig(sid = 0, speed = 1.0f))
                if (token != generation.get()) return@runCatching
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(audio.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes((audio.samples.size * 4).coerceAtLeast(AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack = track
                track.play()
                val chunkSize = 4096
                var offset = 0
                while (offset < audio.samples.size && token == generation.get()) {
                    val count = minOf(chunkSize, audio.samples.size - offset)
                    track.write(audio.samples, offset, count, AudioTrack.WRITE_BLOCKING)
                    offset += count
                    mainHandler.post { onProgress((offset * 100 / audio.samples.size.coerceAtLeast(1)).coerceIn(0, 100)) }
                }
                if (token == generation.get()) {
                    track.stop()
                    track.release()
                    audioTrack = null
                    mainHandler.post(onComplete)
                } else {
                    track.release()
                }
            }.onFailure { error ->
                if (token == generation.get()) mainHandler.post { onError(error) }
            }
        }
    }

    override fun pause() {
        audioTrack?.pause()
    }

    override fun resume() {
        audioTrack?.play()
    }

    override fun stop() {
        generation.incrementAndGet()
        audioTrack?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        audioTrack = null
    }

    override fun release() {
        stop()
        tts?.release()
        tts = null
        executor.shutdownNow()
    }

    private fun ensureTts(): OfflineTts {
        tts?.let { return it }
        return synchronized(this) {
            tts ?: OfflineTts(
                assetManager = context.assets,
                config = getOfflineTtsConfig(
                    modelDir = MODEL_DIR,
                    modelName = "model.onnx",
                    acousticModelName = "",
                    vocoder = "",
                    voices = "",
                    lexicon = "lexicon.txt",
                    dataDir = "",
                    dictDir = "",
                    ruleFsts = "$MODEL_DIR/date.fst,$MODEL_DIR/number.fst,$MODEL_DIR/phone.fst",
                    ruleFars = "$MODEL_DIR/rule.far",
                ),
            ).also { tts = it }
        }
    }

    private companion object {
        const val MODEL_DIR = "sherpa-onnx-tts/vits-icefall-zh-aishell3"
    }
}
