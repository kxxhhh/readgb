package com.dutongjian.app.data.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.TTSEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class EdgeTTSEngine(context: Context) : TTSEngine {
    override val type = TtsEngineType.EDGE
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var player: MediaPlayer? = null
    private var audioFile: File? = null
    private var callbacks: Callbacks? = null

    override fun speak(text: String, onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        stop()
        callbacks = Callbacks(onProgress, onComplete, onError)
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$connectionId&Sec-MS-GEC=${generateSecMsGec()}&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"
        socket = client.newWebSocket(
            Request.Builder()
                .url(url)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", EDGE_ORIGIN)
                .header("Sec-WebSocket-Version", "13")
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("User-Agent", EDGE_USER_AGENT)
                .header("Cookie", "muid=${generateMuid()}")
                .build(),
            EdgeListener(text, connectionId),
        )
    }

    override fun pause() = player?.pause() ?: Unit

    override fun resume() = player?.start() ?: Unit

    override fun stop() {
        socket?.cancel()
        socket = null
        player?.let { media -> runCatching { media.stop() }; media.release() }
        player = null
        audioFile?.delete()
        audioFile = null
        callbacks = null
    }

    override fun release() {
        stop()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private inner class EdgeListener(private val text: String, private val requestId: String) : WebSocketListener() {
        private val audio = java.io.ByteArrayOutputStream()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            sendConfig(webSocket)
            sendSsml(webSocket, text)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.contains("Path:turn.end", ignoreCase = true)) finishAudio()
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val data = bytes.toByteArray()
            if (data.size < 2) return
            val headerLength = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
            if (headerLength + 2 > data.size) return
            val header = String(data, 2, headerLength, StandardCharsets.UTF_8)
            if (header.contains("Path:audio", ignoreCase = true)) {
                audio.write(data, headerLength + 2, data.size - headerLength - 2)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val status = response?.code ?: -1
            val code = if (status > 0) "EDGE_HTTP_$status" else "EDGE_CONNECTION_FAILED"
            val reason = if (status == 403) {
                "Edge-TTS WebSocket 握手被服务拒绝（HTTP 403）；请切换 Android 本地 TTS。"
            } else {
                "Edge-TTS 连接失败：${t.message ?: "服务不可用"}"
            }
            logAudioError(code, reason, status)
            mainHandler.post { callbacks?.onError?.invoke(IllegalStateException(reason, t)) }
        }

        private fun finishAudio() {
            val bytes = audio.toByteArray()
            if (bytes.isEmpty()) {
                logAudioError("EDGE_EMPTY_AUDIO", "Edge-TTS 未返回音频", -1)
                mainHandler.post { callbacks?.onError?.invoke(IllegalStateException("Edge-TTS 未返回音频")) }
                return
            }
            val file = File.createTempFile("edge-tts-", ".mp3", context.cacheDir)
            file.writeBytes(bytes)
            audioFile = file
            mainHandler.post {
                val current = callbacks ?: return@post
                val media = MediaPlayer()
                player = media
                media.setDataSource(file.absolutePath)
                media.setOnPreparedListener {
                    current.onProgress(0)
                    it.start()
                }
                media.setOnCompletionListener {
                    current.onProgress(100)
                    stop()
                    current.onComplete()
                }
                media.setOnErrorListener { _, what, extra ->
                    logAudioError("EDGE_DECODE_FAILED", "Edge-TTS 音频解码失败：$what/$extra", -1)
                    current.onError(IllegalStateException("Edge-TTS 音频解码失败：$what/$extra"))
                    stop()
                    true
                }
                media.prepareAsync()
            }
        }

        private fun sendConfig(webSocket: WebSocket) {
            val body = """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""" + "\r\n"
            webSocket.send("X-Timestamp:${edgeTimestamp()}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n$body")
        }

        private fun sendSsml(webSocket: WebSocket, value: String) {
            val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            val body = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"zh-CN\"><voice name=\"zh-CN-YunxiNeural\"><prosody rate=\"+0%\" pitch=\"+0Hz\" volume=\"+0%\"><s>$escaped</s></prosody></voice></speak>"
            webSocket.send("X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${edgeTimestamp()}Z\r\nPath:ssml\r\n\r\n$body")
        }
    }

    private fun logAudioError(code: String, reason: String, httpStatus: Int) {
        val escapedReason = reason.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        Log.e(
            AUDIO_DIAGNOSTIC_TAG,
            """{"event":"AUDIO_ERROR","code":"$code","reason":"$escapedReason","audioTrackState":-1,"audioTrackPlayState":-1,"writtenBytes":0,"consecutiveZeroWrites":0,"pcmBytes":0,"pcmNonZero":false,"httpStatus":$httpStatus}""",
        )
    }

    private fun generateSecMsGec(): String {
        val windowsEpochSeconds = 11_644_473_600L
        val roundedSeconds = (Instant.now().epochSecond + windowsEpochSeconds).let { it - it % 300L }
        val input = "${roundedSeconds * 10_000_000L}$TRUSTED_CLIENT_TOKEN"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.US_ASCII))
            .joinToString("") { "%02X".format(Locale.US, it) }
    }

    private fun generateMuid(): String = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)

    private fun edgeTimestamp(): String = EDGE_TIMESTAMP_FORMAT.format(Instant.now().atZone(ZoneOffset.UTC))

    private data class Callbacks(
        val onProgress: (Int) -> Unit,
        val onComplete: () -> Unit,
        val onError: (Throwable) -> Unit,
    )

    private companion object {
        const val AUDIO_DIAGNOSTIC_TAG = "AUDIO_DIAGNOSTIC"
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        const val EDGE_ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        const val EDGE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
        val EDGE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'")
            .toFormatter(Locale.US)
    }
}
