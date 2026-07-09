package com.example.appcategorizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {
    @Query("SELECT * FROM app_metadata WHERE packageName = :packageName")
    suspend fun getAppMetadata(packageName: String): AppMetadataEntity?

    @Query("SELECT * FROM app_metadata")
    suspend fun getAllAppMetadata(): List<AppMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: AppMetadataEntity)

    @Query("UPDATE app_metadata SET aiCategory = :category WHERE packageName = :packageName")
    suspend fun updateAppCategory(packageName: String, category: String)

    @Query("SELECT * FROM category_taxonomy")
    suspend fun getAllTaxonomy(): List<CategoryTaxonomyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaxonomy(taxonomy: List<CategoryTaxonomyEntity>)

    @Query("DELETE FROM category_taxonomy")
    suspend fun clearTaxonomy()

    @Query("DELETE FROM category_taxonomy WHERE categoryName = :categoryName")
    suspend fun deleteTaxonomyCategory(categoryName: String)

    @Query("UPDATE app_metadata SET aiCategory = :newName WHERE aiCategory = :oldName")
    suspend fun renameAppCategory(oldName: String, newName: String)

    @Query("UPDATE app_metadata SET aiCategory = NULL")
    suspend fun clearAllAppCategories()

    @Query("DELETE FROM app_metadata")
    suspend fun clearAllMetadata()

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun getSettingFlow(key: String): kotlinx.coroutines.flow.Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingsEntity)
}
