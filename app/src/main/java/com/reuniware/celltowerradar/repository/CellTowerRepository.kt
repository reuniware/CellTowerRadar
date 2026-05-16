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
        _cellTowers.value = towers
        
        // Update History
        val currentHistory = _history.value.toMutableList()
        towers.forEach { tower ->
            val index = currentHistory.indexOfFirst { it.id == tower.id }
            if (index == -1) {
                // New tower discovered
                currentHistory.add(tower)
            } else {
                // Update existing tower in history if signal is better or to update timestamp
                val existing = currentHistory[index]
                currentHistory[index] = tower.copy(
                    // Keep the oldest timestamp as "first seen" if we wanted, 
                    // but here we just update to latest info
                    signalStrength = if ((tower.signalStrength ?: -999) > (existing.signalStrength ?: -999)) 
                        tower.signalStrength else existing.signalStrength
                )
            }
        }
        _history.value = currentHistory.sortedByDescending { it.timestamp }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }
}
