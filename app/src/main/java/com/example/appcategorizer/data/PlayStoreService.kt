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

            // Try standard meta first
            shortDescription = document.select("meta[name=description]").attr("content")
            if (shortDescription.isNullOrEmpty()) {
                shortDescription = document.select("meta[property=og:description]").attr("content")
            }
            if (shortDescription.isNullOrEmpty()) {
                shortDescription = document.select("meta[itemprop=description]").attr("content")
            }

            // Categories are often in links like href="/store/apps/category/GAME_ACTION"
            // However, global navigation tabs also match this (e.g. "Kids" tab). 
            // We should prefer itemprop="genre" first.
            val genreElement = document.select("[itemprop=genre]").first()
            if (genreElement != null) {
                category = genreElement.text()
            } else {
                // Fallback, but try to avoid the top nav by looking inside the main container
                val categoryElement = document.select("main a[href^=/store/apps/category/]").first()
                if (categoryElement != null) {
                    category = categoryElement.text()
                }
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
            // Return a stub so we don't keep failing repeatedly in memory, but DO NOT save to DB
            // so that it can be retried in future app sessions.
            return@withContext AppMetadataEntity(
                packageName = packageName,
                playStoreCategory = null,
                shortDescription = null,
                reviewSnippet = null,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
