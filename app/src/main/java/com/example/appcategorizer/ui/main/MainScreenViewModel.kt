package com.example.appcategorizer.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.appcategorizer.data.AppDao
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.data.CategoryRepository
import com.example.appcategorizer.data.CategoryTaxonomyEntity
import com.example.appcategorizer.domain.GetGroupedAppsUseCase
import com.example.appcategorizer.worker.CategorizationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val getGroupedAppsUseCase: GetGroupedAppsUseCase,
    private val categoryRepo: CategoryRepository,
    private val workManager: WorkManager,
    private val appDao: AppDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading("Initializing..."))
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGroupingEnabled = MutableStateFlow(true)
    val isGroupingEnabled: StateFlow<Boolean> = _isGroupingEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepo.isGroupingEnabledFlow().collect { enabled ->
                _isGroupingEnabled.value = enabled
                if (_uiState.value is MainScreenUiState.Success) {
                    loadApps() // Re-fetch from use case to get updated grouping
                }
            }
        }
        
        // Observe WorkManager for progress updates
        workManager.getWorkInfosForUniqueWorkLiveData("CategorizationWork")
            .observeForever { workInfos ->
                val workInfo = workInfos?.firstOrNull()
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getString(CategorizationWorker.PROGRESS_KEY) ?: "Categorizing..."
                            _uiState.value = MainScreenUiState.Loading(progress)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            loadApps()
                        }
                        WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString(CategorizationWorker.ERROR_KEY) ?: "Categorization failed"
                            _uiState.value = MainScreenUiState.Error(Exception(error))
                        }
                        else -> {}
                    }
                }
            }

        loadApps()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun forceRecategorize() {
        viewModelScope.launch(Dispatchers.IO) {
            categoryRepo.resetTaxonomyToDefault()
            appDao.clearAllAppCategories()
            loadApps()
        }
    }

    fun toggleGrouping(enabled: Boolean) {
        viewModelScope.launch {
            categoryRepo.setGroupingEnabled(enabled)
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            try {
                _uiState.value = MainScreenUiState.Loading("Discovering apps...")
                val apps = getGroupedAppsUseCase(_isGroupingEnabled.value)
                val taxonomy = categoryRepo.getTaxonomy()
                
                val uncategorized = apps.filter { it.llmCategory == null }
                if (uncategorized.isNotEmpty()) {
                    val workRequest = OneTimeWorkRequestBuilder<CategorizationWorker>().build()
                    workManager.enqueueUniqueWork("CategorizationWork", androidx.work.ExistingWorkPolicy.KEEP, workRequest)
                } else {
                    _uiState.value = MainScreenUiState.Success(apps, taxonomy)
                }
            } catch (e: Exception) {
                _uiState.value = MainScreenUiState.Error(e)
            }
        }
    }

    fun getSearchResults(query: String, apps: List<AppInfo>): List<SearchResult> {
        if (query.isBlank()) return apps.map { SearchResult(it, 0, null) }
        val q = query.lowercase()

        return apps.mapNotNull { app ->
            val name = app.name.lowercase()
            val shortDesc = app.shortDescription?.lowercase() ?: ""
            val fullDesc = app.fullDescription?.lowercase() ?: ""
            val review = app.reviewSnippet?.lowercase() ?: ""
            val aiCat = app.llmCategory?.lowercase() ?: ""
            val psCat = app.playStoreCategory?.lowercase() ?: ""

            var score = 0
            var snippet: String? = null

            if (name == q) {
                score += 100
            } else if (name.startsWith(q)) {
                score += 75
            } else if (name.contains(q)) {
                score += 50
            }

            if (aiCat.contains(q) || psCat.contains(q)) {
                score += 30
                if (snippet == null) snippet = "Category: ${app.llmCategory ?: app.playStoreCategory}"
            }

            if (shortDesc.contains(q)) {
                score += 20
                if (snippet == null) snippet = app.shortDescription
            }

            if (fullDesc.contains(q)) {
                score += 15
                if (snippet == null) {
                    val idx = fullDesc.indexOf(q)
                    val start = maxOf(0, idx - 20)
                    val end = minOf(fullDesc.length, idx + q.length + 20)
                    snippet = "...${app.fullDescription!!.substring(start, end)}..."
                }
            }

            if (review.contains(q)) {
                score += 10
                if (snippet == null) snippet = "Review: \"${app.reviewSnippet}\""
            }

            if (score > 0) {
                SearchResult(app, score, snippet)
            } else {
                null
            }
        }.sortedByDescending { it.score }
    }
}

data class SearchResult(
    val app: AppInfo,
    val score: Int,
    val matchSnippet: String?
)

sealed interface MainScreenUiState {
    data class Loading(
        val message: String,
        val currentModel: String? = null,
        val availableModels: List<String> = emptyList()
    ) : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val apps: List<AppInfo>, val taxonomy: List<CategoryTaxonomyEntity>) : MainScreenUiState
}
