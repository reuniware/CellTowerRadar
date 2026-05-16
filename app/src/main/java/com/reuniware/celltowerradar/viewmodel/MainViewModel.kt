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
    @ApplicationContext private val context: Context
) : ViewModel() {

    val cellTowers: StateFlow<List<com.reuniware.celltowerradar.model.CellTowerInfo>> = repository.cellTowers
    val history: StateFlow<List<com.reuniware.celltowerradar.model.CellTowerInfo>> = repository.history
    
    private val _scanStatus = kotlinx.coroutines.flow.MutableStateFlow("Ready")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    private val _isScanning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

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
                scanner.scan { results ->
                    if (results.isNotEmpty()) {
                        _scanStatus.value = "Found ${results.size} towers"
                        repository.updateTowers(results)
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
}
