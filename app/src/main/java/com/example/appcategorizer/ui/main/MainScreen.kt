package com.example.appcategorizer.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable


import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.withStyle
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.data.CategoryTaxonomyEntity
import com.example.appcategorizer.ui.settings.SettingsViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToCategory: (String) -> Unit
) {
    // Settings view model for theme/zoom preferences
    val settingsViewModel: SettingsViewModel = viewModel()
    val zoomLevel by settingsViewModel.zoomLevel.collectAsState(initial = "Medium")
    val scaleFactor = when (zoomLevel) {
        "Small" -> 0.8f
        "Large" -> 1.2f
        else -> 1f
    }
    var pinchScale by remember { mutableStateOf(1f) }


    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGroupingEnabled by viewModel.isGroupingEnabled.collectAsState()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                pinchScale = (pinchScale * zoom).coerceIn(0.5f, 2f)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
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
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        Button(onClick = { viewModel.loadApps() }) {
                            Text("Retry")
                        }
                    }
                }
                is MainScreenUiState.Success -> {
                    if (searchQuery.isNotBlank()) {
                        val searchResults = viewModel.getSearchResults(searchQuery, state.apps)
                        SearchResultsDrawer(results = searchResults, query = searchQuery)
                    } else {
                        if (isGroupingEnabled) {
                            // Render Folders
                            val parentMap = state.taxonomy.associate { it.categoryName to it.parentCategory }
                            val folders = mutableMapOf<String, Int>() // Parent Category -> App Count
                            for (app in state.apps) {
                                val cat = app.llmCategory ?: "Misc"
                                val parent = parentMap[cat] ?: "Other"
                                folders[parent] = folders.getOrDefault(parent, 0) + 1
                            }
                            FolderDrawer(folders = folders, onNavigateToCategory = onNavigateToCategory)
                        } else {
                            // Render flat categories
                            val finalMap = mutableMapOf<String, MutableList<AppInfo>>()
                            for (app in state.apps) {
                                val cat = app.llmCategory ?: "Misc"
                                finalMap.getOrPut(cat) { mutableListOf() }.add(app)
                            }
                            CategorizedDrawer(categories = finalMap, scaleFactor = scaleFactor, pinchScale = pinchScale)
                        }
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
                        val icon = when (parentCategory) {
                            "Communication" -> Icons.Default.Phone
                            "Media" -> Icons.Default.PlayArrow
                            "Information" -> Icons.Default.Info
                            "Work & Data" -> Icons.Default.Build
                            "Travel" -> Icons.Default.Place
                            "System" -> Icons.Default.Settings
                            "Hardware" -> Icons.Default.Phone
                            "Daily Life" -> Icons.Default.ShoppingCart
                            "Other" -> Icons.Default.List
                            else -> Icons.Default.Folder
                        }
                        Icon(
                            imageVector = icon,
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
fun CategorizedDrawer(categories: Map<String, List<AppInfo>>, scaleFactor: Float, pinchScale: Float) {
    val context = LocalContext.current
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

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
                        Box {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(80.dp)
                                    .combinedClickable(
                                        onClick = {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        },
                                        onLongClick = {
                                            selectedApp = app
                                            menuExpanded = true
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                app.icon?.let { drawable ->
                                    Image(
                                        bitmap = drawable.toBitmap().asImageBitmap(),
                                        contentDescription = app.name,
                                        modifier = Modifier.size((56f * scaleFactor * pinchScale).dp)
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
                            
                            // Contextual menu for long‑press on app icons
                            DropdownMenu(
                                expanded = menuExpanded && selectedApp == app,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                val description = app.shortDescription ?: app.fullDescription
                                if (!description.isNullOrEmpty()) {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .widthIn(max = 240.dp),
                                        maxLines = 4,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text("App Info") },
                                    onClick = {
                                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        }
                                        context.startActivity(intent)
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Uninstall") },
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DELETE).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        }
                                        context.startActivity(intent)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsDrawer(results: List<SearchResult>, query: String) {
    val context = LocalContext.current

    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results found.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(results) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(result.app.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    result.app.icon?.let { drawable ->
                        Image(
                            bitmap = drawable.toBitmap().asImageBitmap(),
                            contentDescription = result.app.name,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = result.app.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (result.matchSnippet != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val qLower = query.lowercase()
                            val sLower = result.matchSnippet.lowercase()
                            val idx = sLower.indexOf(qLower)
                            
                            if (idx >= 0) {
                                val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                                    append(result.matchSnippet.substring(0, idx))
                                    withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)) {
                                        append(result.matchSnippet.substring(idx, idx + query.length))
                                    }
                                    append(result.matchSnippet.substring(idx + query.length))
                                }
                                Text(
                                    text = annotatedString,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = result.matchSnippet,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

