package com.dutongjian.app.data.tts

import android.content.Context
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.tts.TTSEngine

/**
 * Offline engine boundary. Model files are optional app assets, so installations
 * without a bundled model remain usable through the Android voice fallback.
 */
class SherpaOnnxEngine(context: Context) : TTSEngine by AndroidTextToSpeechEngine(context, TtsEngineType.SHERPA_ONNX)
