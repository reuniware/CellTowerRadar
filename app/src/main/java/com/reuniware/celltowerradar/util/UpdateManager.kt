package com.reuniware.celltowerradar.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val GITHUB_API_URL = "https://api.github.com/repos/reuniware/CellTowerRadar/releases/latest"
    private var downloadId: Long = -1L
    private var updateReceiver: BroadcastReceiver? = null

    data class UpdateInfo(val tagName: String, val downloadUrl: String)

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("UpdateManager", "Checking for updates. Current version: $currentVersion")
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestTag = json.getString("tag_name")
                    
                    android.util.Log.d("UpdateManager", "Latest tag on GitHub: $latestTag")
                    
                    if (latestTag != currentVersion) {
                        val assets = json.getJSONArray("assets")
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                android.util.Log.d("UpdateManager", "Update found: $latestTag")
                                return@withContext UpdateInfo(
                                    tagName = latestTag,
                                    downloadUrl = asset.getString("browser_download_url")
                                )
                            }
                        }
                    }
                } else {
                    android.util.Log.e("UpdateManager", "GitHub API returned code: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateManager", "Check for updates failed", e)
            }
            null
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        try {
            android.util.Log.d("UpdateManager", "Starting download from: $apkUrl")
            
            // Use External Public Directory to ensure the system installer can access it more easily
            val fileName = "CellTowerRadar_Update.apk"
            val oldFile = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (oldFile.exists()) {
                android.util.Log.d("UpdateManager", "Deleting old update file")
                oldFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("CellTowerRadar Update")
                .setDescription("Downloading latest tactical version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            android.util.Log.d("UpdateManager", "Download enqueued with ID: $downloadId")

            // Cleanup old receiver if any
            updateReceiver?.let { try { context.unregisterReceiver(it) } catch(e: Exception) {} }

            updateReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        android.util.Log.d("UpdateManager", "Download complete event received")
                        queryStatus(downloadManager, id)
                    }
                }
            }
            
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(updateReceiver, filter)
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Download initiation failed", e)
        }
    }

    private fun queryStatus(dm: DownloadManager, id: Long) {
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = dm.query(query)
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)
            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val reason = cursor.getInt(reasonIndex)
            
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                android.util.Log.d("UpdateManager", "Download verified as SUCCESSFUL")
                installApk()
            } else {
                android.util.Log.e("UpdateManager", "Download failed status: $status, reason: $reason")
            }
        }
        cursor.close()
    }

    private fun installApk() {
        try {
            val fileName = "CellTowerRadar_Update.apk"
            val file = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            if (!file.exists()) {
                android.util.Log.e("UpdateManager", "APK file NOT FOUND at: ${file.absolutePath}")
                return
            }

            android.util.Log.d("UpdateManager", "Triggering installation for: ${file.absolutePath}")

            // Grant permission check (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    android.util.Log.w("UpdateManager", "Permission REQUEST_INSTALL_PACKAGES not granted. Redirecting user.")
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            android.util.Log.d("UpdateManager", "Installer intent sent successfully")
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Final installation trigger failed", e)
        }
    }

    fun cancelDownload() {
        if (downloadId != -1L) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
            downloadId = -1L
        }
        updateReceiver?.let {
            try { context.unregisterReceiver(it) } catch(e: Exception) {}
            updateReceiver = null
        }
    }
}
