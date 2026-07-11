package com.example.appcategorizer.di

import android.content.Context
import com.example.appcategorizer.data.AppDao
import com.example.appcategorizer.data.AppDatabase
import com.example.appcategorizer.data.CategoryRepository
import com.example.appcategorizer.data.ClaudeCloudEngine
import com.example.appcategorizer.data.GeminiCloudEngine
import com.example.appcategorizer.data.OpenAICloudEngine
import com.example.appcategorizer.data.PlayStoreService
import com.example.appcategorizer.data.DefaultAppRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.work.WorkManager

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideAppDao(database: AppDatabase): AppDao {
        return database.appDao()
    }

    @Provides
    @Singleton
    fun provideCategoryRepository(@ApplicationContext context: Context): CategoryRepository {
        return CategoryRepository(context)
    }

    @Provides
    @Singleton
    fun provideDefaultAppRepository(@ApplicationContext context: Context): DefaultAppRepository {
        return DefaultAppRepository(context)
    }

    @Provides
    @Singleton
    fun providePlayStoreService(appDao: AppDao): PlayStoreService {
        return PlayStoreService(appDao)
    }

    @Provides
    @Singleton
    fun provideGeminiEngine(categoryRepository: CategoryRepository): GeminiCloudEngine {
        return GeminiCloudEngine(categoryRepository)
    }

    @Provides
    @Singleton
    fun provideOpenAIEngine(categoryRepository: CategoryRepository): OpenAICloudEngine {
        return OpenAICloudEngine(categoryRepository)
    }

    @Provides
    @Singleton
    fun provideClaudeEngine(categoryRepository: CategoryRepository): ClaudeCloudEngine {
        return ClaudeCloudEngine(categoryRepository)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
