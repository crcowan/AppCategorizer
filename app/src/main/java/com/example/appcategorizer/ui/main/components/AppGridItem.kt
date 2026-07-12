package com.example.appcategorizer.ui.main.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.appcategorizer.data.AppInfo

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppGridItem(
    app: AppInfo
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuExpanded = true
                    }
                )
                .padding(8.dp)
        ) {
            app.icon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap().asImageBitmap(),
                    contentDescription = app.name,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
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
            expanded = menuExpanded,
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
