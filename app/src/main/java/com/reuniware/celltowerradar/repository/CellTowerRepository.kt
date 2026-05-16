package com.reuniware.celltowerradar.repository

import com.reuniware.celltowerradar.model.CellTowerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CellTowerRepository @Inject constructor() {
    private val _cellTowers = MutableStateFlow<List<CellTowerInfo>>(emptyList())
    val cellTowers: StateFlow<List<CellTowerInfo>> = _cellTowers.asStateFlow()

    private val _history = MutableStateFlow<List<CellTowerInfo>>(emptyList())
    val history: StateFlow<List<CellTowerInfo>> = _history.asStateFlow()

    fun updateTowers(towers: List<CellTowerInfo>) {
        val previousRegistered = _cellTowers.value.find { it.isRegistered }
        val currentRegistered = towers.find { it.isRegistered }
        
        _cellTowers.value = towers
        
        // Update History & Detect Anomalies
        val currentHistory = _history.value.toMutableList()
        towers.forEach { tower ->
            var updatedTower = tower

            // Anomaly: Isolated Cell (No neighbors)
            if (tower.isRegistered && towers.size == 1) {
                updatedTower = updatedTower.copy(isIsolated = true, securityAlert = "ISOLATED CELL DETECTED")
            }

            // Anomaly: Forced Downgrade
            if (previousRegistered != null && currentRegistered != null) {
                if ((previousRegistered.type == "LTE" || previousRegistered.type.contains("NR")) && 
                    (currentRegistered.type == "GSM" || currentRegistered.type == "WCDMA")) {
                    updatedTower = updatedTower.copy(securityAlert = "POTENTIAL FORCED DOWNGRADE")
                }
            }

            val index = currentHistory.indexOfFirst { it.id == tower.id }
            if (index == -1) {
                currentHistory.add(updatedTower)
            } else {
                val existing = currentHistory[index]
                currentHistory[index] = updatedTower.copy(
                    signalStrength = if ((tower.signalStrength ?: -999) > (existing.signalStrength ?: -999)) 
                        tower.signalStrength else existing.signalStrength,
                    handoversCount = if (previousRegistered?.id != currentRegistered?.id && tower.isRegistered) 
                        existing.handoversCount + 1 else existing.handoversCount
                )
            }
        }
        _history.value = currentHistory.sortedByDescending { it.timestamp }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }
}
