package com.example.appcategorizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val packageName: String,
    val playStoreCategory: String?,
    val shortDescription: String?,
    val fullDescription: String?,
    val reviewSnippet: String?,
    val lastUpdated: Long,
    val aiCategory: String? = null
)
