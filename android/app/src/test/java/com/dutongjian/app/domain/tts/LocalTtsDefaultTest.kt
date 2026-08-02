package com.dutongjian.app.domain.tts

import com.dutongjian.app.domain.model.TtsEngineType
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTtsDefaultTest {
    @Test
    fun playbackDefaultsToAndroidLocalTts() {
        assertEquals(TtsEngineType.LOCAL, TtsPlaybackState().engine)
    }
}
