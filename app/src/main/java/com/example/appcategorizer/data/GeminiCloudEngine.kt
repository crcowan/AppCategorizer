package com.example.appcategorizer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val appListString = apps.joinToString(separator = "\n") { app ->
            val desc = app.playStoreCategory ?: "No description provided"
            "- ${app.packageName} (${app.name}) : $desc"
        }

        val categoryListString = customCategories.joinToString(separator = "\n") { "               - \"$it\"" }

        val promptText = """
            You are a strict categorization AI.
            Here is a list of ${apps.size} installed apps in the format "PACKAGE_NAME (APP_NAME) : DESCRIPTION":
            
            $appListString
            
            CRITICAL INSTRUCTIONS:
            1. You MUST categorize EVERY SINGLE ONE of the ${apps.size} package names listed above.
            2. You MUST use ONLY the following exact categories:
$categoryListString
            3. You MUST output your response as a valid JSON array of objects.
            4. Each object must have a "package" key (the package name) and a "category" key (the assigned category).
            
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
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
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
        return "Gemini 1.5 Flash (Cloud API)"
    }
}
