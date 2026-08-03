package com.dutongjian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dutongjian.app.data.AppSettingsStore
import com.dutongjian.app.domain.model.AppThemeMode
import com.dutongjian.app.domain.model.ReadingMode
import com.dutongjian.app.domain.model.ReadingPreferences
import com.dutongjian.app.domain.model.TextScript
import com.dutongjian.app.ui.DutongjianApp
import com.dutongjian.app.ui.ReadingViewModel
import com.dutongjian.app.ui.theme.DutongjianTheme
import com.dutongjian.app.domain.text.ClassicalGlossary
import com.dutongjian.app.domain.text.ClassicalScriptMapper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ReadingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching { assets.open("classical_char_map.json").use(ClassicalScriptMapper::load) }
        runCatching { assets.open("classical_glossary.json").use(ClassicalGlossary::load) }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val appSettings = remember { AppSettingsStore(applicationContext) }
            val initialReadingPreferences = remember { appSettings.readReadingPreferences() }
            var themeModeName by rememberSaveable { mutableStateOf(appSettings.readThemeMode().name) }
            var readingModeName by rememberSaveable { mutableStateOf(initialReadingPreferences.mode.name) }
            var textScriptName by rememberSaveable { mutableStateOf(initialReadingPreferences.script.name) }
            var fontPercent by rememberSaveable { mutableStateOf(initialReadingPreferences.fontPercent) }
            var lineSpacingPercent by rememberSaveable { mutableStateOf(initialReadingPreferences.lineSpacingPercent) }
            var motionEnabled by rememberSaveable { mutableStateOf(initialReadingPreferences.motionEnabled) }
            val themeMode = AppThemeMode.fromName(themeModeName)
            val readingPreferences = ReadingPreferences(
                mode = runCatching { ReadingMode.valueOf(readingModeName) }.getOrDefault(ReadingMode.PARALLEL),
                script = runCatching { TextScript.valueOf(textScriptName) }.getOrDefault(TextScript.SIMPLIFIED),
                fontPercent = fontPercent.coerceIn(80, 140),
                lineSpacingPercent = lineSpacingPercent.coerceIn(90, 140),
                motionEnabled = motionEnabled,
            )
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            DutongjianTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DutongjianApp(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onSearch = viewModel::search,
                        onCategorySelected = viewModel::selectCategory,
                        onCycleFeatured = viewModel::cycleFeatured,
                        onLibraryTabSelected = viewModel::selectLibraryTab,
                        onFavoriteToggle = viewModel::toggleFavorite,
                        onOpen = viewModel::open,
                        onSectionSelected = viewModel::selectSection,
                        onVolumeSelected = viewModel::selectVolume,
                        onYearSelected = viewModel::selectYear,
                        onCatalogBack = viewModel::catalogBack,
                        onKnowledgeSearch = viewModel::searchKnowledge,
                        onKnowledgeCategorySelected = viewModel::selectKnowledgeCategory,
                        aiState = state.ai,
                        onAiBaseUrlChanged = viewModel::updateAiBaseUrl,
                        onAiModelChanged = viewModel::updateAiModel,
                        onAiApiKeyChanged = viewModel::updateAiApiKey,
                        onAiSettingsSave = viewModel::saveAiSettings,
                        onAiApiKeyClear = viewModel::clearAiApiKey,
                        onAiGenerate = viewModel::generateAi,
                        onRoleDialogueGenerate = viewModel::generateRoleDialogue,
                        onAiContinue = viewModel::continueAi,
                        onAiResultClear = viewModel::clearAiResult,
                        onAiResultOpen = viewModel::openAiResult,
                        onAiResultDelete = viewModel::deleteAiResult,
                        onTtsEngineSelected = viewModel::selectTtsEngine,
                        onSpeak = viewModel::speak,
                        onPauseTts = viewModel::pauseTts,
                        onResumeTts = viewModel::resumeTts,
                        onStopTts = viewModel::stopTts,
                        onStartTtsSleepTimer = viewModel::startTtsSleepTimer,
                        onStopTtsAfterCurrentItem = viewModel::stopTtsAfterCurrentItem,
                        onCancelTtsSleepTimer = viewModel::cancelTtsSleepTimer,
                        onStopReadingSession = viewModel::stopReadingSession,
                        onSaveNote = viewModel::saveNote,
                        onDeleteNote = viewModel::deleteNote,
                        themeMode = themeMode,
                        onThemeModeSelected = { mode ->
                            themeModeName = mode.name
                            appSettings.writeThemeMode(mode)
                        },
                        readingPreferences = readingPreferences,
                        onReadingPreferencesChanged = { value ->
                            readingModeName = value.mode.name
                            textScriptName = value.script.name
                            fontPercent = value.fontPercent
                            lineSpacingPercent = value.lineSpacingPercent
                            motionEnabled = value.motionEnabled
                            appSettings.writeReadingPreferences(value)
                        },
                    )
                }
            }
        }
    }
}
