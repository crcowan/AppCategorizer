package com.example.appcategorizer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiCloudEngine(private val repository: CategoryRepository) : CategorizationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun categorizeBatch(apps: List<AppInfo>, customCategories: List<String>): String {
        val apiKey = repository.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) throw IllegalStateException("Gemini API Key is not set")

        val appListJsonArray = JSONArray()
        for (app in apps) {
            appListJsonArray.put(JSONObject().apply {
                put("package", app.packageName)
                put("name", app.name)
                put("description", app.playStoreCategory ?: "No description provided")
            })
        }
        val appListString = appListJsonArray.toString(2)

        val categoryListString = customCategories.joinToString(separator = "\n") { "               - \"$it\"" }

        val promptText = """
            You are an expert app categorizer.
            Here is a JSON array of ${apps.size} installed apps to categorize:
            
            $appListString
            
            CRITICAL INSTRUCTIONS:
            1. You MUST categorize EVERY SINGLE ONE of the ${apps.size} package names listed above.
            2. You MUST use ONLY the following exact categories:
$categoryListString
            3. If an app does not perfectly fit into any category, you MUST select the closest conceptual match. DO NOT omit any apps. Your output array MUST contain exactly ${apps.size} items.
            4. You MUST output your response as a valid JSON array of objects.
            5. Each object must have a "package" key (the exact package name) and a "category" key (the assigned category).
            
            Example of EXPECTED JSON output:
            [
                {"package": "com.facebook.katana", "category": "Social Media"},
                {"package": "com.spotify.music", "category": "Music Players"},
                {"package": "org.telegram.messenger", "category": "Messaging & SMS"}
            ]
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", promptText)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        return withContext(Dispatchers.IO) {
            val modelName = getBestAvailableModel(apiKey)
            val url = "https://generativelanguage.googleapis.com/v1beta/$modelName:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    try {
                        val modelsRequest = Request.Builder()
                            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                            .get()
                            .build()
                        val modelsResponse = client.newCall(modelsRequest).execute()
                        val modelsBody = modelsResponse.body?.string() ?: ""
                        if (modelsResponse.isSuccessful) {
                            val modelsJson = JSONObject(modelsBody)
                            val modelsArray = modelsJson.optJSONArray("models")
                            val modelNames = mutableListOf<String>()
                            if (modelsArray != null) {
                                for (i in 0 until modelsArray.length()) {
                                    modelNames.add(modelsArray.getJSONObject(i).optString("name"))
                                }
                            }
                            throw Exception("Gemini HTTP Error ${response.code}: $body\n\nAVAILABLE MODELS FOR THIS API KEY:\n${modelNames.joinToString("\n")}")
                        }
                    } catch (e: Exception) {
                        // ignore and throw original if fetching models fails
                    }
                    throw Exception("Gemini HTTP Error ${response.code}: $body")
                }
                
                // Parse the Gemini REST response
                val jsonResponse = JSONObject(body)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "")
                        }
                    }
                }
                throw Exception("Failed to extract text from Gemini response: $body")
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        return !repository.getGeminiApiKey().isNullOrBlank()
    }

    override fun getEngineName(): String {
        return cachedModelName ?: "Gemini (Cloud API Auto-Select)"
    }
    
    companion object {
        private var cachedModelName: String? = null
        private val blacklistedModels = mutableSetOf<String>()
        var lastAvailableModels: List<String> = emptyList()
            private set
        
        fun blacklistModel(modelName: String) {
            blacklistedModels.add(modelName)
            if (cachedModelName == modelName) {
                cachedModelName = null
            }
        }
        
        private val PREFERRED_MODELS = listOf(
            // We prioritize the most stable and generous models. 
            // -flash-latest typically maps to the current stable version of Flash, which has massive batch quotas.
            // -flash-lite-latest is an incredibly fast, highly generous model for simple tasks like classification.
            "models/gemini-flash-latest",
            "models/gemini-flash-lite-latest",
            "models/gemini-3.5-flash", // Leave 3.5 as fallback if the others are missing
            "models/gemini-2.0-flash-lite",
            "models/gemini-2.0-flash",
            "models/gemini-2.5-pro"
        )
    }
    
    private fun getBestAvailableModel(apiKey: String): String {
        cachedModelName?.let { return it }

        try {
            val modelsRequest = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .get()
                .build()
            client.newCall(modelsRequest).execute().use { modelsResponse ->
                val modelsBody = modelsResponse.body?.string() ?: ""
                if (modelsResponse.isSuccessful) {
                    val modelsJson = JSONObject(modelsBody)
                    val modelsArray = modelsJson.optJSONArray("models")
                    val availableModels = mutableListOf<String>()
                    if (modelsArray != null) {
                        for (i in 0 until modelsArray.length()) {
                            availableModels.add(modelsArray.getJSONObject(i).optString("name"))
                        }
                    }
                    
                    lastAvailableModels = availableModels.toList()
                    
                    // Log the available models so we can see what Google actually provides
                    Log.d("AppCategorizer", "AVAILABLE MODELS FROM GOOGLE: $availableModels")
                    
                    for (preferred in PREFERRED_MODELS) {
                        val match = availableModels.firstOrNull { 
                            it.startsWith(preferred) && !blacklistedModels.contains(it)
                        }
                        if (match != null) {
                            cachedModelName = match
                            return match
                        }
                    }
                    
                    // If no preferred models match, try to find any model with "flash" or "pro"
                    val fallback = availableModels.firstOrNull { 
                        (it.contains("flash") || it.contains("pro")) && !blacklistedModels.contains(it) 
                    }
                    if (fallback != null) {
                        cachedModelName = fallback
                        return fallback
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore error and fall through to default
        }
        
        // Final fallback if network fetch fails
        val defaultModel = "models/gemini-1.5-pro" // Try 1.5-pro as fallback since flash is missing
        cachedModelName = defaultModel
        return defaultModel
    }
}
