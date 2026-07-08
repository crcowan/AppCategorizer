package com.example.appcategorizer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcategorizer.data.CategoryRepository
import com.example.appcategorizer.data.CategoryTaxonomyEntity
import com.example.appcategorizer.data.DownloadState
import com.example.appcategorizer.data.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CategoryRepository(application)
    private val downloader = ModelDownloader(application)

    private val _taxonomy = MutableStateFlow<List<CategoryTaxonomyEntity>>(emptyList())
    val taxonomy: StateFlow<List<CategoryTaxonomyEntity>> = _taxonomy.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _enginePreference = MutableStateFlow("Auto")
    val enginePreference: StateFlow<String> = _enginePreference.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _isModelDownloaded = MutableStateFlow(false)
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _taxonomy.value = repo.getTaxonomy()
            _geminiApiKey.value = repo.getGeminiApiKey() ?: ""
            _enginePreference.value = repo.getEnginePreference()
            _isModelDownloaded.value = downloader.isModelDownloaded()
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            _taxonomy.value = repo.resetTaxonomyToDefault()
        }
    }

    fun addOrUpdateCategory(oldName: String?, newName: String, parentCategory: String?) {
        viewModelScope.launch {
            repo.addOrUpdateCategory(oldName, newName, parentCategory)
            _taxonomy.value = repo.getTaxonomy()
        }
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch {
            repo.deleteCategory(categoryName)
            _taxonomy.value = repo.getTaxonomy()
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            repo.setGeminiApiKey(key)
            _geminiApiKey.value = key
        }
    }

    fun setEnginePreference(pref: String) {
        viewModelScope.launch {
            repo.setEnginePreference(pref)
            _enginePreference.value = pref
        }
    }

    fun importModelFromUri(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            downloader.importModelFromUri(uri, context).collectLatest { state ->
                _downloadState.value = state
                if (state is DownloadState.Finished) {
                    _isModelDownloaded.value = true
                }
            }
        }
    }
    
    fun deleteModel() {
        downloader.deleteModel()
        _isModelDownloaded.value = false
        _downloadState.value = DownloadState.Idle
    }
}
