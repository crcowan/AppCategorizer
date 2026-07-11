package com.example.appcategorizer.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.appcategorizer.domain.CategorizeAppsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class CategorizationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val categorizeAppsUseCase: CategorizeAppsUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setProgress(workDataOf(PROGRESS_KEY to "Starting categorization..."))
            
            categorizeAppsUseCase { progress ->
                setProgress(workDataOf(PROGRESS_KEY to progress))
            }
            
            setProgress(workDataOf(PROGRESS_KEY to "Done"))
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("CategorizationWorker", "Error during categorization", e)
            val errorMsg = e.message ?: "Unknown error"
            Result.failure(workDataOf(ERROR_KEY to errorMsg))
        }
    }

    companion object {
        const val PROGRESS_KEY = "progress_msg"
        const val ERROR_KEY = "error_msg"
    }
}
