package com.example.appcategorizer

import org.jsoup.Jsoup
import org.junit.Test
import org.junit.Assert.assertNotNull

class PlayStoreTest {
    @Test
    fun testScrape() {
        val url = "https://play.google.com/store/apps/details?id=com.maxmpz.audioplayer"
        val document = Jsoup.connect(url).timeout(10000).get()
        
        var shortDescription = document.select("meta[name=description]").attr("content")
        println("shortDescription from meta: " + shortDescription)
        
        val categoryElement = document.select("a[href^=/store/apps/category/]").first()
        println("category from link: " + categoryElement?.text())
        
        // Try other ways to get description if meta is empty
        var ogDescription = ""
        var itemPropDescription = ""
        var appName = ""
        
        if (shortDescription.isNullOrEmpty()) {
            ogDescription = document.select("meta[property=og:description]").attr("content")
            
            // Modern PlayStore puts description inside <meta itemprop="description">
            itemPropDescription = document.select("meta[itemprop=description]").attr("content")
            
            appName = document.select("meta[property=og:title]").attr("content")
        }
        
        throw Exception("Results:\nshortDescription: $shortDescription\ncategory: ${categoryElement?.text()}\nogDescription: $ogDescription\nitemPropDescription: $itemPropDescription\nappName: $appName")
    }
}
