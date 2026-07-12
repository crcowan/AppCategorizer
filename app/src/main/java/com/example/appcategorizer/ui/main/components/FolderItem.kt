package com.example.appcategorizer.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FolderItem(
    parentCategory: String,
    count: Int,
    onNavigateToCategory: (String) -> Unit
) {
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
                "Other" -> Icons.AutoMirrored.Filled.List
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
