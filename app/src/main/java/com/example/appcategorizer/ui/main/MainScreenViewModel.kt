package com.example.appcategorizer.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcategorizer.data.AppDatabase
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.data.DefaultAppRepository
import com.example.appcategorizer.data.CategorizationEngine
import com.example.appcategorizer.data.GeminiCloudEngine
import com.example.appcategorizer.data.OpenAICloudEngine
import com.example.appcategorizer.data.ClaudeCloudEngine
import com.example.appcategorizer.data.PlayStoreService
import com.example.appcategorizer.data.CategoryRepository
import com.example.appcategorizer.data.CategoryTaxonomyEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading("Initializing..."))
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGroupingEnabled = MutableStateFlow(true)
    val isGroupingEnabled: StateFlow<Boolean> = _isGroupingEnabled.asStateFlow()

    // We expose a combined state so search updates the UI instantly without re-emitting UiState.Success
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val appRepository = DefaultAppRepository(application)
    private val database = AppDatabase.getDatabase(application)
    private val playStoreService = PlayStoreService(database.appDao())
    private val categoryRepo = CategoryRepository(application)

    private val geminiEngine = GeminiCloudEngine(categoryRepo)
    private val openAIEngine = OpenAICloudEngine(categoryRepo)
    private val claudeEngine = ClaudeCloudEngine(categoryRepo)

    init {
        viewModelScope.launch {
            categoryRepo.isGroupingEnabledFlow().collect { enabled ->
                _isGroupingEnabled.value = enabled
                // Re-render grid if we already have apps
                val currentState = _uiState.value
                if (currentState is MainScreenUiState.Success) {
                    renderGrid(currentState.apps)
                }
            }
        }
        loadApps()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun forceRecategorize() {
        viewModelScope.launch {
            categoryRepo.resetTaxonomyToDefault() // Wipes and re-seeds DB taxonomy
            database.appDao().clearAllAppCategories()
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
                android.util.Log.d("AppCategorizer", "Loading apps...")
                _uiState.value = MainScreenUiState.Loading("Discovering installed apps...")
                
                val installedApps = appRepository.getInstalledApps()
                val dbMetadata = database.appDao().getAllAppMetadata()
                
                // Map DB cache
                val dbMap = dbMetadata.associateBy { it.packageName }
                
                val uncategorizedApps = mutableListOf<AppInfo>()
                val categorizedApps = mutableListOf<AppInfo>()
                
                for (app in installedApps) {
                    val meta = dbMap[app.packageName]
                    if (meta?.aiCategory != null) {
                        app.playStoreCategory = meta.shortDescription ?: meta.playStoreCategory
                        app.llmCategory = meta.aiCategory
                        categorizedApps.add(app)
                    } else {
                        uncategorizedApps.add(app)
                    }
                }

                if (uncategorizedApps.isNotEmpty()) {
                    runCategorizationFor(uncategorizedApps, categorizedApps)
                } else {
                    renderGrid(categorizedApps)
                }

            } catch (e: Exception) {
                android.util.Log.e("AppCategorizer", "Error during load", e)
                _uiState.value = MainScreenUiState.Error(e)
            }
        }
    }

    private suspend fun runCategorizationFor(uncategorizedApps: List<AppInfo>, alreadyCategorized: MutableList<AppInfo>) {
        _uiState.value = MainScreenUiState.Loading("Fetching Play Store context for ${uncategorizedApps.size} new apps...")
        
        val enrichedApps = uncategorizedApps.map { app ->
            val metadata = playStoreService.getAppMetadata(app.packageName)
            app.apply { playStoreCategory = metadata?.shortDescription ?: metadata?.playStoreCategory }
        }

        val taxonomy = categoryRepo.getTaxonomy()
        val customCategories = taxonomy.map { it.categoryName }
        android.util.Log.d("AppCategorizer", "LOADED TAXONOMY: $customCategories")

        val seenPackages = mutableSetOf<String>()
        var engine = determineEngine()
        
        if (engine == null) {
            _uiState.value = MainScreenUiState.Error(Exception("No AI Engine available. Please configure an API Key in Settings."))
            return
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), "Using Engine: ${engine!!.getEngineName()}", Toast.LENGTH_SHORT).show()
        }

        val batches = enrichedApps.chunked(30) // Increased batch size since cloud models have huge context limits
        for ((index, batch) in batches.withIndex()) {
            var retryCount = 0
            var rateLimitRetries = 0
            var success = false
            
            while (!success && retryCount < 10) {
                val availableModels = if (engine is com.example.appcategorizer.data.GeminiCloudEngine) com.example.appcategorizer.data.GeminiCloudEngine.lastAvailableModels else emptyList()
                _uiState.value = MainScreenUiState.Loading(
                    message = "AI Categorizing... (Batch ${index + 1} of ${batches.size})${if (retryCount > 0) "\nRetrying..." else ""}",
                    currentModel = engine!!.getEngineName(),
                    availableModels = availableModels
                )
                try {
                    // Perform generation on IO thread
                    val responseString = withContext(Dispatchers.IO) {
                        engine!!.categorizeBatch(batch, customCategories)
                    }
                    val batchMap = parseLlmResponse(responseString, batch, customCategories, seenPackages)
                    
                    // Save to DB
                    batchMap.forEach { (category, appsInCategory) ->
                        for (app in appsInCategory) {
                            app.llmCategory = category
                            database.appDao().updateAppCategory(app.packageName, category)
                            alreadyCategorized.add(app)
                        }
                    }
                    success = true
                } catch (e: Exception) {
                    android.util.Log.e("AppCategorizer", "Error during categorization of batch $index, retry: $retryCount", e)
                    val errorMsg = e.message ?: ""
                    if (errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED") || errorMsg.contains("quota")) {
                        if (errorMsg.contains("prepayment credits are depleted")) {
                            val msg = "API Error: Your prepayment credits are depleted."
                            withContext(Dispatchers.Main) {
                                Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                            }
                            _uiState.value = MainScreenUiState.Error(Exception(msg))
                            return // Abort completely, retrying won't help
                        }
                        
                        if (rateLimitRetries == 0) {
                            rateLimitRetries++
                            retryCount++
                            val delayMsg = "Rate limit hit. Waiting 60s for quota reset..."
                            val availableModels = if (engine is com.example.appcategorizer.data.GeminiCloudEngine) com.example.appcategorizer.data.GeminiCloudEngine.lastAvailableModels else emptyList()
                            _uiState.value = MainScreenUiState.Loading(
                                message = delayMsg,
                                currentModel = engine!!.getEngineName(),
                                availableModels = availableModels
                            )
                            kotlinx.coroutines.delay(62000L) // Wait slightly over a minute to clear RPM limits
                            continue
                        } else {
                            // The model is still rate limited (likely a daily limit). Blacklist it and failover!
                            if (engine is com.example.appcategorizer.data.GeminiCloudEngine) {
                                com.example.appcategorizer.data.GeminiCloudEngine.blacklistModel(engine!!.getEngineName())
                            }
                            engine = determineEngine()
                            if (engine == null) {
                                val msg = "API Error: All available models are currently overloaded or rate-limited."
                                _uiState.value = MainScreenUiState.Error(Exception(msg))
                                return // Abort remaining batches
                            }
                            
                            retryCount++
                            rateLimitRetries = 0 // Reset for the new model
                            val availableModels = if (engine is com.example.appcategorizer.data.GeminiCloudEngine) com.example.appcategorizer.data.GeminiCloudEngine.lastAvailableModels else emptyList()
                            _uiState.value = MainScreenUiState.Loading(
                                message = "Rate limit hit again. Failing over to ${engine!!.getEngineName()}...",
                                currentModel = engine!!.getEngineName(),
                                availableModels = availableModels
                            )
                            kotlinx.coroutines.delay(2000L) // Brief pause before retry
                            continue
                        }
                    } else if (errorMsg.contains("503") || errorMsg.contains("UNAVAILABLE") || errorMsg.contains("high demand")) {
                        // The model is overloaded. Blacklist it and failover to the next best model!
                        if (engine is com.example.appcategorizer.data.GeminiCloudEngine) {
                            com.example.appcategorizer.data.GeminiCloudEngine.blacklistModel(engine!!.getEngineName())
                        }
                        engine = determineEngine()
                        if (engine == null) {
                            val msg = "API Error: All available models are currently overloaded or failing."
                            _uiState.value = MainScreenUiState.Error(Exception(msg))
                            return // Abort remaining batches
                        }
                        
                        retryCount++
                        val availableModels = if (engine is com.example.appcategorizer.data.GeminiCloudEngine) com.example.appcategorizer.data.GeminiCloudEngine.lastAvailableModels else emptyList()
                        _uiState.value = MainScreenUiState.Loading(
                            message = "Model overloaded. Failing over...",
                            currentModel = engine!!.getEngineName(),
                            availableModels = availableModels
                        )
                        kotlinx.coroutines.delay(2000L) // Brief pause before retry
                        continue
                    } else {
                        // Non-rate limit error, don't retry
                        val msg = "API Error: $errorMsg"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                        }
                        _uiState.value = MainScreenUiState.Error(Exception(msg))
                        return // Abort remaining batches
                    }
                }
            }
            
            if (index < batches.size - 1) {
                _uiState.value = MainScreenUiState.Loading("Waiting for API rate limit... (4.5s)")
                kotlinx.coroutines.delay(4500L)
            }
        }
        
        // Handle apps that the AI completely dropped
        val categorizedPackages = alreadyCategorized.map { it.packageName }.toSet()
        val completelyDropped = uncategorizedApps.filter { !categorizedPackages.contains(it.packageName) }
        android.util.Log.d("AppCategorizer", "COMPLETELY DROPPED APPS COUNT: ${completelyDropped.size}")
        for (app in completelyDropped) {
            android.util.Log.w("AppCategorizer", "FORCED TO MISC: ${app.packageName} - ${app.name}")
            app.llmCategory = "Misc"
            database.appDao().updateAppCategory(app.packageName, "Misc")
            alreadyCategorized.add(app)
        }

        renderGrid(alreadyCategorized)
    }

    private suspend fun determineEngine(): CategorizationEngine? {
        val pref = categoryRepo.getEnginePreference()
        return when (pref) {
            "OpenAI" -> if (openAIEngine.isAvailable()) openAIEngine else null
            "Claude" -> if (claudeEngine.isAvailable()) claudeEngine else null
            else -> if (geminiEngine.isAvailable()) geminiEngine else null
        }
    }

    private suspend fun renderGrid(allApps: List<AppInfo>) {
        val taxonomy = categoryRepo.getTaxonomy()
        _uiState.value = MainScreenUiState.Success(allApps.toList(), taxonomy)
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = Array(lhsLength + 1) { it }
        var newCost = Array(lhsLength + 1) { 0 }
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    private fun matchCategoryFuzzy(hallucinated: String, masterList: List<String>): String {
        return masterList.minByOrNull { levenshtein(hallucinated.lowercase(), it.lowercase()) } ?: "Misc"
    }

    private fun parseLlmResponse(
        responseString: String,
        originalApps: List<AppInfo>,
        customCategories: List<String>,
        seenPackages: MutableSet<String>
    ): Map<String, List<AppInfo>> {
        val result = mutableMapOf<String, MutableList<AppInfo>>()
        
        android.util.Log.d("AppCategorizer", "RAW LLM RESPONSE:\n$responseString")
        
        try {
            // Remove markdown blocks if present
            val cleanedResponse = responseString.replace(Regex("```json\\n?|```"), "").trim()
            
            var jsonArray: JSONArray? = null
            
            fun tryParse(text: String): JSONArray? {
                try {
                    return JSONArray(text)
                } catch (e: Exception) {
                    try {
                        val jsonObj = JSONObject(text)
                        val keys = jsonObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val possibleArray = jsonObj.optJSONArray(key)
                            if (possibleArray != null) {
                                return possibleArray
                            }
                        }
                    } catch (e2: Exception) {
                        // ignore
                    }
                }
                return null
            }

            jsonArray = tryParse(cleanedResponse)
            
            if (jsonArray == null) {
                // Try to extract a JSON block using regex if there is conversational text
                val jsonRegex = Regex("\\[.*\\]|\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
                val match = jsonRegex.find(cleanedResponse)
                if (match != null) {
                    jsonArray = tryParse(match.value)
                }
            }
            
            if (jsonArray == null) {
                android.util.Log.e("AppCategorizer", "No JSON Array found in response: $cleanedResponse")
                return result
            }
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val rawPkg = item.optString("package")
                val rawCategory = item.optString("category")
                
                if (rawPkg.isBlank() || rawCategory.isBlank()) continue
                
                // Deduplicate
                if (seenPackages.contains(rawPkg)) continue
                
                val matchedApp = originalApps.find { it.packageName.equals(rawPkg, ignoreCase = true) }
                    ?: originalApps.find { it.name.equals(rawPkg, ignoreCase = true) }
                
                if (matchedApp != null) {
                    val finalCategory = matchCategoryFuzzy(rawCategory, customCategories)
                    if (finalCategory == "Misc") {
                        android.util.Log.w("AppCategorizer", "LLM CLASSIFIED AS MISC: ${matchedApp.packageName} -> raw category was '$rawCategory'")
                    }
                    result.getOrPut(finalCategory) { mutableListOf() }.add(matchedApp)
                    seenPackages.add(matchedApp.packageName)
                    android.util.Log.d("AppCategorizer", "Matched: ${matchedApp.packageName} -> $finalCategory (from $rawCategory)")
                } else {
                    android.util.Log.w("AppCategorizer", "Hallucinated Package in LLM Response: $rawPkg")
                }
            }
            
            // Log missing packages
            val missing = originalApps.filter { !seenPackages.contains(it.packageName) }
            if (missing.isNotEmpty()) {
                android.util.Log.w("AppCategorizer", "LLM dropped apps: ${missing.map { it.packageName }}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AppCategorizer", "Failed to parse JSON response: $responseString", e)
        }
        return result
    }
}

sealed interface MainScreenUiState {
    data class Loading(
        val message: String,
        val currentModel: String? = null,
        val availableModels: List<String> = emptyList()
    ) : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val apps: List<AppInfo>, val taxonomy: List<CategoryTaxonomyEntity>) : MainScreenUiState
}
