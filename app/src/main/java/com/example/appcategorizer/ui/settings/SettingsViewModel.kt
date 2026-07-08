package com.example.appcategorizer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcategorizer.data.CategoryRepository
import com.example.appcategorizer.data.CategoryTaxonomyEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CategoryRepository(application)

    private val _taxonomy = MutableStateFlow<List<CategoryTaxonomyEntity>>(emptyList())
    val taxonomy: StateFlow<List<CategoryTaxonomyEntity>> = _taxonomy.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _openAIApiKey = MutableStateFlow("")
    val openAIApiKey: StateFlow<String> = _openAIApiKey.asStateFlow()

    private val _claudeApiKey = MutableStateFlow("")
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    private val _enginePreference = MutableStateFlow("Gemini") // Default to Gemini
    val enginePreference: StateFlow<String> = _enginePreference.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _taxonomy.value = repo.getTaxonomy()
            _geminiApiKey.value = repo.getGeminiApiKey() ?: ""
            _openAIApiKey.value = repo.getOpenAIApiKey() ?: ""
            _claudeApiKey.value = repo.getClaudeApiKey() ?: ""
            
            val pref = repo.getEnginePreference()
            // Migrate legacy preferences to Gemini
            if (pref == "Auto" || pref == "Local Only" || pref == "Cloud Only") {
                _enginePreference.value = "Gemini"
                repo.setEnginePreference("Gemini")
            } else {
                _enginePreference.value = pref
            }
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

    fun setOpenAIApiKey(key: String) {
        viewModelScope.launch {
            repo.setOpenAIApiKey(key)
            _openAIApiKey.value = key
        }
    }

    fun setClaudeApiKey(key: String) {
        viewModelScope.launch {
            repo.setClaudeApiKey(key)
            _claudeApiKey.value = key
        }
    }

    fun setEnginePreference(pref: String) {
        viewModelScope.launch {
            repo.setEnginePreference(pref)
            _enginePreference.value = pref
        }
    }
}
