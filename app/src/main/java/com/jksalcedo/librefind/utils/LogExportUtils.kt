package com.jksalcedo.librefind.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.jksalcedo.librefind.R
import com.jksalcedo.librefind.data.local.PreferencesManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogExportUtils {

    fun generateAppDiagnostics(context: Context): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

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

        val prefs = PreferencesManager(context)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo().apply {
            actManager?.getMemoryInfo(this)
        }

        val logcatContent = captureAppLogcat()

        return """
            ========================================
            LIBREFIND DIAGNOSTIC LOG
            ========================================
            Generated At: $timestamp
            
            [APPLICATION INFO]
            Package Name: ${packageInfo.packageName}
            Version Name: ${packageInfo.versionName}
            Version Code: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode}
            
            [DEVICE INFO]
            Manufacturer: ${Build.MANUFACTURER}
            Brand: ${Build.BRAND}
            Model: ${Build.MODEL}
            Device: ${Build.DEVICE}
            Hardware: ${Build.HARDWARE}
            Product: ${Build.PRODUCT}
            Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            Build Display ID: ${Build.DISPLAY}
            
            [SYSTEM MEMORY]
            Available RAM: ${memInfo.availMem / (1024 * 1024)} MB
            Total RAM: ${memInfo.totalMem / (1024 * 1024)} MB
            Low Memory State: ${memInfo.lowMemory}
            
            [APPLICATION PREFERENCES]
            Hide System Packages: ${prefs.shouldHideSystemPackages()}
            Network Consent Granted: ${prefs.getNetworkConsentGranted()}
            Auto Update Enabled: ${prefs.getAutoUpdateEnabled()}
            Include Pre-releases: ${prefs.shouldIncludePrereleases()}
            
            ========================================
            RECENT APP LOGS (LOGCAT)
            ========================================
            $logcatContent
        """.trimIndent()
    }

    private fun captureAppLogcat(): String {
        val pid = Process.myPid().toString()
        val logBuilder = StringBuilder()

        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var count = 0
            val maxLines = 1000

            while (reader.readLine().also { line = it } != null && count < maxLines) {
                val currentLine = line ?: continue
                if (currentLine.contains(pid) ||
                    currentLine.contains("LibreFind", ignoreCase = true) ||
                    currentLine.contains("DeviceInventory", ignoreCase = true) ||
                    currentLine.contains("Supabase", ignoreCase = true)
                ) {
                    logBuilder.append(currentLine).append("\n")
                    count++
                }
            }
        } catch (e: Exception) {
            logBuilder.append("Failed to capture logcat: ${e.message}\n")
        }

        return if (logBuilder.isEmpty()) {
            "No logcat entries captured."
        } else {
            logBuilder.toString()
        }
    }

    fun exportCurrentAppLog(context: Context, customLocationUriString: String?): File? {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "LF_AppLog_$timestamp.txt"
        val content = generateAppDiagnostics(context)

        if (customLocationUriString != null) {
            try {
                val treeUri = customLocationUriString.toUri()
                val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                if (pickedDir != null && pickedDir.canWrite()) {
                    val newFile = pickedDir.createFile("text/plain", fileName)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            output.write(content.toByteArray())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Always save a local copy in cache for immediate sharing
        val cacheFile = File(context.cacheDir, fileName)
        try {
            cacheFile.writeText(content)
            return cacheFile
        } catch (_: Exception) {
            return null
        }
    }

    fun shareCurrentAppLog(context: Context, customLocationUriString: String?) {
        val logFile = exportCurrentAppLog(context, customLocationUriString)
        if (logFile != null && logFile.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LibreFind App Diagnostic Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.pref_export_app_logs_title)))
        } else {
            Toast.makeText(context, context.getString(R.string.logs_none_found), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAllLogs(context: Context, customLocationUriString: String?) {
        val filesToZip = mutableListOf<File>()

        if (customLocationUriString != null) {
            try {
                val treeUri = customLocationUriString.toUri()
                val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                pickedDir?.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name != null && (name.startsWith("LF_") || name.startsWith("PV_")) && name.endsWith(".txt")) {
                        val cacheFile = File(context.cacheDir, name)
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        filesToZip.add(cacheFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Collect from local logs & crash_logs folders
        listOf("logs", "crash_logs").forEach { dirName ->
            val dir = File(context.getExternalFilesDir(null), dirName)
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".txt")) {
                        filesToZip.add(file)
                    }
                }
            }
        }

        // Also ensure current diagnostic log is included if no logs exist yet
        if (filesToZip.isEmpty()) {
            exportCurrentAppLog(context, customLocationUriString)?.let {
                filesToZip.add(it)
            }
        }

        if (filesToZip.isNotEmpty()) {
            val zipFile = File(context.cacheDir, "librefind_logs.zip")
            if (zipFiles(filesToZip, zipFile)) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "LibreFind Logs & Diagnostics")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.pref_share_all_logs_title)))
            } else {
                Toast.makeText(context, context.getString(R.string.logs_zip_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.logs_none_found), Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllLogs(context: Context, customLocationUriString: String?): Boolean {
        var cleared = false

        if (customLocationUriString != null) {
            try {
                val treeUri = customLocationUriString.toUri()
                val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                pickedDir?.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name != null && (name.startsWith("LF_") || name.startsWith("PV_")) && name.endsWith(".txt")) {
                        file.delete()
                        cleared = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        listOf("logs", "crash_logs").forEach { dirName ->
            val dir = File(context.getExternalFilesDir(null), dirName)
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".txt")) {
                        file.delete()
                        cleared = true
                    }
                }
            }
        }

        Toast.makeText(context, context.getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
        return cleared
    }

    fun zipFiles(files: List<File>, zipFile: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                val data = ByteArray(1024)
                for (file in files) {
                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi, 1024).use { origin ->
                            val entry = ZipEntry(file.name)
                            out.putNextEntry(entry)
                            var count: Int
                            while (origin.read(data, 0, 1024).also { count = it } != -1) {
                                out.write(data, 0, count)
                            }
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
