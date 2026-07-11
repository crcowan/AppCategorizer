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

class OpenAICloudEngine(private val repository: CategoryRepository) : CategorizationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun categorizeBatch(apps: List<AppInfo>, customCategories: List<String>): String = withContext(Dispatchers.IO) {
        val apiKey = repository.getOpenAIApiKey()
            ?: throw IllegalStateException("OpenAI API Key is not set")
            
        val appListJsonArray = JSONArray()
        for (app in apps) {
            appListJsonArray.put(JSONObject().apply {
                put("package", app.packageName)
                put("name", app.name)
                val descText = buildString {
                    append(app.playStoreCategory ?: "No category")
                    if (!app.shortDescription.isNullOrBlank()) append(" | ${app.shortDescription}")
                    if (!app.fullDescription.isNullOrBlank()) {
                        val full = app.fullDescription!!
                        append(" | ")
                        append(if (full.length > 300) full.substring(0, 300) + "..." else full)
                    }
                }
                put("description", descText)
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
            4. You MUST NOT invent new categories. You MUST NOT use 'Miscellaneous' or 'Misc' unless it is explicitly listed above.
            5. You MUST output your response as a valid JSON array of objects.
            6. Each object must have a "package" key (the exact package name) and a "category" key (the assigned category).
            
            Example of EXPECTED JSON output:
            [
                {"package": "com.facebook.katana", "category": "Social Media"},
                {"package": "com.spotify.music", "category": "Music Players"},
                {"package": "org.telegram.messenger", "category": "Messaging & SMS"}
            ]
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.2)
            put("response_format", JSONObject().apply { put("type", "json_object") })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a helpful assistant that only outputs valid JSON.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt + "\nOutput a JSON object with a single key 'results' mapping to the JSON array.")
                })
            })
            put("temperature", 0.1)
            put("response_format", JSONObject().apply {
                put("type", "json_object")
            })
        }
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response from OpenAI")
            val jsonResponse = JSONObject(responseBody)
            val content = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            
            // Extract the 'results' array if it exists to normalize output
            try {
                val contentObj = JSONObject(content)
                if (contentObj.has("results")) {
                    return@withContext contentObj.getJSONArray("results").toString()
                }
            } catch (e: Exception) {
                // If it's already an array, just return it
            }
            
            return@withContext content
        }
    }

    override suspend fun isAvailable(): Boolean {
        return !repository.getOpenAIApiKey().isNullOrBlank()
    }

    override fun getEngineName(): String {
        return "OpenAI GPT-4o-mini"
    }
}
