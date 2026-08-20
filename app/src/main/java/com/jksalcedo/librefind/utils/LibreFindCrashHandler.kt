package com.jksalcedo.librefind.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.jksalcedo.librefind.data.local.PreferencesManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LibreFindCrashHandler(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "LibreFindCrashHandler"
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                .format(Date())

            val packageInfo: PackageInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            } catch (_: Exception) {
                PackageInfo().apply {
                    packageName = context.packageName
                    versionName = "Unknown"
                }
            }

            val contentToWrite = """
                $timestamp
                ${packageInfo.packageName} ${packageInfo.versionName}
                ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
                Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})

                ${Log.getStackTraceString(e)}
            """.trimIndent()

            val fileName = "LF_Crash_$timestamp.txt"
            val crashLocationUri = preferencesManager.getLogsLocation()

            if (crashLocationUri != null) {
                try {
                    val treeUri = crashLocationUri.toUri()
                    val pickedDir = DocumentFile.fromTreeUri(context, treeUri)

                    if (pickedDir != null && pickedDir.canWrite()) {
                        val newFile = pickedDir.createFile("text/plain", fileName)
                        if (newFile != null) {
                            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                                outputStream.write(contentToWrite.toByteArray())
                            }
                        } else {
                            Log.e(TAG, "Failed to create crash log file")
                        }
                    } else {
                        Log.e(TAG, "Custom crash logs location is not accessible or writable")
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error writing crash log to custom location", ex)
                }
            } else {
                try {
                    val crashLogDir = File(context.getExternalFilesDir(null), "logs")
                    if (!crashLogDir.exists()) {
                        crashLogDir.mkdirs()
                    }
                    val crashLogFile = File(crashLogDir, fileName)
                    crashLogFile.writeText(contentToWrite)
                } catch (ex: Exception) {
                    Log.e(TAG, "Error writing crash log to internal storage", ex)
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error in crash handler", ex)
        } finally {
            defaultHandler?.uncaughtException(t, e)
        }
    }
}
