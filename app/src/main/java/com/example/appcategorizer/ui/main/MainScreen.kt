package com.example.appcategorizer.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.data.CategoryTaxonomyEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToCategory: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGroupingEnabled by viewModel.isGroupingEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Drawer") },
                actions = {
                    IconButton(onClick = { viewModel.toggleGrouping(!isGroupingEnabled) }) {
                        if (isGroupingEnabled) {
                            Icon(androidx.compose.material.icons.Icons.Default.Menu, contentDescription = "Flat List")
                        } else {
                            Icon(androidx.compose.material.icons.Icons.Default.Folder, contentDescription = "Group into Folders")
                        }
                    }
                    IconButton(onClick = { viewModel.forceRecategorize() }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = "Recategorize All")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = "Settings")
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            when (val state = uiState) {
                is MainScreenUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = state.message, style = MaterialTheme.typography.bodyLarge)
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
                        Button(onClick = { viewModel.loadApps() }) {
                            Text("Retry")
                        }
                    }
                }
                is MainScreenUiState.Success -> {
                    // Filter apps based on search query
                    val filteredApps = if (searchQuery.isBlank()) {
                        state.apps
                    } else {
                        state.apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    if (isGroupingEnabled) {
                        // Render Folders
                        val parentMap = state.taxonomy.associate { it.categoryName to it.parentCategory }
                        val folders = mutableMapOf<String, Int>() // Parent Category -> App Count
                        for (app in filteredApps) {
                            val cat = app.llmCategory ?: "Misc"
                            val parent = parentMap[cat] ?: "Other"
                            folders[parent] = folders.getOrDefault(parent, 0) + 1
                        }
                        FolderDrawer(folders = folders, onNavigateToCategory = onNavigateToCategory)
                    } else {
                        // Render flat categories
                        val finalMap = mutableMapOf<String, MutableList<AppInfo>>()
                        for (app in filteredApps) {
                            val cat = app.llmCategory ?: "Misc"
                            finalMap.getOrPut(cat) { mutableListOf() }.add(app)
                        }
                        CategorizedDrawer(categories = finalMap)
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDrawer(folders: Map<String, Int>, onNavigateToCategory: (String) -> Unit) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps found.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        val sortedFolders = folders.entries.sortedBy { it.key }
        sortedFolders.forEach { (parentCategory, count) ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onNavigateToCategory(parentCategory) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Folder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = parentCategory,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$count apps",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategorizedDrawer(categories: Map<String, List<AppInfo>>) {
    val context = LocalContext.current

    if (categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps found.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        val sortedCategories = categories.entries.sortedBy { it.key }
        sortedCategories.forEach { (categoryName, apps) ->
            item {
                Text(
                    text = categoryName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    apps.sortedBy { it.name }.forEach { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(80.dp)
                                .clickable {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            app.icon?.let { drawable ->
                                Image(
                                    bitmap = drawable.toBitmap().asImageBitmap(),
                                    contentDescription = app.name,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
