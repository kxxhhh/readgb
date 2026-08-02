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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
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
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private var stopAfterCurrent = false

    override fun selectEngine(type: TtsEngineType) {
        cancelSleepTimer()
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
        sleepJob?.cancel()
        sleepJob = null
        stopAfterCurrent = false
        _state.value = TtsPlaybackState(engine = settings.read())
    }

    override fun startSleepTimer(minutes: Int) {
        val seconds = minutes.coerceAtLeast(1) * 60L
        sleepJob?.cancel()
        stopAfterCurrent = false
        _state.value = _state.value.copy(sleepRemainingSeconds = seconds, stopAfterCurrentItem = false)
        sleepJob = timerScope.launch {
            var remaining = seconds
            while (isActive && remaining > 0L) {
                delay(1000L)
                remaining -= 1L
                _state.value = _state.value.copy(sleepRemainingSeconds = remaining)
            }
            if (isActive) stop()
        }
    }

    override fun stopAfterCurrentItem() {
        sleepJob?.cancel()
        sleepJob = null
        stopAfterCurrent = true
        _state.value = _state.value.copy(sleepRemainingSeconds = 0L, stopAfterCurrentItem = true)
    }

    override fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        stopAfterCurrent = false
        _state.value = _state.value.copy(sleepRemainingSeconds = 0L, stopAfterCurrentItem = false)
    }

    override fun release() {
        sleepJob?.cancel()
        timerScope.coroutineContext.cancel()
        engine.release()
    }

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
                if (sentenceIndex >= sentences.size && stopAfterCurrent) {
                    stop()
                    return@speak
                }
                _state.value = _state.value.copy(currentSentence = sentenceIndex)
                speakCurrentSentence()
            },
            onError = { error -> _state.value = _state.value.copy(isPlaying = false, error = error.message) },
        )
    }

    private fun createEngine(type: TtsEngineType): TTSEngine = when (type) {
        TtsEngineType.LOCAL -> LocalTtsEngine(context)
        TtsEngineType.EDGE -> EdgeTTSEngine(context)
    }
}
