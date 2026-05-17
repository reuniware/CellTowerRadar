package com.reuniware.celltowerradar.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.*
import com.reuniware.celltowerradar.model.CellTowerInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CellTowerScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @SuppressLint("MissingPermission")
    fun scan(callback: (List<CellTowerInfo>) -> Unit) {
        android.util.Log.d("CellTowerScanner", "Scan requested")
        
        // Try to get immediate results first (faster)
        val immediateResults = mutableListOf<CellTowerInfo>()
        try {
            telephonyManager.allCellInfo?.forEach { info ->
                processCellInfo(info, telephonyManager, "Default")?.let { immediateResults.add(it) }
            }
            if (immediateResults.isNotEmpty()) {
                android.util.Log.d("CellTowerScanner", "Found ${immediateResults.size} immediate results")
                callback(immediateResults)
            }
        } catch (e: Exception) {
            android.util.Log.e("CellTowerScanner", "Immediate scan failed", e)
        }

        // Then request a fresh update (better for newer Androids)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            
            if (activeSubscriptions.isNullOrEmpty()) {
                requestUpdate(telephonyManager, null, callback)
            } else {
                activeSubscriptions.forEach { subInfo ->
                    val subTelephonyManager = telephonyManager.createForSubscriptionId(subInfo.subscriptionId)
                    requestUpdate(subTelephonyManager, subInfo.displayName.toString(), callback)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdate(tm: TelephonyManager, simName: String?, callback: (List<CellTowerInfo>) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tm.requestCellInfoUpdate(context.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                    android.util.Log.d("CellTowerScanner", "Callback received for $simName: ${cellInfo.size} cells")
                    val results = cellInfo.mapNotNull { processCellInfo(it, tm, simName) }
                    if (results.isNotEmpty()) {
                        callback(results)
                    }
                }
            })
        }
    }

    private fun processCellInfo(info: CellInfo, tm: TelephonyManager, simName: String?): CellTowerInfo? {
        val operatorName = simName ?: tm.networkOperatorName
        
        val is5gSa = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
            info.isRegistered
        } else false
        
        val is5gNsa = false

        fun sanitizeInt(value: Int): Int? = if (value == Int.MAX_VALUE || value == -1) null else value
        fun sanitizeLong(value: Long): Long? = if (value == Long.MAX_VALUE || value == -1L) null else value
        fun sanitizeString(value: String?): String? = if (value == null || value == "2147483647") null else value

        fun getOperatorKey(mcc: String?, mnc: String?): String {
            return if (mcc.isNullOrEmpty() || mnc.isNullOrEmpty()) "UNKNOWN" else "${mcc}_${mnc}"
        }

        return when (info) {
            is CellInfoLte -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val operatorKey = getOperatorKey(identity.mccString, identity.mncString)
                val uniqueId = "LTE_${operatorKey}_${identity.tac}_${identity.ci}_${identity.earfcn}"
                CellTowerInfo(
                    id = uniqueId,
                    type = "LTE",
                    mcc = sanitizeString(identity.mccString),
                    mnc = sanitizeString(identity.mncString),
                    lac = sanitizeInt(identity.tac),
                    cid = sanitizeLong(identity.ci.toLong()),
                    signalStrength = sanitizeInt(signal.dbm),
                    pci = sanitizeInt(identity.pci),
                    isRegistered = info.isRegistered,
                    rsrp = sanitizeInt(signal.rsrp),
                    rsrq = sanitizeInt(signal.rsrq),
                    rssnr = sanitizeInt(signal.rssnr),
                    cqi = sanitizeInt(signal.cqi),
                    ta = sanitizeInt(signal.timingAdvance),
                    earfcn = sanitizeInt(identity.earfcn),
                    lteBand = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands.firstOrNull() else null,
                    bandwidth = sanitizeInt(identity.bandwidth),
                    operatorName = operatorName,
                    is5gNsa = is5gNsa,
                    is5gSa = is5gSa,
                    frequencyMhz = getLteFrequency(identity.earfcn),
                    vendor = estimateVendor(identity.ci.toLong(), identity.mccString)
                )
            }
            is CellInfoGsm -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val operatorKey = getOperatorKey(identity.mccString, identity.mncString)
                val uniqueId = "GSM_${operatorKey}_${identity.lac}_${identity.cid}"
                CellTowerInfo(
                    id = uniqueId,
                    type = "GSM",
                    mcc = sanitizeString(identity.mccString),
                    mnc = sanitizeString(identity.mncString),
                    lac = sanitizeInt(identity.lac),
                    cid = sanitizeLong(identity.cid.toLong()),
                    signalStrength = sanitizeInt(signal.dbm),
                    pci = null,
                    isRegistered = info.isRegistered,
                    arfcn = sanitizeInt(identity.arfcn),
                    operatorName = operatorName,
                    vendor = estimateVendor(identity.cid.toLong(), identity.mccString)
                )
            }
            is CellInfoWcdma -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                val operatorKey = getOperatorKey(identity.mccString, identity.mncString)
                val uniqueId = "WCDMA_${operatorKey}_${identity.lac}_${identity.cid}"
                CellTowerInfo(
                    id = uniqueId,
                    type = "WCDMA",
                    mcc = sanitizeString(identity.mccString),
                    mnc = sanitizeString(identity.mncString),
                    lac = sanitizeInt(identity.lac),
                    cid = sanitizeLong(identity.cid.toLong()),
                    signalStrength = sanitizeInt(signal.dbm),
                    pci = sanitizeInt(identity.psc),
                    isRegistered = info.isRegistered,
                    arfcn = sanitizeInt(identity.uarfcn),
                    psc = sanitizeInt(identity.psc),
                    operatorName = operatorName,
                    vendor = estimateVendor(identity.cid.toLong(), identity.mccString)
                )
            }
            is CellInfoNr -> {
                val identity = info.cellIdentity as CellIdentityNr
                val signal = info.cellSignalStrength as CellSignalStrengthNr
                val operatorKey = getOperatorKey(identity.mccString, identity.mncString)
                val uniqueId = "NR_${operatorKey}_${identity.tac}_${identity.nci}"
                CellTowerInfo(
                    id = uniqueId,
                    type = "NR (5G)",
                    mcc = sanitizeString(identity.mccString),
                    mnc = sanitizeString(identity.mncString),
                    lac = sanitizeInt(identity.tac),
                    cid = sanitizeLong(identity.nci),
                    signalStrength = sanitizeInt(signal.dbm),
                    pci = sanitizeInt(identity.pci),
                    isRegistered = info.isRegistered,
                    ssRsrp = sanitizeInt(signal.ssRsrp),
                    ssRsrq = sanitizeInt(signal.ssRsrq),
                    ssSinr = sanitizeInt(signal.ssSinr),
                    csiRsrp = sanitizeInt(signal.csiRsrp),
                    csiRsrq = sanitizeInt(signal.csiRsrq),
                    csiSinr = sanitizeInt(signal.csiSinr),
                    nrarfcn = sanitizeInt(identity.nrarfcn),
                    nrBand = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) identity.bands.firstOrNull() else null,
                    operatorName = operatorName,
                    is5gSa = info.isRegistered,
                    is5gNsa = false,
                    frequencyMhz = getNrFrequency(identity.nrarfcn),
                    vendor = estimateVendor(identity.nci, identity.mccString)
                )
            }
            else -> null
        }?.copy(dataNetworkType = if (info.isRegistered) {
            try {
                @SuppressLint("MissingPermission")
                val type = tm.dataNetworkType
                getNetworkTypeName(type)
            } catch (e: Exception) {
                "Unknown"
            }
        } else null)
    }

    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
            else -> "Unknown ($type)"
        }
    }

    // --- RADIO INTELLIGENCE HELPERS ---

    private fun getLteFrequency(earfcn: Int?): Double? {
        if (earfcn == null) return null
        return when {
            earfcn in 0..599 -> 2100.0 + (earfcn - 0) * 0.1 // B1
            earfcn in 1200..1949 -> 1800.0 + (earfcn - 1200) * 0.1 // B3
            earfcn in 2400..2649 -> 2600.0 + (earfcn - 2400) * 0.1 // B7
            earfcn in 2750..3449 -> 900.0 + (earfcn - 2750) * 0.1 // B8
            earfcn in 6150..6449 -> 800.0 + (earfcn - 6150) * 0.1 // B20
            earfcn in 9210..9659 -> 700.0 + (earfcn - 9210) * 0.1 // B28
            earfcn in 37750..38249 -> 2600.0 + (earfcn - 37750) * 0.1 // B38 (TDD)
            else -> null
        }
    }

    private fun getNrFrequency(nrarfcn: Int?): Double? {
        if (nrarfcn == null) return null
        return when {
            nrarfcn in 0..599999 -> nrarfcn * 0.005 // Sub-3GHz
            nrarfcn in 600000..2016666 -> 3000.0 + (nrarfcn - 600000) * 0.015 // 3GHz-10GHz
            else -> null
        }
    }

    private fun estimateVendor(cid: Long?, mcc: String?): String? {
        if (cid == null) return null
        return when {
            cid % 1000 == 0L -> "Nokia"
            cid % 3 == 0L && mcc == "208" -> "Huawei"
            cid % 2 == 0L -> "Ericsson"
            else -> "ZTE/Alt"
        }
    }
}
