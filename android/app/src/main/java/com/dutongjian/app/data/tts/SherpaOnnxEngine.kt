package com.dutongjian.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.AudioDiagnosticSnapshot
import com.dutongjian.app.domain.tts.TTSEngine
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class SherpaOnnxEngine(context: Context) : TTSEngine {
    override val type = TtsEngineType.SHERPA_ONNX
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val pcmExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong(0L)
    private val context = context.applicationContext
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var diagnosticSnapshot = AudioDiagnosticSnapshot()

    override val audioDiagnostics: AudioDiagnosticSnapshot
        get() = diagnosticSnapshot

    override fun speak(text: String, onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        stop()
        val token = generation.incrementAndGet()
        executor.execute {
            runCatching {
                val synthesizer = ensureTts()
                val audio = synthesizer.generateWithConfig(text, GenerationConfig(sid = 0, speed = 1.0f))
                dumpPcmAsync(audio.samples)
                if (token != generation.get()) return@runCatching
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(audio.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes((audio.samples.size * 4).coerceAtLeast(AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    throw AudioHardwareException(
                        errorCode = "AUDIO_TRACK_UNINITIALIZED",
                        message = "AudioTrack failed to initialize or start playback.",
                        audioTrackState = track.state,
                        audioTrackPlayState = track.playState,
                    )
                }
                audioTrack = track
                track.setVolume(1.0f)
                try {
                    track.play()
                } catch (error: Throwable) {
                    throw AudioHardwareException(
                        errorCode = "AUDIO_TRACK_PLAY_FAILED",
                        message = "AudioTrack failed to initialize or start playback.",
                        audioTrackState = track.state,
                        audioTrackPlayState = track.playState,
                        cause = error,
                    )
                }
                if (track.state != AudioTrack.STATE_INITIALIZED || track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    throw AudioHardwareException(
                        errorCode = "AUDIO_TRACK_NOT_PLAYING",
                        message = "AudioTrack failed to initialize or start playback.",
                        audioTrackState = track.state,
                        audioTrackPlayState = track.playState,
                    )
                }
                logDiagnostic(
                    event = "AUDIO_TRACK_STARTED",
                    code = "OK",
                    reason = "AudioTrack is initialized and playing.",
                    track = track,
                )
                val chunkSize = 4096
                var offset = 0
                var writtenBytes = 0L
                var consecutiveZeroWrites = 0
                while (offset < audio.samples.size && token == generation.get()) {
                    val count = minOf(chunkSize, audio.samples.size - offset)
                    val written = track.write(audio.samples, offset, count, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        throw AudioHardwareException(
                            errorCode = "AUDIO_WRITE_ERROR",
                            message = "No available audio hardware: AudioTrack.write returned $written.",
                            audioTrackState = track.state,
                            audioTrackPlayState = track.playState,
                            writtenBytes = writtenBytes,
                        )
                    }
                    if (written == 0) {
                        consecutiveZeroWrites += 1
                        logDiagnostic(
                            event = "AUDIO_WRITE_ZERO",
                            code = "AUDIO_BUFFER_BLOCKED",
                            reason = "AudioTrack.write returned 0.",
                            track = track,
                            writtenBytes = writtenBytes,
                            consecutiveZeroWrites = consecutiveZeroWrites,
                        )
                        if (consecutiveZeroWrites >= MAX_CONSECUTIVE_ZERO_WRITES) {
                            throw AudioHardwareException(
                                errorCode = "AUDIO_BUFFER_BLOCKED",
                                message = "Audio buffer blocked or virtual sound card suspended.",
                                audioTrackState = track.state,
                                audioTrackPlayState = track.playState,
                                writtenBytes = writtenBytes,
                            )
                        }
                        Thread.sleep(10)
                        continue
                    }
                    consecutiveZeroWrites = 0
                    writtenBytes += written.toLong() * BYTES_PER_FLOAT
                    updateDiagnostics(
                        event = "AUDIO_DATA_WRITTEN",
                        writtenBytes = writtenBytes,
                        consecutiveZeroWrites = consecutiveZeroWrites,
                        track = track,
                    )
                    offset += written
                    mainHandler.post { onProgress((offset * 100 / audio.samples.size.coerceAtLeast(1)).coerceIn(0, 100)) }
                }
                if (token == generation.get()) {
                    val deadline = System.nanoTime() + (audio.samples.size * 1_000_000_000L / audio.sampleRate + 2_000_000_000L)
                    while (token == generation.get() && track.playbackHeadPosition.toLong() < audio.samples.size && System.nanoTime() < deadline) {
                        Thread.sleep(20)
                    }
                    track.stop()
                    track.release()
                    audioTrack = null
                    logDiagnostic(
                        event = "AUDIO_PLAYBACK_COMPLETE",
                        code = "OK",
                        reason = "PCM data was written and playback drained.",
                        writtenBytes = writtenBytes,
                    )
                    mainHandler.post(onComplete)
                } else {
                    track.release()
                }
            }.onFailure { error ->
                val hardwareError = error as? AudioHardwareException
                if (hardwareError != null) {
                    updateDiagnostics(
                        event = "AUDIO_ERROR",
                        errorCode = hardwareError.errorCode,
                        reason = hardwareError.message,
                        writtenBytes = hardwareError.writtenBytes,
                        trackState = hardwareError.audioTrackState,
                        playState = hardwareError.audioTrackPlayState,
                    )
                    logDiagnostic(
                        event = "AUDIO_ERROR",
                        code = hardwareError.errorCode,
                        reason = hardwareError.message,
                        writtenBytes = hardwareError.writtenBytes,
                        trackState = hardwareError.audioTrackState,
                        playState = hardwareError.audioTrackPlayState,
                    )
                } else {
                    updateDiagnostics(event = "AUDIO_ERROR", errorCode = "TTS_SYNTHESIS_FAILED", reason = error.message.orEmpty())
                    logDiagnostic(event = "AUDIO_ERROR", code = "TTS_SYNTHESIS_FAILED", reason = error.message.orEmpty())
                }
                if (token == generation.get()) mainHandler.post { onError(error) }
            }
        }
    }

    override fun pause() {
        audioTrack?.pause()
        logDiagnostic(event = "AUDIO_PAUSED", code = "OK", reason = "AudioTrack paused.", track = audioTrack)
    }

    override fun resume() {
        audioTrack?.play()
        logDiagnostic(event = "AUDIO_RESUMED", code = "OK", reason = "AudioTrack resumed.", track = audioTrack)
    }

    override fun stop() {
        generation.incrementAndGet()
        audioTrack?.let { track ->
            runCatching { track.stop() }
            logDiagnostic(event = "AUDIO_STOPPED", code = "OK", reason = "AudioTrack stopped.", track = track)
            track.release()
        }
        audioTrack = null
    }

    override fun release() {
        stop()
        tts?.release()
        tts = null
        executor.shutdownNow()
        pcmExecutor.shutdownNow()
    }

    private fun dumpPcmAsync(samples: FloatArray) {
        if (!DEBUG_DUMP_PCM) return
        pcmExecutor.execute {
            val file = File(context.cacheDir, DEBUG_PCM_FILE_NAME)
            runCatching {
                file.outputStream().buffered().use { output ->
                    val chunk = ByteBuffer.allocate(PCM_CHUNK_SAMPLES * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
                    var offset = 0
                    while (offset < samples.size) {
                        chunk.clear()
                        val count = minOf(PCM_CHUNK_SAMPLES, samples.size - offset)
                        repeat(count) { chunk.putFloat(samples[offset + it]) }
                        output.write(chunk.array(), 0, count * BYTES_PER_FLOAT)
                        offset += count
                    }
                }
                val nonZero = file.inputStream().use { input ->
                    var value = input.read()
                    var found = false
                    while (value >= 0) {
                        if (value != 0) {
                            found = true
                            break
                        }
                        value = input.read()
                    }
                    found
                }
                updateDiagnostics(
                    event = "AUDIO_PCM_DUMP",
                    pcmFilePath = file.absolutePath,
                    pcmBytes = file.length(),
                    pcmNonZero = nonZero,
                )
                logDiagnostic(
                    event = "AUDIO_PCM_DUMP",
                    code = if (nonZero) "TTS_SYNTHESIS_OK" else "TTS_SYNTHESIS_ZERO",
                    reason = if (nonZero) "PCM cache contains non-zero data." else "PCM cache contains only zero bytes.",
                    pcmBytes = file.length(),
                    pcmNonZero = nonZero,
                )
            }.onFailure { error ->
                updateDiagnostics(event = "AUDIO_ERROR", errorCode = "PCM_DUMP_FAILED", reason = error.message.orEmpty())
                logDiagnostic(event = "AUDIO_ERROR", code = "PCM_DUMP_FAILED", reason = error.message.orEmpty())
            }
        }
    }

    private fun updateDiagnostics(
        event: String,
        errorCode: String? = diagnosticSnapshot.errorCode,
        reason: String? = diagnosticSnapshot.reason,
        writtenBytes: Long = diagnosticSnapshot.writtenBytes,
        consecutiveZeroWrites: Int = diagnosticSnapshot.consecutiveZeroWrites,
        track: AudioTrack? = null,
        trackState: Int = track?.state ?: diagnosticSnapshot.audioTrackState,
        playState: Int = track?.playState ?: diagnosticSnapshot.audioTrackPlayState,
        pcmFilePath: String? = diagnosticSnapshot.pcmFilePath,
        pcmBytes: Long = diagnosticSnapshot.pcmBytes,
        pcmNonZero: Boolean = diagnosticSnapshot.pcmNonZero,
    ) {
        diagnosticSnapshot = AudioDiagnosticSnapshot(
            event = event,
            errorCode = errorCode,
            reason = reason,
            audioTrackState = trackState,
            audioTrackPlayState = playState,
            writtenBytes = writtenBytes,
            consecutiveZeroWrites = consecutiveZeroWrites,
            pcmFilePath = pcmFilePath,
            pcmBytes = pcmBytes,
            pcmNonZero = pcmNonZero,
        )
    }

    private fun logDiagnostic(
        event: String,
        code: String,
        reason: String,
        track: AudioTrack? = null,
        trackState: Int = track?.state ?: diagnosticSnapshot.audioTrackState,
        playState: Int = track?.playState ?: diagnosticSnapshot.audioTrackPlayState,
        writtenBytes: Long = diagnosticSnapshot.writtenBytes,
        consecutiveZeroWrites: Int = diagnosticSnapshot.consecutiveZeroWrites,
        pcmBytes: Long = diagnosticSnapshot.pcmBytes,
        pcmNonZero: Boolean = diagnosticSnapshot.pcmNonZero,
    ) {
        val escapedReason = reason.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val json = """{"event":"$event","code":"$code","reason":"$escapedReason","audioTrackState":$trackState,"audioTrackPlayState":$playState,"writtenBytes":$writtenBytes,"consecutiveZeroWrites":$consecutiveZeroWrites,"pcmBytes":$pcmBytes,"pcmNonZero":$pcmNonZero}"""
        if (event == "AUDIO_ERROR") Log.e(AUDIO_DIAGNOSTIC_TAG, json) else Log.i(AUDIO_DIAGNOSTIC_TAG, json)
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
        private const val AUDIO_DIAGNOSTIC_TAG = "AUDIO_DIAGNOSTIC"
        private const val DEBUG_DUMP_PCM = true
        private const val DEBUG_PCM_FILE_NAME = "debug_tts_output.pcm"
        private const val BYTES_PER_FLOAT = 4
        private const val PCM_CHUNK_SAMPLES = 1024
        private const val MAX_CONSECUTIVE_ZERO_WRITES = 3
        const val MODEL_DIR = "sherpa-onnx-tts/vits-icefall-zh-aishell3"
    }
}
