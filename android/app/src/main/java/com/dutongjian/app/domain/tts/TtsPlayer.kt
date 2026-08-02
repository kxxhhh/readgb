package com.dutongjian.app.domain.tts

import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.TtsEngineType
import kotlinx.coroutines.flow.StateFlow

data class TtsPlaybackState(
    val engine: TtsEngineType = TtsEngineType.LOCAL,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentItemId: String? = null,
    val currentSentence: Int = 0,
    val sentenceCount: Int = 0,
    val progress: Int = 0,
    val error: String? = null,
)

interface TtsPlayer {
    val state: StateFlow<TtsPlaybackState>
    fun selectEngine(type: TtsEngineType)
    fun speak(items: List<ReadingItem>, item: ReadingItem)
    fun pause()
    fun resume()
    fun stop()
    fun release()
}
