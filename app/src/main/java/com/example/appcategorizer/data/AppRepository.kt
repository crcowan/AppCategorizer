package com.example.appcategorizer.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.IntentFilter

interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
    fun observeInstalledApps(): Flow<List<AppInfo>>
}

class DefaultAppRepository(private val context: Context) : AppRepository {
    override suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        // Use 0 to only match currently active/installed activities (bypassing uninstalled packages)
        val flags = 0
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, flags)
        
        resolveInfos.map { resolveInfo ->
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                name = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm)
            )
        }.distinctBy { it.packageName }.sortedBy { it.name }
    }

    override fun observeInstalledApps(): Flow<List<AppInfo>> = callbackFlow {
        // Send initial list
        launch { send(getInstalledApps()) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                launch { send(getInstalledApps()) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}
