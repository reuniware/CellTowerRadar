package com.reuniware.celltowerradar.repository

import com.reuniware.celltowerradar.model.CellTowerEntity
import com.reuniware.celltowerradar.model.CellTowerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CellTowerRepository @Inject constructor(
    private val cellTowerDao: CellTowerDao
) {
    private val _cellTowers = MutableStateFlow<List<CellTowerInfo>>(emptyList())
    val cellTowers: StateFlow<List<CellTowerInfo>> = _cellTowers.asStateFlow()

    // Expose flow from DB
    val history: Flow<List<CellTowerEntity>> = cellTowerDao.getAllHistory()

    suspend fun updateTowers(towers: List<CellTowerInfo>) {
        _cellTowers.value = towers
        
        towers.forEach { tower ->
            // Convert to Entity for storage
            val entity = CellTowerEntity(
                id = tower.id, type = tower.type, mcc = tower.mcc, mnc = tower.mnc,
                lac = tower.lac, cid = tower.cid, signalStrength = tower.signalStrength,
                pci = tower.pci, isRegistered = tower.isRegistered, timestamp = tower.timestamp,
                latitude = tower.latitude, longitude = tower.longitude, altitude = tower.altitude,
                rsrp = tower.rsrp, rsrq = tower.rsrq, rssnr = tower.rsrq,
                cqi = tower.cqi, ta = tower.ta, earfcn = tower.earfcn,
                lteBand = tower.lteBand, ssRsrp = tower.ssRsrp, ssRsrq = tower.ssRsrq,
                ssSinr = tower.ssSinr, csiRsrp = tower.csiRsrp, csiRsrq = tower.csiRsrq,
                csiSinr = tower.csiSinr, nrarfcn = tower.nrarfcn, nrBand = tower.nrBand,
                arfcn = tower.arfcn, psc = tower.psc, operatorName = tower.operatorName,
                bandwidth = tower.bandwidth, is5gNsa = tower.is5gNsa, is5gSa = tower.is5gSa,
                dataNetworkType = tower.dataNetworkType, frequencyMhz = tower.frequencyMhz,
                vendor = tower.vendor, isIsolated = tower.isIsolated,
                securityAlert = tower.securityAlert, handoversCount = tower.handoversCount
            )
            cellTowerDao.insert(entity)
        }
    }

    suspend fun clearHistory() {
        cellTowerDao.clearHistory()
    }
}
