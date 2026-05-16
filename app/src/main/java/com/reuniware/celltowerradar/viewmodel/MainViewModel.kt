package com.reuniware.celltowerradar.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import com.reuniware.celltowerradar.repository.CellTowerRepository
import com.reuniware.celltowerradar.service.CellTowerForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.reuniware.celltowerradar.service.CellTowerScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: CellTowerRepository,
    private val scanner: CellTowerScanner,
    private val updateManager: com.reuniware.celltowerradar.util.UpdateManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val cellTowers: StateFlow<List<com.reuniware.celltowerradar.model.CellTowerInfo>> = repository.cellTowers
    val history: StateFlow<List<com.reuniware.celltowerradar.model.CellTowerInfo>> = repository.history
    
    private val _updateInfo = kotlinx.coroutines.flow.MutableStateFlow<com.reuniware.celltowerradar.util.UpdateManager.UpdateInfo?>(null)
    val updateInfo: StateFlow<com.reuniware.celltowerradar.util.UpdateManager.UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isDownloading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    
    private val _scanStatus = kotlinx.coroutines.flow.MutableStateFlow("Ready")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    private val _isScanning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    data class SystemStatus(
        val isAirplaneModeOn: Boolean = false,
        val isGpsEnabled: Boolean = false,
        val hasCellularNetwork: Boolean = true,
        val isPowerSaveMode: Boolean = false,
        val canInstallPackages: Boolean = true
    )

    private val _systemStatus = kotlinx.coroutines.flow.MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private var scanJob: Job? = null

    init {
        updateSystemStatus()
        checkForUpdates()
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = "v${packageInfo.versionName}"
                _updateInfo.value = updateManager.checkForUpdates(currentVersion)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Check updates failed", e)
            }
        }
    }

    fun triggerUpdate() {
        _updateInfo.value?.let { info ->
            _isDownloading.value = true
            updateManager.downloadAndInstall(info.downloadUrl)
            // Note: In a production app, we would listen for download completion to reset this
        }
    }

    fun updateSystemStatus() {
        val isAirplaneMode = android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsOn = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isPowerSave = powerManager.isPowerSaveMode

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        val hasNetwork = telephonyManager.networkOperatorName.isNotEmpty()

        val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

        _systemStatus.value = SystemStatus(
            isAirplaneModeOn = isAirplaneMode,
            isGpsEnabled = isGpsOn,
            hasCellularNetwork = hasNetwork,
            isPowerSaveMode = isPowerSave,
            canInstallPackages = canInstall
        )
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        android.util.Log.d("MainViewModel", "startScanning called")
        _scanStatus.value = "Scanning..."
        
        // Start background service
        try {
            val intent = Intent(context, CellTowerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Failed to start service", e)
        }
        
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            while (isActive) {
                // Get current location for mapping
                val location = try {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                    lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) 
                        ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                } catch (e: SecurityException) { null }

                scanner.scan { results ->
                    if (results.isNotEmpty()) {
                        _scanStatus.value = "Found ${results.size} towers"
                        // Attach current location to each tower for historical mapping
                        val locatedResults = results.map { 
                            it.copy(latitude = location?.latitude, longitude = location?.longitude) 
                        }
                        repository.updateTowers(locatedResults)
                    } else if (cellTowers.value.isEmpty()) {
                        _scanStatus.value = "No towers found (Check GPS)"
                    }
                }
                delay(4000)
            }
        }
    }

    fun stopScanning() {
        android.util.Log.d("MainViewModel", "stopScanning called")
        _isScanning.value = false
        _scanStatus.value = "Stopped"
        scanJob?.cancel()
        val intent = Intent(context, CellTowerForegroundService::class.java)
        context.stopService(intent)
        // Also cancel any pending update download
        updateManager.cancelDownload()
        _isDownloading.value = false
    }

    fun clearHistory() {
        repository.clearHistory()
    }

    fun exportHistoryToCSV() {
        val data = history.value
        if (data.isEmpty()) return

        val csvHeader = "Timestamp,Type,Operator,MCC,MNC,LAC_TAC,CID_NCI,Signal_dBm,RSRP,RSRQ,RSSNR,Band\n"
        val csvRows = data.joinToString("\n") { 
            "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it.timestamp)}," +
            "${it.type},${it.operatorName},${it.mcc},${it.mnc},${it.lac},${it.cid},${it.signalStrength}," +
            "${it.rsrp ?: ""},${it.rsrq ?: ""},${it.rssnr ?: ""},${it.lteBand ?: it.nrBand ?: ""}"
        }
        val csvContent = csvHeader + csvRows

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvContent)
            type = "text/csv"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export History CSV")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun exportHistoryToKML() {
        val data = history.value
        if (data.isEmpty()) return

        val kmlHeader = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>CellTowerRadar History</name>
"""
        val kmlFooter = """  </Document>
</kml>"""
        
        val kmlPlacemarks = data.joinToString("\n") { 
            """    <Placemark>
      <name>${it.type} - ${it.cid}</name>
      <description>Vendor: ${it.vendor}, Signal: ${it.signalStrength}dBm, MCC: ${it.mcc}, MNC: ${it.mnc}</description>
      <Point>
        <!-- Coordinates are N/A since we don't store lat/lon yet, but structure is ready -->
        <coordinates>0,0,0</coordinates>
      </Point>
    </Placemark>"""
        }
        
        val kmlContent = kmlHeader + kmlPlacemarks + kmlFooter

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, kmlContent)
            type = "application/vnd.google-earth.kml+xml"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export History KML")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
