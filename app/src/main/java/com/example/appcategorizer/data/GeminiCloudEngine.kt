package com.example.appcategorizer.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

class GeminiCloudEngine(private val repository: CategoryRepository) : CategorizationEngine {

    override suspend fun categorizeBatch(apps: List<AppInfo>, customCategories: List<String>): String {
        val apiKey = repository.getGeminiApiKey()
            ?: throw IllegalStateException("Gemini API Key is not set")
            
        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
            }
        )

        val appListString = apps.joinToString(separator = "\n") { app ->
            val desc = app.playStoreCategory ?: "No description provided"
            "- ${app.packageName} (${app.name}) : $desc"
        }

        val categoryListString = customCategories.joinToString(separator = "\n") { "               - \"$it\"" }

        val prompt = """
            You are a strict categorization AI.
            Here is a list of ${apps.size} installed apps in the format "PACKAGE_NAME (APP_NAME) : DESCRIPTION":
            
            $appListString
            
            CRITICAL INSTRUCTIONS:
            1. You MUST categorize EVERY SINGLE ONE of the ${apps.size} package names listed above.
            2. You MUST use ONLY the following exact categories:
$categoryListString
            3. You MUST output your response as a simple list of "PACKAGE_NAME=CATEGORY".
            4. Do NOT output JSON. Do NOT include quotes. Do NOT add any conversational text.
            
            Example of EXPECTED output format:
            com.facebook.katana=Social Media
            com.spotify.music=Music Players
            org.telegram.messenger=Messaging & SMS
        """.trimIndent()

        val response = model.generateContent(prompt)
        return response.text ?: throw IllegalStateException("Empty response from Gemini")
    }

    override suspend fun isAvailable(): Boolean {
        return !repository.getGeminiApiKey().isNullOrBlank()
    }

    override fun getEngineName(): String {
        return "Gemini 1.5 Flash (Cloud)"
    }
}
