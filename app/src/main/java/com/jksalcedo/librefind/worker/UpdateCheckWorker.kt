package com.jksalcedo.librefind.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jksalcedo.librefind.R
import com.jksalcedo.librefind.domain.repository.UpdateRepository
import com.jksalcedo.librefind.utils.NotificationHelper
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val updateRepository: UpdateRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            val result = updateRepository.checkForUpdate()
            if (result.isSuccess) {
                val update = result.getOrNull()
                if (update != null && update.isUpdateAvailable) {
                    NotificationHelper.createNotificationChannel(applicationContext)
                    NotificationHelper.showNotification(
                        context = applicationContext,
                        notificationId = 3001,
                        title = applicationContext.getString(R.string.update_available_title),
                        content = applicationContext.getString(R.string.update_available_content, update.version)
                    )
                }
            }
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
