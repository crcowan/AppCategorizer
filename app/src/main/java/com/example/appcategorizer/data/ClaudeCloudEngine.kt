package com.example.appcategorizer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ClaudeCloudEngine(private val repository: CategoryRepository) : CategorizationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun categorizeBatch(apps: List<AppInfo>, customCategories: List<String>): String = withContext(Dispatchers.IO) {
        val apiKey = repository.getClaudeApiKey()
            ?: throw IllegalStateException("Claude API Key is not set")
            
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

        val prompt = """
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
            
            Output ONLY the valid JSON array and nothing else.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", "claude-3-haiku-20240307")
            put("max_tokens", 1024)
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response from Claude")
            val jsonResponse = JSONObject(responseBody)
            
            // Claude returns content as an array of blocks
            val contentArray = jsonResponse.getJSONArray("content")
            if (contentArray.length() > 0) {
                return@withContext contentArray.getJSONObject(0).getString("text")
            }
            
            throw IllegalStateException("No text returned from Claude")
        }
    }

    override suspend fun isAvailable(): Boolean {
        return !repository.getClaudeApiKey().isNullOrBlank()
    }

    override fun getEngineName(): String {
        return "Anthropic Claude 3 Haiku"
    }
}
