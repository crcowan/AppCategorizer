package com.example.appcategorizer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

class CategoryRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val appDao = database.appDao()

    suspend fun getTaxonomy(): List<CategoryTaxonomyEntity> = withContext(Dispatchers.IO) {
        val existing = appDao.getAllTaxonomy()
        if (existing.isNotEmpty()) {
            // Check if it's an outdated taxonomy missing the newer categories
            if (existing.none { it.categoryName == "Developer Tools" }) {
                appDao.clearTaxonomy() // wipe and re-seed
            } else {
                return@withContext existing
            }
        }

        // Seed with defaults
        val defaults = listOf(
            // Communication
            CategoryTaxonomyEntity("Social Media", "Communication"),
            CategoryTaxonomyEntity("Messaging & SMS", "Communication"),
            CategoryTaxonomyEntity("Email", "Communication"),
            CategoryTaxonomyEntity("Phone & Contacts", "Communication"),
            CategoryTaxonomyEntity("Browsers", "Communication"),
            // Media
            CategoryTaxonomyEntity("Music Players", "Media"),
            CategoryTaxonomyEntity("Video & TV", "Media"),
            CategoryTaxonomyEntity("Podcasts & Audiobooks", "Media"),
            CategoryTaxonomyEntity("Photos & Gallery", "Media"),
            CategoryTaxonomyEntity("Camera", "Media"),
            // Information
            CategoryTaxonomyEntity("News & Magazines", "Information"),
            CategoryTaxonomyEntity("Social News & Forums", "Information"),
            CategoryTaxonomyEntity("Sports", "Information"),
            CategoryTaxonomyEntity("Weather", "Information"),
            // Daily Life
            CategoryTaxonomyEntity("Shopping & Grocery", "Daily Life"),
            CategoryTaxonomyEntity("Food & Drink", "Daily Life"),
            CategoryTaxonomyEntity("Finance & Banking", "Daily Life"),
            CategoryTaxonomyEntity("Health & Fitness", "Daily Life"),
            // Travel
            CategoryTaxonomyEntity("Navigation & Maps", "Travel"),
            CategoryTaxonomyEntity("Travel & Transit", "Travel"),
            CategoryTaxonomyEntity("Local Services", "Travel"),
            // Work & Data
            CategoryTaxonomyEntity("Productivity", "Work & Data"),
            CategoryTaxonomyEntity("Notes & Writing", "Work & Data"),
            CategoryTaxonomyEntity("Databases & Data Management", "Work & Data"),
            CategoryTaxonomyEntity("Education", "Work & Data"),
            // System
            CategoryTaxonomyEntity("System Management & Cleaning", "System"),
            CategoryTaxonomyEntity("File Managers", "System"),
            CategoryTaxonomyEntity("Security & Privacy", "System"),
            CategoryTaxonomyEntity("Device Customization", "System"),
            CategoryTaxonomyEntity("Developer Tools", "System"),
            // Hardware
            CategoryTaxonomyEntity("Smart Home & IoT", "Hardware"),
            CategoryTaxonomyEntity("Wearables & Watches", "Hardware"),
            // Other
            CategoryTaxonomyEntity("Games", "Daily Life"),
            CategoryTaxonomyEntity("Misc", "Other")
        )
        appDao.insertTaxonomy(defaults)
        defaults
    }

    suspend fun resetTaxonomyToDefault() = withContext(Dispatchers.IO) {
        appDao.clearTaxonomy()
        getTaxonomy() // Will automatically re-seed
    }
    
    suspend fun saveTaxonomy(taxonomy: List<CategoryTaxonomyEntity>) = withContext(Dispatchers.IO) {
        appDao.clearTaxonomy()
        appDao.insertTaxonomy(taxonomy)
    }

    suspend fun addOrUpdateCategory(oldName: String?, newName: String, parentCategory: String?) = withContext(Dispatchers.IO) {
        if (oldName != null && oldName != newName) {
            appDao.deleteTaxonomyCategory(oldName)
            appDao.renameAppCategory(oldName, newName)
        }
        val entity = CategoryTaxonomyEntity(
            categoryName = newName,
            parentCategory = if (parentCategory.isNullOrBlank()) null else parentCategory
        )
        appDao.insertTaxonomy(listOf(entity))
    }

    suspend fun deleteCategory(categoryName: String) = withContext(Dispatchers.IO) {
        appDao.deleteTaxonomyCategory(categoryName)
        appDao.renameAppCategory(categoryName, "Misc")
    }

    suspend fun isGroupingEnabled(): Boolean = withContext(Dispatchers.IO) {
        val setting = appDao.getSetting("GROUP_BY_MAIN_CATEGORY")
        setting?.toBoolean() ?: true // Default to true
    }

    fun isGroupingEnabledFlow(): kotlinx.coroutines.flow.Flow<Boolean> {
        return appDao.getSettingFlow("GROUP_BY_MAIN_CATEGORY").map { it?.toBoolean() ?: true }
    }

    suspend fun setGroupingEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingsEntity("GROUP_BY_MAIN_CATEGORY", enabled.toString()))
    }

    suspend fun getGeminiApiKey(): String? = withContext(Dispatchers.IO) {
        appDao.getSetting("GEMINI_API_KEY")
    }

    suspend fun setGeminiApiKey(key: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingsEntity("GEMINI_API_KEY", key))
    }

    suspend fun getOpenAIApiKey(): String? = withContext(Dispatchers.IO) {
        appDao.getSetting("OPENAI_API_KEY")
    }

    suspend fun setOpenAIApiKey(key: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingsEntity("OPENAI_API_KEY", key))
    }

    suspend fun getClaudeApiKey(): String? = withContext(Dispatchers.IO) {
        appDao.getSetting("CLAUDE_API_KEY")
    }

    suspend fun setClaudeApiKey(key: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingsEntity("CLAUDE_API_KEY", key))
    }

    // Theme Preference
    suspend fun getThemePreference(): String = withContext(Dispatchers.IO) {
        appDao.getSetting("THEME_PREFERENCE") ?: "System"
    }

    suspend fun setThemePreference(pref: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingsEntity("THEME_PREFERENCE", pref))
    }

    // Zoom Level Preference
    suspend fun getZoomLevel(): String = withContext(Dispatchers.IO) {
        appDao.getSetting("GRID_ZOOM_LEVEL") ?: "Medium"
    }

    suspend fun setZoomLevel(level: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingsEntity("GRID_ZOOM_LEVEL", level))
    }

    suspend fun getEnginePreference(): String = withContext(Dispatchers.IO) {
        appDao.getSetting("ENGINE_PREFERENCE") ?: "Auto"
    }

    suspend fun setEnginePreference(pref: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingsEntity("ENGINE_PREFERENCE", pref))
    }
}
