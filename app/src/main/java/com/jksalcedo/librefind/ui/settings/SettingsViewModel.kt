package com.jksalcedo.librefind.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jksalcedo.librefind.data.local.PreferencesManager
import com.jksalcedo.librefind.domain.model.AppUpdate
import com.jksalcedo.librefind.domain.repository.AuthRepository
import com.jksalcedo.librefind.domain.repository.CacheRepository
import com.jksalcedo.librefind.domain.repository.UpdateRepository
import com.jksalcedo.librefind.ui.dashboard.components.AppIconCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class UpdateCheckStatus {
    IDLE, CHECKING, UPDATE_AVAILABLE, UP_TO_DATE, ERROR
}

data class SettingsState(
    val cacheSizeMB: String = "0.0 MB",
    val classificationCacheCount: Int = 0,
    val isClearing: Boolean = false,
    val isClearingClassification: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val showClearClassificationConfirmation: Boolean = false,
    val showDeleteAccountConfirmation: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val isAccountDeleted: Boolean = false,
    val deleteAccountError: String? = null,
    val updateCheckStatus: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val latestUpdate: AppUpdate? = null,
    val updateError: String? = null,
    val includePrereleases: Boolean = false,
    val autoUpdateEnabled: Boolean = true,
    val isLoggedIn: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notificationIntervalMins: Long = 60L,
    val networkConsentGranted: Boolean = false
)

class SettingsViewModel(
    private val appContext: android.content.Context,
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val updateRepository: UpdateRepository,
    private val cacheRepository: CacheRepository,
    private val notificationPrefs: com.jksalcedo.librefind.data.local.NotificationPrefsDataStore,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        calculateCacheSize()
        observePreferences()
        calculateClassificationCacheCount()
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _state.update { it.copy(isLoggedIn = user != null) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesManager.observeIncludePrereleases().collect { enabled ->
                _state.update { it.copy(includePrereleases = enabled) }
            }
        }
        viewModelScope.launch {
            preferencesManager.observeNetworkConsentGranted().collect { granted ->
                _state.update { it.copy(networkConsentGranted = granted) }
            }
        }
        viewModelScope.launch {
            preferencesManager.observeAutoUpdateEnabled().collect { enabled ->
                _state.update { it.copy(autoUpdateEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPrefs.notificationsEnabledFlow.collect { enabled ->
                _state.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPrefs.notificationIntervalFlow.collect { interval ->
                _state.update { it.copy(notificationIntervalMins = interval) }
            }
        }
    }

    fun setIncludePrereleases(enabled: Boolean) {
        preferencesManager.setIncludePrereleases(enabled)
    }

    fun setNetworkConsentGranted(granted: Boolean) {
        preferencesManager.setNetworkConsentGranted(granted)
        
        // Handle worker scheduling based on consent
        val workManager = androidx.work.WorkManager.getInstance(appContext)
        if (granted) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val signerRequest = androidx.work.PeriodicWorkRequestBuilder<com.jksalcedo.librefind.worker.SignerFeedWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork("signer_feed_update", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, signerRequest)
            
            if (preferencesManager.getAutoUpdateEnabled()) {
                val updateRequest = androidx.work.PeriodicWorkRequestBuilder<com.jksalcedo.librefind.worker.UpdateCheckWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniquePeriodicWork("app_update_check", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, updateRequest)
            }
            
            rescheduleNotificationWorker()
        } else {
            workManager.cancelUniqueWork("signer_feed_update")
            workManager.cancelUniqueWork("app_update_check")
            workManager.cancelUniqueWork("notification_check")
        }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        preferencesManager.setAutoUpdateEnabled(enabled)
        val workManager = androidx.work.WorkManager.getInstance(appContext)
        if (enabled && preferencesManager.getNetworkConsentGranted()) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val updateRequest = androidx.work.PeriodicWorkRequestBuilder<com.jksalcedo.librefind.worker.UpdateCheckWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork("app_update_check", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, updateRequest)
        } else {
            workManager.cancelUniqueWork("app_update_check")
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPrefs.setNotificationsEnabled(enabled)
            rescheduleNotificationWorker()
        }
    }

    fun setNotificationInterval(minutes: Long) {
        viewModelScope.launch {
            notificationPrefs.setNotificationInterval(minutes)
            rescheduleNotificationWorker()
        }
    }

    private fun rescheduleNotificationWorker() {
        val interval = _state.value.notificationIntervalMins
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val workRequest =
            androidx.work.PeriodicWorkRequestBuilder<com.jksalcedo.librefind.worker.NotificationWorker>(
                interval,
                java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
        androidx.work.WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "notification_check",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val sizeBytes = withContext(ioDispatcher) {
                AppIconCache.getCacheSize()
            }
            val sizeMB = sizeBytes / (1024.0 * 1024.0)
            _state.update {
                it.copy(
                    cacheSizeMB = String.format(
                        Locale.getDefault(),
                        "%.2f MB",
                        sizeMB
                    )
                )
            }
        }
    }

    fun calculateClassificationCacheCount() {
        viewModelScope.launch {
            val count = cacheRepository.getTotalCachedItems()
            _state.update { it.copy(classificationCacheCount = count) }
        }
    }

    fun showClearConfirmation() {
        _state.update { it.copy(showClearConfirmation = true) }
    }

    fun hideClearConfirmation() {
        _state.update { it.copy(showClearConfirmation = false) }
    }

    fun clearCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearing = true, showClearConfirmation = false) }
            withContext(ioDispatcher) {
                AppIconCache.clearCache()
            }
            calculateCacheSize()
            _state.update { it.copy(isClearing = false) }
        }
    }

    fun showClearClassificationConfirmation() {
        _state.update { it.copy(showClearClassificationConfirmation = true) }
    }

    fun hideClearClassificationConfirmation() {
        _state.update { it.copy(showClearClassificationConfirmation = false) }
    }

    fun clearClassificationCache() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isClearingClassification = true,
                    showClearClassificationConfirmation = false
                )
            }
            cacheRepository.clearCache()
            calculateClassificationCacheCount()
            _state.update { it.copy(isClearingClassification = false) }
        }
    }

    // --- Account Deletion ---

    fun showDeleteAccountConfirmation() {
        _state.update { it.copy(showDeleteAccountConfirmation = true) }
    }

    fun hideDeleteAccountConfirmation() {
        _state.update { it.copy(showDeleteAccountConfirmation = false) }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isDeletingAccount = true,
                    showDeleteAccountConfirmation = false
                )
            }
            authRepository.deleteAccount()
                .onSuccess {
                    _state.update { it.copy(isDeletingAccount = false, isAccountDeleted = true) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isDeletingAccount = false,
                            deleteAccountError = e.message ?: "Failed to delete account"
                        )
                    }
                }
        }
    }

    fun clearDeleteAccountError() {
        _state.update { it.copy(deleteAccountError = null) }
    }

    // --- Auto-Updater ---

    fun checkForUpdates() {
        viewModelScope.launch {
            _state.update { it.copy(updateCheckStatus = UpdateCheckStatus.CHECKING) }
            updateRepository.checkForUpdate()
                .onSuccess { update ->
                    _state.update {
                        it.copy(
                            updateCheckStatus = if (update.isUpdateAvailable) UpdateCheckStatus.UPDATE_AVAILABLE else UpdateCheckStatus.UP_TO_DATE,
                            latestUpdate = update
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            updateCheckStatus = UpdateCheckStatus.ERROR,
                            updateError = error.message ?: "Failed to check for updates"
                        )
                    }
                }
        }
    }

    fun downloadUpdate() {
        val update = _state.value.latestUpdate ?: return
        updateRepository.downloadUpdate(update.downloadUrl, update.fileName)
        resetUpdateStatus()
    }

    fun resetUpdateStatus() {
        _state.update {
            it.copy(
                updateCheckStatus = UpdateCheckStatus.IDLE,
                latestUpdate = null,
                updateError = null
            )
        }
    }
}