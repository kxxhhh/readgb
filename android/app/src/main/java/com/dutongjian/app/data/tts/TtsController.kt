package com.dutongjian.app.data.tts

import android.content.Context
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.ClassicalTextPreprocessor
import com.dutongjian.app.domain.tts.TTSEngine
import com.dutongjian.app.domain.tts.TtsPlaybackState
import com.dutongjian.app.domain.tts.TtsPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsController @Inject constructor(@ApplicationContext private val context: Context) : TtsPlayer {
    private val settings = TtsSettingsStore(context)
    private val _state = MutableStateFlow(TtsPlaybackState(engine = settings.read()))
    override val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()
    private var engine: TTSEngine = createEngine(_state.value.engine)
    private var queue: List<ReadingItem> = emptyList()
    private var itemIndex = 0
    private var sentences: List<String> = emptyList()
    private var sentenceIndex = 0

    override fun selectEngine(type: TtsEngineType) {
        settings.write(type)
        engine.release()
        engine = createEngine(type)
        _state.value = TtsPlaybackState(engine = type)
    }

    override fun speak(items: List<ReadingItem>, item: ReadingItem) {
        queue = items
        itemIndex = items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        sentenceIndex = 0
        startItem()
    }

    override fun pause() {
        if (!_state.value.isPlaying) return
        engine.pause()
        _state.value = _state.value.copy(isPaused = true)
    }

    override fun resume() {
        if (!_state.value.isPaused) return
        engine.resume()
        _state.value = _state.value.copy(isPaused = false)
    }

    override fun stop() {
        engine.stop()
        queue = emptyList()
        _state.value = TtsPlaybackState(engine = settings.read())
    }

    override fun release() = engine.release()

    private fun startItem() {
        val item = queue.getOrNull(itemIndex)
        if (item == null) {
            _state.value = TtsPlaybackState(engine = settings.read())
            return
        }
        sentences = ClassicalTextPreprocessor.sentences(item.original.ifBlank { item.content })
        sentenceIndex = sentenceIndex.coerceIn(0, sentences.lastIndex.coerceAtLeast(0))
        _state.value = _state.value.copy(
            isPlaying = true,
            isPaused = false,
            currentItemId = item.id,
            currentSentence = sentenceIndex,
            sentenceCount = sentences.size,
            progress = 0,
            error = null,
        )
        speakCurrentSentence()
    }

    private fun speakCurrentSentence() {
        val sentence = sentences.getOrNull(sentenceIndex)
        if (sentence == null) {
            itemIndex += 1
            sentenceIndex = 0
            startItem()
            return
        }
        engine.speak(
            sentence,
            onProgress = { progress -> _state.value = _state.value.copy(progress = progress) },
            onComplete = {
                sentenceIndex += 1
                _state.value = _state.value.copy(currentSentence = sentenceIndex)
                speakCurrentSentence()
            },
            onError = { error -> _state.value = _state.value.copy(isPlaying = false, error = error.message) },
        )
    }

    private fun createEngine(type: TtsEngineType): TTSEngine = when (type) {
        TtsEngineType.LOCAL -> LocalTtsEngine(context)
        TtsEngineType.EDGE -> EdgeTTSEngine(context)
        TtsEngineType.SHERPA_ONNX -> SherpaOnnxEngine(context)
    }
}
