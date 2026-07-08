package com.example.appcategorizer.data

interface CategorizationEngine {
    /**
     * Categorizes a batch of apps.
     * Returns a string in the format of "PACKAGE_NAME=CATEGORY" separated by newlines.
     * Throws an Exception if categorization fails.
     */
    suspend fun categorizeBatch(apps: List<AppInfo>, customCategories: List<String>): String
    
    /**
     * Optional method for the engine to initialize itself or check availability.
     * For MediaPipe, it might check if the model file exists.
     * For Gemini, it might check if the API key is present.
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * A human-readable name for the engine, e.g., "MediaPipe (On-Device)" or "Gemini (Cloud)".
     */
    fun getEngineName(): String
}
