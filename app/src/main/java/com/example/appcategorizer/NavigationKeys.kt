package com.example.appcategorizer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Settings : NavKey
@Serializable data class CategoryDetail(val parentCategory: String) : NavKey
