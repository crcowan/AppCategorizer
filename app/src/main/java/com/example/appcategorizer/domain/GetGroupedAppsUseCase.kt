package com.example.appcategorizer.domain

import com.example.appcategorizer.data.AppDatabase
import com.example.appcategorizer.data.AppInfo
import com.example.appcategorizer.data.CategoryRepository
import com.example.appcategorizer.data.DefaultAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetGroupedAppsUseCase @Inject constructor(
    private val appRepository: DefaultAppRepository,
    private val database: AppDatabase,
    private val categoryRepo: CategoryRepository
) {

    suspend operator fun invoke(isGroupingEnabled: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        val installedApps = appRepository.getInstalledApps()
        val dbMetadata = database.appDao().getAllAppMetadata()
        val dbMap = dbMetadata.associateBy { it.packageName }
        
        for (app in installedApps) {
            val meta = dbMap[app.packageName]
            if (meta?.aiCategory != null) {
                app.playStoreCategory = meta.playStoreCategory
                app.shortDescription = meta.shortDescription
                app.fullDescription = meta.fullDescription
                app.reviewSnippet = meta.reviewSnippet
                app.llmCategory = meta.aiCategory
            }
        }

        if (!isGroupingEnabled) {
            return@withContext installedApps.sortedBy { it.name }
        }

        val taxonomy = categoryRepo.getTaxonomy()
        
        // Map taxonomy categories to their parent categories
        val subCategoryToParent = mutableMapOf<String, String?>()
        for (category in taxonomy) {
            subCategoryToParent[category.categoryName] = category.parentCategory
        }

        // Apply parent categories to apps for grouping
        for (app in installedApps) {
            val subCategory = app.llmCategory ?: ""
            app.parentCategory = subCategoryToParent[subCategory] ?: "Other"
        }

        return@withContext installedApps
    }
}
