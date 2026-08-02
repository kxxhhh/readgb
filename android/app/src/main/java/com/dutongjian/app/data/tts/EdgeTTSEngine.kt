package com.dutongjian.app.data.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
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
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
    private val requestId = UUID.randomUUID().toString().replace("-", "")

    override fun speak(text: String, onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        stop()
        callbacks = Callbacks(onProgress, onComplete, onError)
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$connectionId"
        socket = client.newWebSocket(Request.Builder().url(url).build(), EdgeListener(text))
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

    private inner class EdgeListener(private val text: String) : WebSocketListener() {
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
            mainHandler.post { callbacks?.onError?.invoke(IllegalStateException("Edge-TTS 连接失败：${t.message ?: "服务不可用"}", t)) }
        }

        private fun finishAudio() {
            val bytes = audio.toByteArray()
            if (bytes.isEmpty()) {
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
                    current.onComplete()
                    stop()
                }
                media.setOnErrorListener { _, what, extra ->
                    current.onError(IllegalStateException("Edge-TTS 音频解码失败：$what/$extra"))
                    stop()
                    true
                }
                media.prepareAsync()
            }
        }

        private fun sendConfig(webSocket: WebSocket) {
            val body = """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
            webSocket.send(frame("speech.config", body, "application/json; charset=utf-8"))
        }

        private fun sendSsml(webSocket: WebSocket, value: String) {
            val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            val body = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"zh-CN\"><voice name=\"zh-CN-YunxiNeural\"><prosody rate=\"0%\" pitch=\"0%\">$escaped</prosody></voice></speak>"
            webSocket.send(frame("ssml", body, "application/ssml+xml"))
        }

        private fun frame(path: String, body: String, contentType: String): String {
            val timestamp = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now())
            return "X-RequestId: $requestId\r\nContent-Type: $contentType\r\nPath: $path\r\nX-Timestamp: $timestamp\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body"
        }
    }

    private data class Callbacks(
        val onProgress: (Int) -> Unit,
        val onComplete: () -> Unit,
        val onError: (Throwable) -> Unit,
    )

    private companion object {
        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    }
}
