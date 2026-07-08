package com.example.appcategorizer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val taxonomy by viewModel.taxonomy.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val openAIApiKey by viewModel.openAIApiKey.collectAsState()
    val claudeApiKey by viewModel.claudeApiKey.collectAsState()
    val enginePreference by viewModel.enginePreference.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<com.example.appcategorizer.data.CategoryTaxonomyEntity?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }
    var parentNameInput by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingCategory == null) "Add Category" else "Edit Category") },
            text = {
                Column {
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        label = { Text("Category Name") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = parentNameInput,
                        onValueChange = { parentNameInput = it },
                        label = { Text("Parent Folder (Optional)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        viewModel.addOrUpdateCategory(
                            oldName = editingCategory?.categoryName,
                            newName = categoryNameInput.trim(),
                            parentCategory = parentNameInput.trim().takeIf { it.isNotBlank() }
                        )
                    }
                    showDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                if (editingCategory != null) {
                    TextButton(onClick = {
                        viewModel.deleteCategory(editingCategory!!.categoryName)
                        showDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingCategory = null
                categoryNameInput = ""
                parentNameInput = ""
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            
            // AI Configuration Section
            Text(
                "AI Engine Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            // Engine Preference Dropdown
            var expanded by remember { mutableStateOf(false) }
            val options = listOf("Gemini", "OpenAI", "Claude")
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = enginePreference,
                    onValueChange = { },
                    label = { Text("Cloud Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                viewModel.setEnginePreference(selectionOption)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Gemini API Key Input
            if (enginePreference == "Gemini") {
                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { viewModel.setGeminiApiKey(it) },
                    label = { Text("Gemini API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else if (enginePreference == "OpenAI") {
                OutlinedTextField(
                    value = openAIApiKey,
                    onValueChange = { viewModel.setOpenAIApiKey(it) },
                    label = { Text("OpenAI API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else if (enginePreference == "Claude") {
                OutlinedTextField(
                    value = claudeApiKey,
                    onValueChange = { viewModel.setClaudeApiKey(it) },
                    label = { Text("Claude API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Taxonomy Header & Reset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Master Category List", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { viewModel.resetToDefault() }) {
                    Text("Reset Defaults")
                }
            }

            // Taxonomy List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(taxonomy) { item ->
                    ListItem(
                        headlineContent = { Text(item.categoryName) },
                        supportingContent = { Text("Parent: ${item.parentCategory ?: "None"}") },
                        trailingContent = {
                            IconButton(onClick = {
                                editingCategory = item
                                categoryNameInput = item.categoryName
                                parentNameInput = item.parentCategory ?: ""
                                showDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
