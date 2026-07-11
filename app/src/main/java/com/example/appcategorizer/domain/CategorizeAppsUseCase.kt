package com.example.appcategorizer.domain

import com.example.appcategorizer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class CategorizeAppsUseCase @Inject constructor(
    private val appRepository: DefaultAppRepository,
    private val database: AppDatabase,
    private val playStoreService: PlayStoreService,
    private val categoryRepo: CategoryRepository,
    private val geminiEngine: GeminiCloudEngine,
    private val openAIEngine: OpenAICloudEngine,
    private val claudeEngine: ClaudeCloudEngine
) {

    suspend operator fun invoke(onProgress: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        onProgress("Discovering installed apps...")
        
        val installedApps = appRepository.getInstalledApps()
        val dbMetadata = database.appDao().getAllAppMetadata()
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

        if (uncategorizedApps.isEmpty()) {
            onProgress("All apps are categorized.")
            return@withContext
        }

        onProgress("Fetching Play Store context for ${uncategorizedApps.size} new apps...")
        
        val enrichedApps = uncategorizedApps.map { app ->
            val metadata = playStoreService.getAppMetadata(app.packageName)
            app.apply { playStoreCategory = metadata?.shortDescription ?: metadata?.playStoreCategory }
        }

        val taxonomy = categoryRepo.getTaxonomy()
        val customCategories = taxonomy.map { it.categoryName }

        val seenPackages = mutableSetOf<String>()
        var engine = determineEngine()
        
        if (engine == null) {
            throw Exception("No AI Engine available. Please configure an API Key in Settings.")
        }

        val batches = enrichedApps.chunked(30)
        for ((index, batch) in batches.withIndex()) {
            var retryCount = 0
            var rateLimitRetries = 0
            var success = false
            
            while (!success && retryCount < 10) {
                onProgress("AI Categorizing... (Batch ${index + 1} of ${batches.size})${if (retryCount > 0) "\nRetrying..." else ""} [Model: ${engine!!.getEngineName()}]")
                try {
                    val responseString = engine!!.categorizeBatch(batch, customCategories)
                    val batchMap = parseLlmResponse(responseString, batch, customCategories, seenPackages)
                    
                    batchMap.forEach { (category, appsInCategory) ->
                        for (app in appsInCategory) {
                            app.llmCategory = category
                            val existing = database.appDao().getAppMetadata(app.packageName)
                            if (existing != null) {
                                database.appDao().updateAppCategory(app.packageName, category)
                            } else {
                                database.appDao().insertMetadata(AppMetadataEntity(
                                    packageName = app.packageName,
                                    playStoreCategory = app.playStoreCategory,
                                    shortDescription = null,
                                    fullDescription = null,
                                    reviewSnippet = null,
                                    lastUpdated = 0L,
                                    aiCategory = category
                                ))
                            }
                        }
                    }
                    success = true
                } catch (e: Exception) {
                    val errorMsg = e.message ?: ""
                    if (errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED") || errorMsg.contains("quota")) {
                        if (errorMsg.contains("prepayment credits are depleted")) {
                            throw Exception("API Error: Your prepayment credits are depleted.")
                        }
                        
                        if (rateLimitRetries == 0) {
                            rateLimitRetries++
                            retryCount++
                            onProgress("Rate limit hit. Waiting 60s for quota reset...")
                            delay(62000L)
                            continue
                        } else {
                            if (engine is GeminiCloudEngine) {
                                GeminiCloudEngine.blacklistModel(engine!!.getEngineName())
                            }
                            engine = determineEngine()
                            if (engine == null) {
                                throw Exception("API Error: All available models are currently overloaded or rate-limited.")
                            }
                            retryCount++
                            rateLimitRetries = 0
                            delay(2000L)
                            continue
                        }
                    } else if (errorMsg.contains("503") || errorMsg.contains("UNAVAILABLE") || errorMsg.contains("high demand")) {
                        if (engine is GeminiCloudEngine) {
                            GeminiCloudEngine.blacklistModel(engine!!.getEngineName())
                        }
                        engine = determineEngine()
                        if (engine == null) {
                            throw Exception("API Error: All available models are currently overloaded or failing.")
                        }
                        retryCount++
                        delay(2000L)
                        continue
                    } else {
                        throw Exception("API Error: $errorMsg")
                    }
                }
            }
        }
    }

    private suspend fun determineEngine(): CategorizationEngine? {
        if (!categoryRepo.getOpenAIApiKey().isNullOrEmpty()) return openAIEngine
        if (!categoryRepo.getClaudeApiKey().isNullOrEmpty()) return claudeEngine
        if (!categoryRepo.getGeminiApiKey().isNullOrEmpty()) return geminiEngine
        return null
    }

    private fun parseLlmResponse(
        responseString: String,
        batchApps: List<AppInfo>,
        customCategories: List<String>,
        seenPackages: MutableSet<String>
    ): Map<String, List<AppInfo>> {
        val appMap = batchApps.associateBy { it.packageName }
        val categoryMap = mutableMapOf<String, MutableList<AppInfo>>()
        customCategories.forEach { categoryMap[it] = mutableListOf() }
        categoryMap["Misc"] = mutableListOf()

        try {
            val jsonStart = responseString.indexOf("[")
            val jsonEnd = responseString.lastIndexOf("]") + 1
            if (jsonStart != -1 && jsonEnd != -1) {
                val jsonString = responseString.substring(jsonStart, jsonEnd)
                val jsonArray = JSONArray(jsonString)
                
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val pkg = item.optString("package", item.optString("packageName", ""))
                    var category = item.optString("category", "Misc")
                    
                    if (!customCategories.contains(category)) {
                        category = "Misc"
                    }
                    
                    appMap[pkg]?.let { app ->
                        if (!seenPackages.contains(pkg)) {
                            categoryMap[category]?.add(app)
                            seenPackages.add(pkg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppCategorizer", "Failed to parse LLM response: $responseString", e)
        }

        // Fallback for missing apps
        for (app in batchApps) {
            if (!seenPackages.contains(app.packageName)) {
                categoryMap["Misc"]?.add(app)
                seenPackages.add(app.packageName)
            }
        }

        return categoryMap
    }
}
