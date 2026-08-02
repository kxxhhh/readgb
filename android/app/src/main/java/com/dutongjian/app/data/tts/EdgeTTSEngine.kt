package com.dutongjian.app.data.tts

import android.content.Context
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.TTSEngine

/**
 * Edge-TTS boundary. The app keeps this adapter separate so a signed Edge audio
 * transport can be added without changing the reading queue. Until that transport
 * is configured, Android's Chinese voice is used as an explicit local fallback.
 */
class EdgeTTSEngine(context: Context) : TTSEngine by AndroidTextToSpeechEngine(context, TtsEngineType.EDGE)
