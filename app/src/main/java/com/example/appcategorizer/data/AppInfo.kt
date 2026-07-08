package com.example.appcategorizer.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    var playStoreCategory: String? = null,
    var llmCategory: String? = null
)
