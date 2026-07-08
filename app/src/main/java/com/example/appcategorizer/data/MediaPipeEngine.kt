package com.example.appcategorizer.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class MediaPipeEngine(private val context: Context) : CategorizationEngine {

    private var llmInference: LlmInference? = null
    
    private val modelPath: String
        get() {
            val galleryFile = File(context.filesDir, "gemma_gallery/gemma4_2b_v09_obfus_fix_all_modalities_thinking.litertlm")
            if (galleryFile.exists()) return galleryFile.absolutePath
            
            return File(context.filesDir, "gemma-2b-it-cpu-int4.bin").absolutePath
        }

    private fun initializeLlmIfNeeded() {
        if (llmInference == null) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
        }
    }

    override suspend fun categorizeBatch(apps: List<AppInfo>, customCategories: List<String>): String {
        if (!isAvailable()) {
            throw IllegalStateException("Model file not found at $modelPath")
        }
        
        initializeLlmIfNeeded()

        val appListString = apps.joinToString(separator = "\n") { app ->
            val desc = app.playStoreCategory ?: "No description provided"
            "- ${app.packageName} (${app.name}) : $desc"
        }

        val categoryListString = customCategories.joinToString(separator = "\n") { "               - \"$it\"" }

        val prompt = """
            <start_of_turn>user
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
            org.telegram.messenger=Messaging & SMS<end_of_turn>
            <start_of_turn>model
            
        """.trimIndent()

        // generateResponse is a blocking call in MediaPipe, so this should ideally run on Dispatchers.IO 
        // which it does in MainScreenViewModel
        return llmInference?.generateResponse(prompt) ?: throw IllegalStateException("LlmInference is null")
    }

    override suspend fun isAvailable(): Boolean {
        return File(modelPath).exists()
    }

    override fun getEngineName(): String {
        return "Gemma 2B (On-Device via MediaPipe)"
    }
}
