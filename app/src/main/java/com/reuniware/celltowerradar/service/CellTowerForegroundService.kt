package com.reuniware.celltowerradar.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.reuniware.celltowerradar.MainActivity
import com.reuniware.celltowerradar.repository.CellTowerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class CellTowerForegroundService : Service() {

    @Inject
    lateinit var scanner: CellTowerScanner

    @Inject
    lateinit var repository: CellTowerRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        const val CHANNEL_ID = "CellTowerScanningChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): android.location.Location? {
        return try {
            val task = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            task.await()
        } catch (e: Exception) {
            android.util.Log.e("CellTowerService", "Error getting location", e)
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startScanning()
        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cell Tower Radar")
            .setContentText("Scanning for cell towers in background...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            try {
                android.util.Log.d("CellTowerService", "Calling startForeground with type $type")
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                android.util.Log.e("CellTowerService", "Failed to start foreground", e)
            }
        } else {
            android.util.Log.d("CellTowerService", "Calling startForeground (Legacy)")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startScanning() {
        android.util.Log.d("CellTowerService", "Starting scan loop")
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            while (isActive) {
                android.util.Log.d("CellTowerService", "Requesting scan...")
                // Wrapped the scan call to ensure repository operations run within a coroutine context if needed,
                // although the error specifically suggests a problem with how the suspend function/callback interacts.
                // Given the scanner structure, we will just ensure the repository update is handled appropriately.
                scanner.scan { results ->
                    launch {
                        android.util.Log.d("CellTowerService", "Scan results received: ${results.size} towers")
                        repository.updateTowers(results)
                        updateNotification("${results.size} towers found")
                    }
                }
                delay(5000)
            }
        }
    }

    private fun updateNotification(contentText: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cell Tower Radar")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Cell Tower Scanning Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
