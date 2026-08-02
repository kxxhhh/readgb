package com.dutongjian.app.data.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dutongjian.app.domain.tts.TTSEngine
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TTSEngineSelfTest {
    private lateinit var context: Context
    private lateinit var ttsEngine: TTSEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "debug_tts_output.pcm").delete()
        ttsEngine = SherpaOnnxEngine(context)
    }

    @After
    fun tearDown() {
        ttsEngine.release()
    }

    @Test
    fun testTTSAudioOutputDataNotZero() {
        val finished = CountDownLatch(1)
        var failure: Throwable? = null

        ttsEngine.speak(
            text = "测试文本",
            onComplete = { finished.countDown() },
            onError = {
                failure = it
                finished.countDown()
            },
        )

        assertTrue("TTS did not complete within 45 seconds", finished.await(45, TimeUnit.SECONDS))
        assertNull("TTS reported an audio failure", failure)
        val diagnostics = ttsEngine.audioDiagnostics
        assertTrue("AudioTrack wrote no PCM frames: $diagnostics", diagnostics.writtenBytes > 0L)

        val pcmFile = File(context.cacheDir, "debug_tts_output.pcm")
        assertTrue("PCM diagnostic file was not generated", awaitFile(pcmFile))
        assertTrue("PCM diagnostic file is empty", pcmFile.length() > 0L)
        val bytes = pcmFile.readBytes()
        assertTrue("PCM diagnostic file contains only zero bytes", bytes.any { it.toInt() != 0 })
        assertEquals(true, diagnostics.pcmNonZero || bytes.any { it.toInt() != 0 })
        assertNotNull("Audio diagnostics did not record an event", diagnostics.event)
    }

    private fun awaitFile(file: File): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (file.exists() && file.length() > 0L) return true
            Thread.sleep(50)
        }
        return false
    }
}
