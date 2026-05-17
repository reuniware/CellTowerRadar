package com.reuniware.celltowerradar.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cell_towers")
data class CellTowerEntity(
    @PrimaryKey val id: String,
    val type: String,
    val mcc: String?,
    val mnc: String?,
    val lac: Int?,
    val cid: Long?,
    val signalStrength: Int?,
    val pci: Int?,
    val isRegistered: Boolean,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssnr: Int? = null,
    val cqi: Int? = null,
    val ta: Int? = null,
    val earfcn: Int? = null,
    val lteBand: Int? = null,
    val ssRsrp: Int? = null,
    val ssRsrq: Int? = null,
    val ssSinr: Int? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Int? = null,
    val nrarfcn: Int? = null,
    val nrBand: Int? = null,
    val arfcn: Int? = null,
    val psc: Int? = null,
    val operatorName: String? = null,
    val bandwidth: Int? = null,
    val is5gNsa: Boolean = false,
    val is5gSa: Boolean = false,
    val dataNetworkType: String? = null,
    val frequencyMhz: Double? = null,
    val vendor: String? = null,
    val isIsolated: Boolean = false,
    val securityAlert: String? = null,
    val handoversCount: Int = 0
)
