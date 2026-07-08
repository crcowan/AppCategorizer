package com.example.appcategorizer.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
}

class DefaultAppRepository(private val context: Context) : AppRepository {
    override suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        // MATCH_ALL ensures we bypass filtering and get all launcher activities
        val flags = PackageManager.MATCH_ALL
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, flags)
        
        resolveInfos.map { resolveInfo ->
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                name = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm)
            )
        }.distinctBy { it.packageName }.sortedBy { it.name }
    }
}
