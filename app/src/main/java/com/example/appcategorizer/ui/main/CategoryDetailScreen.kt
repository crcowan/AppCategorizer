package com.example.appcategorizer.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.data.CategoryTaxonomyEntity
import com.example.appcategorizer.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    parentCategory: String,
    onBack: () -> Unit,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val settingsViewModel: SettingsViewModel = viewModel()
    val zoomLevel by settingsViewModel.zoomLevel.collectAsState(initial = "Medium")
    var gridColumns by remember(zoomLevel) {
        mutableStateOf(
            when (zoomLevel) {
                "Small" -> 5
                "Large" -> 3
                else -> 4
            }
        )
    }

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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var currentZoom = 1f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                currentZoom *= zoom
                                if (currentZoom > 1.2f) {
                                    gridColumns = max(2, gridColumns - 1)
                                    currentZoom = 1f
                                } else if (currentZoom < 0.8f) {
                                    gridColumns = min(7, gridColumns + 1)
                                    currentZoom = 1f
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val sortedCategories = groupedMap.entries.sortedBy { it.key }
                            sortedCategories.forEach { (categoryName, apps) ->
                                item(span = { GridItemSpan(maxLineSpan) }, key = "header_$categoryName") {
                                    Text(
                                        text = categoryName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                
                                items(apps.sortedBy { it.name }, key = { it.packageName }) { app ->
                                    com.example.appcategorizer.ui.main.components.AppGridItem(
                                        app = app
                                    )
                                }
                            }
                        }
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
