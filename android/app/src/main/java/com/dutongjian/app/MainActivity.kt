package com.dutongjian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dutongjian.app.ui.DutongjianApp
import com.dutongjian.app.ui.ReadingViewModel
import com.dutongjian.app.ui.theme.DutongjianTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ReadingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            var darkMode by rememberSaveable { mutableStateOf(false) }
            DutongjianTheme(darkTheme = darkMode) {
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
                        onDarkModeToggle = { darkMode = !darkMode },
                    )
                }
            }
        }
    }
}
