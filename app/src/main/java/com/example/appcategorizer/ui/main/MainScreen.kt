package com.example.appcategorizer.ui.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.max
import kotlin.math.min
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.ui.main.components.AppGridItem
import com.example.appcategorizer.ui.main.components.FolderItem
import com.example.appcategorizer.ui.main.components.SearchBar
import com.example.appcategorizer.ui.main.components.SearchResultItem
import com.example.appcategorizer.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToCategory: (String) -> Unit
) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val zoomLevel by settingsViewModel.zoomLevel.collectAsStateWithLifecycle(initialValue = "Medium")
    var gridColumns by remember(zoomLevel) {
        mutableStateOf(
            when (zoomLevel) {
                "Small" -> 5
                "Large" -> 3
                else -> 4
            }
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isGroupingEnabled by viewModel.isGroupingEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Drawer") },
                actions = {
                    IconButton(onClick = { viewModel.toggleGrouping(!isGroupingEnabled) }) {
                        if (isGroupingEnabled) {
                            Icon(Icons.Default.Menu, contentDescription = "Flat List")
                        } else {
                            Icon(Icons.Default.Folder, contentDescription = "Group into Folders")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                                    // Zoomed in -> make items bigger -> fewer columns
                                    gridColumns = max(2, gridColumns - 1)
                                    currentZoom = 1f
                                } else if (currentZoom < 0.8f) {
                                    // Zoomed out -> make items smaller -> more columns
                                    gridColumns = min(7, gridColumns + 1)
                                    currentZoom = 1f
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )

            when (val state = uiState) {
                is MainScreenUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )

                        if (state.currentModel != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Active Model: ${state.currentModel}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (state.availableModels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Available Models from Google (${state.availableModels.size}):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .padding(8.dp)
                            ) {
                                items(state.availableModels) { model ->
                                    val isActive = model == state.currentModel
                                    Text(
                                        text = if (isActive) "► $model" else "  $model",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                is MainScreenUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: ${state.throwable.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        // Note: The UI layer no longer directly calls loadApps() since it's driven by flow, 
                        // but we could send an intent to the viewmodel to retry collecting the flow if needed.
                    }
                }
                is MainScreenUiState.Success -> {
                    if (searchQuery.isNotBlank()) {
                        val searchResults = viewModel.getSearchResults(searchQuery, state.apps)
                        if (searchResults.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No results found.") }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .animateContentSize(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                items(searchResults) { result ->
                                    SearchResultItem(result = result, query = searchQuery)
                                }
                            }
                        }
                    } else {
                        if (isGroupingEnabled) {
                            val parentMap = state.taxonomy.associate { it.categoryName to it.parentCategory }
                            val folders = mutableMapOf<String, Int>()
                            for (app in state.apps) {
                                val cat = app.llmCategory ?: "Misc"
                                val parent = parentMap[cat] ?: "Other"
                                folders[parent] = folders.getOrDefault(parent, 0) + 1
                            }
                            if (folders.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No apps found.") }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .animateContentSize(),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    val sortedFolders = folders.entries.sortedBy { it.key }
                                    sortedFolders.forEach { (parentCategory, count) ->
                                        item(key = parentCategory) {
                                            FolderItem(
                                                parentCategory = parentCategory,
                                                count = count,
                                                onNavigateToCategory = onNavigateToCategory
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val finalMap = mutableMapOf<String, MutableList<AppInfo>>()
                            for (app in state.apps) {
                                val cat = app.llmCategory ?: "Misc"
                                finalMap.getOrPut(cat) { mutableListOf() }.add(app)
                            }
                            if (finalMap.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No apps found.") }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(gridColumns),
                                    modifier = Modifier.fillMaxSize().animateContentSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val sortedCategories = finalMap.entries.sortedBy { it.key }
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
                                        
                                        gridItems(apps.sortedBy { it.name }, key = { it.packageName }) { app ->
                                            AppGridItem(app = app)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
