package com.example.appcategorizer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import android.util.Log

class PlayStoreService(private val appDao: AppDao) {

    suspend fun getAppMetadata(packageName: String): AppMetadataEntity? = withContext(Dispatchers.IO) {
        // First check the database cache
        val cached = appDao.getAppMetadata(packageName)
        if (cached != null) {
            return@withContext cached
        }

        // If not in cache, try to scrape it
        try {
            val url = "https://play.google.com/store/apps/details?id=$packageName"
            val document = Jsoup.connect(url).timeout(5000).get()

            // Best-effort scraping for Play Store
            // The structure changes frequently, this is a basic extraction attempt
            var category: String? = null
            var shortDescription: String? = null

            // Example: <meta name="description" content="Spotify description...">
            shortDescription = document.select("meta[name=description]").attr("content")

            // Categories are often in links like href="/store/apps/category/GAME_ACTION"
            val categoryElement = document.select("a[href^=/store/apps/category/]").first()
            if (categoryElement != null) {
                category = categoryElement.text()
            }

            val metadata = AppMetadataEntity(
                packageName = packageName,
                playStoreCategory = category,
                shortDescription = shortDescription,
                reviewSnippet = null, // Parsing reviews requires complex JS rendering in modern Play Store
                lastUpdated = System.currentTimeMillis()
            )

            // Save to database
            appDao.insertMetadata(metadata)
            return@withContext metadata
        } catch (e: Exception) {
            Log.e("PlayStoreService", "Failed to fetch metadata for $packageName: ${e.message}")
            // Return a stub so we don't keep failing repeatedly
            val stub = AppMetadataEntity(
                packageName = packageName,
                playStoreCategory = null,
                shortDescription = null,
                reviewSnippet = null,
                lastUpdated = System.currentTimeMillis()
            )
            appDao.insertMetadata(stub)
            return@withContext stub
        }
    }
}
