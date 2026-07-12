package com.example.appcategorizer.ui.main.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.appcategorizer.ui.main.SearchResult

@Composable
fun SearchResultItem(
    result: SearchResult,
    query: String
) {
    val context = LocalContext.current

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
