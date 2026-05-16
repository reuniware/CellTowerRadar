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

        return when (info) {
            is CellInfoLte -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTowerInfo(
                    id = "LTE_${identity.ci}",
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
                    is5gSa = is5gSa
                )
            }
            is CellInfoGsm -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTowerInfo(
                    id = "GSM_${identity.cid}",
                    type = "GSM",
                    mcc = sanitizeString(identity.mccString),
                    mnc = sanitizeString(identity.mncString),
                    lac = sanitizeInt(identity.lac),
                    cid = sanitizeLong(identity.cid.toLong()),
                    signalStrength = sanitizeInt(signal.dbm),
                    pci = null,
                    isRegistered = info.isRegistered,
                    arfcn = sanitizeInt(identity.arfcn),
                    operatorName = operatorName
                )
            }
            is CellInfoWcdma -> {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTowerInfo(
                    id = "WCDMA_${identity.cid}",
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
                    operatorName = operatorName
                )
            }
            is CellInfoNr -> {
                val identity = info.cellIdentity as CellIdentityNr
                val signal = info.cellSignalStrength as CellSignalStrengthNr
                CellTowerInfo(
                    id = "NR_${identity.nci}",
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
                    is5gNsa = false
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
}
