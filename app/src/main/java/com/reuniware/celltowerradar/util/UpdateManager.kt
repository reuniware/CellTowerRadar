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
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestTag = json.getString("tag_name")
                    
                    if (latestTag != currentVersion) {
                        val assets = json.getJSONArray("assets")
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                return@withContext UpdateInfo(
                                    tagName = latestTag,
                                    downloadUrl = asset.getString("browser_download_url")
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateManager", "Check for updates failed", e)
            }
            null
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        try {
            // Delete old update file to avoid cache/conflict issues
            val oldFile = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            if (oldFile.exists()) oldFile.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("CellTowerRadar Update")
                .setDescription("Downloading latest version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update.apk")
                .setAllowedOverMetered(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            // Cleanup old receiver if any
            updateReceiver?.let { try { context.unregisterReceiver(it) } catch(e: Exception) {} }

            updateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk()
                        // Stop listening once install is triggered
                        context.unregisterReceiver(this)
                        updateReceiver = null
                    }
                }
            }
            
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(updateReceiver, filter)
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Download failed", e)
        }
    }

    private fun installApk() {
        try {
            val file = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            if (!file.exists()) {
                android.util.Log.e("UpdateManager", "APK file not found")
                return
            }

            // Check if we can install packages (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
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
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Installation failed", e)
        }
    }
}
