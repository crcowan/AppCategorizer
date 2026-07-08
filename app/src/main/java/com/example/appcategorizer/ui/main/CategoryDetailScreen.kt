package com.example.appcategorizer.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.data.CategoryTaxonomyEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    parentCategory: String,
    onBack: () -> Unit,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(parentCategory) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is MainScreenUiState.Success -> {
                    // Filter apps to ONLY those in this parent category
                    val parentMap = state.taxonomy.associate { it.categoryName to it.parentCategory }
                    
                    val appsInParent = state.apps.filter { app ->
                        val cat = app.llmCategory ?: "Misc"
                        val parent = parentMap[cat] ?: "Other"
                        parent == parentCategory
                    }
                    
                    // Group them by their granular subcategories
                    val groupedMap = mutableMapOf<String, MutableList<AppInfo>>()
                    for (app in appsInParent) {
                        val cat = app.llmCategory ?: "Misc"
                        groupedMap.getOrPut(cat) { mutableListOf() }.add(app)
                    }
                    
                    if (groupedMap.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No apps in this folder.")
                        }
                    } else {
                        // We can reuse the CategorizedDrawer we built in MainScreen.kt!
                        CategorizedDrawer(categories = groupedMap)
                    }
                }
                else -> {
                    // If state is not success, just show loading or error
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
