package com.jksalcedo.librefind

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jksalcedo.librefind.di.appModule
import com.jksalcedo.librefind.di.networkModule
import com.jksalcedo.librefind.di.repositoryModule
import com.jksalcedo.librefind.di.supabaseModule
import com.jksalcedo.librefind.di.useCaseModule
import com.jksalcedo.librefind.di.viewModelModule
import com.jksalcedo.librefind.worker.NotificationWorker
import com.jksalcedo.librefind.worker.SignerFeedWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class LibreFindApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@LibreFindApp)
            modules(
                appModule,
                networkModule,
                repositoryModule,
                useCaseModule,
                viewModelModule,
                supabaseModule
            )
        }

        val prefs = com.jksalcedo.librefind.data.local.PreferencesManager(this)
        
        if (prefs.getNetworkConsentGranted()) {
            scheduleSignerFeedUpdate()
            scheduleNotificationWorker()
            
            if (prefs.getAutoUpdateEnabled()) {
                scheduleAutoUpdateWorker()
            } else {
                WorkManager.getInstance(this).cancelUniqueWork("app_update_check")
            }
        } else {
            WorkManager.getInstance(this).cancelUniqueWork("signer_feed_update")
            WorkManager.getInstance(this).cancelUniqueWork("notification_check")
            WorkManager.getInstance(this).cancelUniqueWork("app_update_check")
        }
    }

    private fun scheduleAutoUpdateWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<com.jksalcedo.librefind.worker.UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "app_update_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun scheduleSignerFeedUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SignerFeedWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "signer_feed_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun scheduleNotificationWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notification_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
