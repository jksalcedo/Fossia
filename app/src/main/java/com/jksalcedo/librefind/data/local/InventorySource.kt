package com.jksalcedo.librefind.data.local

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Local data source for device app inventory
 *
 * Wraps Android PackageManager API to extract installed packages.
 */
class InventorySource(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    /**
     * Gets all user-installed apps
     *
     * Filters out pure system apps, but keeps:
     * - User-installed apps
     *
     * @return List of PackageInfo for user apps
     */
    fun getRawApps(): List<PackageInfo> {
        return try {
            val pm = context.packageManager

            // Get all launchable apps (including system apps like Calculator, Camera, etc.)
            val launchIntent = Intent(Intent.ACTION_MAIN, null)
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val launchablePackages = pm.queryIntentActivities(launchIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()

            val hideSystemPackages = preferencesManager.shouldHideSystemPackages()

            // Flags including certificates
            val flags = PackageManager.GET_META_DATA or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        @Suppress("DEPRECATION")
                        PackageManager.GET_SIGNATURES
                    }

            pm.getInstalledPackages(flags)
                .filter { app ->
                    val isSystem =
                        (app.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0)
                    val isUpdatedSystem =
                        (app.applicationInfo?.flags?.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)

                    if (hideSystemPackages) {
                        !isSystem || isUpdatedSystem
                    } else {
                        !isSystem || isUpdatedSystem || launchablePackages.contains(app.packageName)
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Gets the installer package name for a specific app
     *
     * Handles API level differences:
     * - Android R (API 30+): Uses getInstallSourceInfo
     * - Pre-R: Uses deprecated getInstallerPackageName
     *
     * @param packageName Package to query
     * @return Installer package name or null if unknown/error
     */
    fun getInstaller(packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager
                    .getInstallSourceInfo(packageName)
                    .installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets human-readable label for an app
     *
     * @param packageName Package to query
     * @return App label or package name if label unavailable
     */
    fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun getLabelFromInfo(appInfo: android.content.pm.ApplicationInfo): String {
        return try {
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            appInfo.packageName
        }
    }
}
