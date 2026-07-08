package com.example.appcategorizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_taxonomy")
data class CategoryTaxonomyEntity(
    @PrimaryKey val categoryName: String,
    val parentCategory: String? // For grouping (e.g., "Social Media" -> "Communication")
)
