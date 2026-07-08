package com.example.appcategorizer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Finished : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(private val context: Context) {
    private val modelFile = File(context.filesDir, "gemma-2b-it-cpu-int4.bin")

    fun importModelFromUri(uri: Uri, context: Context): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Downloading(0))
            
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val outputStream = FileOutputStream(modelFile)
                
                val totalLength = inputStream.available().toLong()
                var downloaded = 0L
                val buffer = ByteArray(8192)
                var read: Int
                var lastProgress = 0

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    downloaded += read
                    
                    if (totalLength > 0) {
                        val progress = ((downloaded * 100) / totalLength).toInt()
                        if (progress != lastProgress) {
                            emit(DownloadState.Downloading(progress))
                            lastProgress = progress
                        }
                    }
                }
                outputStream.flush()
                outputStream.close()
            }
            emit(DownloadState.Finished)
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown Error"))
            modelFile.delete()
        }
    }.flowOn(Dispatchers.IO)
    
    private val galleryModelFile = File(context.filesDir, "gemma_gallery/gemma4_2b_v09_obfus_fix_all_modalities_thinking.litertlm")

    fun isModelDownloaded(): Boolean {
        if (galleryModelFile.exists() && galleryModelFile.length() > 0) return true
        return modelFile.exists() && modelFile.length() > 0
    }
    
    fun deleteModel() {
        if (modelFile.exists()) {
            modelFile.delete()
        }
        val galleryDir = File(context.filesDir, "gemma_gallery")
        if (galleryDir.exists()) {
            galleryDir.deleteRecursively()
        }
    }
}
