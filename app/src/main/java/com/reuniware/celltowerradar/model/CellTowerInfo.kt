package com.reuniware.celltowerradar.model

data class CellTowerInfo(
    val id: String,
    val type: String, // GSM, WCDMA, LTE, NR (5G)
    val mcc: String?,
    val mnc: String?,
    val lac: Int?, // Location Area Code / Tracking Area Code
    val cid: Long?, // Cell ID / New Radio Cell Identity
    val signalStrength: Int?, // in dBm
    val pci: Int?, // Physical Cell ID
    val isRegistered: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Advanced LTE Metrics
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssnr: Int? = null,
    val cqi: Int? = null,
    val ta: Int? = null,
    val earfcn: Int? = null,
    val lteBand: Int? = null,

    // Advanced NR (5G) Metrics
    val ssRsrp: Int? = null,
    val ssRsrq: Int? = null,
    val ssSinr: Int? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Int? = null,
    val nrarfcn: Int? = null,
    val nrBand: Int? = null,

    // GSM/WCDMA
    val arfcn: Int? = null,
    val psc: Int? = null, // Primary Scrambling Code for WCDMA

    // General
    val operatorName: String? = null,
    val bandwidth: Int? = null,
    val is5gNsa: Boolean = false,
    val is5gSa: Boolean = false,
    val dataNetworkType: String? = null,
    
    // Intelligence & Security
    val frequencyMhz: Double? = null,
    val bandName: String? = null,
    val vendor: String? = null,
    val isIsolated: Boolean = false,
    val securityAlert: String? = null,
    val handoversCount: Int = 0,

    // Geospatial data for mapping
    val latitude: Double? = null,
    val longitude: Double? = null
)
