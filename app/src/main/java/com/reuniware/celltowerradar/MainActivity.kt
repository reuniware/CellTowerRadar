package com.reuniware.celltowerradar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.reuniware.celltowerradar.model.CellTowerInfo
import com.reuniware.celltowerradar.ui.theme.CellTowerRadarTheme
import com.reuniware.celltowerradar.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CellTowerRadarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CellTowerRadarScreen()
                }
            }
        }
    }
}

@Composable
fun CellTowerRadarScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val cellTowers by viewModel.cellTowers.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var navigateToLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Periodically refresh system status
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateSystemStatus()
            delay(5000)
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasPhoneStatePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasPhoneStatePermission = permissions[Manifest.permission.READ_PHONE_STATE] == true
        hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] == true || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    LaunchedEffect(hasLocationPermission, hasPhoneStatePermission, hasNotificationPermission) {
        if (!hasLocationPermission || !hasPhoneStatePermission || !hasNotificationPermission) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(permissions.toTypedArray())
        } else {
            viewModel.startScanning()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Cell Tower Radar Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // --- ALERTS SECTION ---
        if (systemStatus.isAirplaneModeOn) {
            AlertCard("AIRPLANE MODE ON", "Deactivate airplane mode to scan towers.", Color(0xFFD32F2F))
        }
        
        if (!systemStatus.hasCellularNetwork && !systemStatus.isAirplaneModeOn) {
            AlertCard("NO CELLULAR SIGNAL", "Searching for network provider...", Color(0xFFF57C00))
        }

        if (!systemStatus.isGpsEnabled) {
            AlertCard("GPS DISABLED", "Scanning performance is limited without GPS.", Color(0xFFF57C00)) {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        
        if (systemStatus.isPowerSaveMode) {
            AlertCard("POWER SAVE MODE", "Background scanning may be throttled.", Color(0xFF1976D2))
        }

        if (!systemStatus.canInstallPackages) {
            AlertCard(
                title = "UPDATE PERMISSION MISSING",
                message = "The app needs authorization to install its own updates.",
                color = Color(0xFFD32F2F)
            ) {
                viewModel.openInstallPermissionSettings()
            }
        }

        updateInfo?.let { info ->
            AlertCard(
                title = if (isDownloading) "DOWNLOADING UPDATE..." else "UPDATE AVAILABLE: ${info.tagName}",
                message = if (isDownloading) "Please wait while the new version is being fetched." else "A newer tactical version is ready on GitHub.",
                color = if (isDownloading) Color(0xFF1976D2) else Color(0xFF4CAF50),
                onAction = if (isDownloading) null else { { viewModel.triggerUpdate() } }
            )
        }
        
        val activeTower = cellTowers.find { it.isRegistered }
        activeTower?.let {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Current Primary Connection", fontWeight = FontWeight.Bold)
                    Text("Operator: ${it.operatorName ?: "Unknown"}")
                    Text("Network: ${it.type} ${if (it.is5gSa) "5G-SA" else if (it.is5gNsa) "5G-NSA" else ""}")
                    it.dataNetworkType?.let { type ->
                        Text("Data Type: $type", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Signal: ${it.signalStrength} dBm", color = getSignalColor(it.signalStrength))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var selectedTab by remember { mutableIntStateOf(0) }
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Live", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("History", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text("Map", modifier = Modifier.padding(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // ... alerts and header ...
        
        // Use weight 1f for the content area to allow it to take available space
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (hasLocationPermission && hasPhoneStatePermission && hasNotificationPermission) {
                when (selectedTab) {
                    0 -> {
                        // LIVE VIEW
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(
                                onClick = { viewModel.startScanning() },
                                enabled = !isScanning,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color.LightGray,
                                    disabledContentColor = Color.DarkGray
                                )
                            ) {
                                Text("Start Scan")
                            }
                            Button(
                                onClick = { viewModel.stopScanning() },
                                enabled = isScanning,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color.LightGray,
                                    disabledContentColor = Color.DarkGray
                                )
                            ) {
                                Text("Stop Scan")
                            }
                        }
                        Text(
                            text = "Status: $scanStatus",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = cellTowers,
                                key = { it.id }
                            ) { tower ->
                                CellTowerItem(tower) { lat, lon ->
                                    navigateToLocation = Pair(lat, lon)
                                    selectedTab = 2
                                }
                            }
                        }
                    }
                    1 -> {
                        // HISTORY VIEW
                        val history by viewModel.history.collectAsState()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = { viewModel.exportHistoryToCSV() }) {
                                Text("CSV")
                            }
                            Button(onClick = { viewModel.exportHistoryToKML() }) {
                                Text("KML")
                            }
                            Button(
                                onClick = { viewModel.clearHistory() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear")
                            }
                        }
                        Text(
                            text = "${history.size} unique cells encountered",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = history,
                                key = { it.id }
                            ) { tower ->
                                CellTowerItem(tower) { lat, lon ->
                                    navigateToLocation = Pair(lat, lon)
                                    selectedTab = 2
                                }
                            }
                        }
                    }
                    2 -> {
                        // TACTICAL MAP VIEW
                        val historyData by viewModel.history.collectAsState()
                        TacticalMapView(history = historyData, initialLocation = navigateToLocation)
                    }
                }
            } else {
                Text(text = "Permissions required to scan cell towers in background.")
                Button(onClick = {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    launcher.launch(permissions.toTypedArray())
                }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Composable
fun TacticalMapView(history: List<CellTowerInfo>, initialLocation: Pair<Double, Double>?) {
    val context = LocalContext.current
    
    // Improved OSM Initialization
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, android.preference.PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // State to track if user has manually moved the map
    var isMapInteracted by remember { mutableStateOf(false) }

    // Effect to handle navigation from history
    LaunchedEffect(initialLocation) {
        initialLocation?.let { isMapInteracted = false }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setUseDataConnection(true)
                controller.setZoom(15.0)

                // Listen for user interaction to stop automatic centering
                setOnTouchListener { _, _ ->
                    isMapInteracted = true
                    false
                }
                
                // Set center on passed location, last known, or default
                val point = initialLocation?.let { GeoPoint(it.first, it.second) }
                    ?: history.lastOrNull { it.latitude != null }?.let { GeoPoint(it.latitude!!, it.longitude!!) }
                    ?: GeoPoint(48.8566, 2.3522)
                
                controller.setCenter(point)
            }
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
        update = { mapView ->
            // Use a map to aggregate markers by a unique ID (CID)
            // This prevents duplicate markers when location varies slightly
            val uniqueTowers = history.filter { it.latitude != null && it.longitude != null }
                .groupBy { it.cid ?: it.id } // Group by CID or fallback to ID
                .mapValues { entry -> entry.value.last() } // Keep the most recent data for this tower

            // Clear and re-add only if the set of towers has changed significantly
            // Or just clear/re-add once per update.
            mapView.overlays.clear()
            uniqueTowers.values.forEach { tower ->
                val marker = Marker(mapView)
                marker.position = GeoPoint(tower.latitude!!, tower.longitude!!)
                marker.title = "${tower.type} ID: ${tower.cid}"
                marker.snippet = "Signal: ${tower.signalStrength}dBm\nVendor: ${tower.vendor}"
                
                marker.setOnMarkerClickListener { m, _ ->
                    isMapInteracted = true
                    mapView.controller.animateTo(m.position)
                    m.showInfoWindow()
                    true
                }

                marker.icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                mapView.overlays.add(marker)
            }
            
            // Only auto-center if user hasn't interacted with the map
            if (!isMapInteracted) {
                val centerPoint = initialLocation?.let { GeoPoint(it.first, it.second) }
                    ?: history.lastOrNull { it.latitude != null }?.let { GeoPoint(it.latitude!!, it.longitude!!) }
                
                centerPoint?.let {
                    mapView.controller.animateTo(it)
                }
            }
            mapView.invalidate()
        }
    )
}

@Composable
fun CellTowerItem(tower: CellTowerInfo, onNavigateToMap: ((Double, Double) -> Unit)? = null) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    val uniqueId = tower.cid?.toString() ?: "???"
                    Text(
                        text = "${tower.type} ID: $uniqueId",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (tower.is5gSa || tower.is5gNsa) {
                        Text(
                            text = if (tower.is5gSa) "5G-Standalone" else "5G-NonStandalone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    tower.operatorName?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                    tower.securityAlert?.let {
                        Text(text = "⚠️ $it", color = Color.Red, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    }
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    if (tower.isRegistered) {
                        Text(
                            text = "CONNECTED",
                            color = Color.Blue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "${tower.signalStrength ?: "N/A"} dBm",
                        color = getSignalColor(tower.signalStrength),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                val mccText = tower.mcc ?: if (tower.isRegistered) "Searching..." else "Limited (Neighbor)"
                val mncText = tower.mnc ?: if (tower.isRegistered) "Searching..." else "Limited (Neighbor)"
                Text(text = "MCC: $mccText | MNC: $mncText", style = MaterialTheme.typography.bodyMedium)
                
                val lacText = tower.lac?.toString() ?: "Scanning..."
                val cidText = tower.cid?.toString() ?: "Scanning..."
                Text(text = "LAC/TAC: $lacText | CID/NCI: $cidText", style = MaterialTheme.typography.bodyMedium)
                
                if (tower.pci != null) {
                    Text(text = "PCI: ${tower.pci} ${if (tower.psc != null) "| PSC: ${tower.psc}" else ""}")
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Advanced Metrics Section
                Text(text = "Signal Metrics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    val strengthText = tower.signalStrength?.let { "$it dBm" } ?: "Low/Wait"
                    Text(
                        text = "Strength: $strengthText",
                        color = getSignalColor(tower.signalStrength)
                    )
                    
                    // LTE Specifics
                    if (tower.type == "LTE") {
                        tower.vendor?.let { Text(text = "Vendor: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                        tower.frequencyMhz?.let { Text(text = "Freq: $it MHz", style = MaterialTheme.typography.bodySmall) }
                        tower.rsrp?.let { Text(text = "RSRP: $it dBm", style = MaterialTheme.typography.bodySmall) }
                        tower.rsrq?.let { Text(text = "RSRQ: $it dB", style = MaterialTheme.typography.bodySmall) }
                        tower.rssnr?.let { Text(text = "RSSNR: $it dB", style = MaterialTheme.typography.bodySmall) }
                        tower.earfcn?.let { Text(text = "EARFCN: $it ${if (tower.lteBand != null) "(Band ${tower.lteBand})" else ""}", style = MaterialTheme.typography.bodySmall) }
                        tower.bandwidth?.let { Text(text = "Bandwidth: ${it / 1000} MHz", style = MaterialTheme.typography.bodySmall) }
                        tower.ta?.let { Text(text = "Timing Advance: $it (~${it * 78}m)", style = MaterialTheme.typography.bodySmall) }
                    }
                    
                    // NR Specifics
                    if (tower.type.contains("NR")) {
                        tower.vendor?.let { Text(text = "Vendor: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
                        tower.frequencyMhz?.let { Text(text = "Freq: $it MHz", style = MaterialTheme.typography.bodySmall) }
                        tower.ssRsrp?.let { Text(text = "SS-RSRP: $it dBm", style = MaterialTheme.typography.bodySmall) }
                        tower.ssRsrq?.let { Text(text = "SS-RSRQ: $it dB", style = MaterialTheme.typography.bodySmall) }
                        tower.ssSinr?.let { Text(text = "SS-SINR: $it dB", style = MaterialTheme.typography.bodySmall) }
                        tower.nrarfcn?.let { Text(text = "NR-ARFCN: $it ${if (tower.nrBand != null) "(Band n${tower.nrBand})" else ""}", style = MaterialTheme.typography.bodySmall) }
                    }
                    
                    // GSM/WCDMA
                    tower.arfcn?.let { Text(text = "ARFCN/UARFCN: $it", style = MaterialTheme.typography.bodySmall) }
                }

                // Local Map Button
                Spacer(modifier = Modifier.height(8.dp))
                val hasCoords = tower.latitude != null && tower.longitude != null
                val canNavigate = hasCoords && onNavigateToMap != null
                Button(
                    onClick = { if (canNavigate) onNavigateToMap?.invoke(tower.latitude!!, tower.longitude!!) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canNavigate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(if (hasCoords) "Locate on Map" else "Location Unavailable")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            if (tower.latitude != null && tower.longitude != null) {
                Text(
                    text = "GPS: ${"%.5f".format(tower.latitude)}, ${"%.5f".format(tower.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "Last updated: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(tower.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun AlertCard(title: String, message: String, color: Color, onAction: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp)
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
            onAction?.let {
                Button(
                    onClick = it,
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Action", fontSize = 10.sp, color = Color.White)
                }
            }
        }
    }
}

fun getSignalColor(dbm: Int?): Color {
    return when {
        dbm == null -> Color.Gray
        dbm > -70 -> Color(0xFF2E7D32) // Green (Darker for better contrast)
        dbm > -90 -> Color(0xFFF57C00) // Orange/Amber (Much more readable than yellow)
        dbm > -110 -> Color(0xFFD32F2F) // Red
        else -> Color(0xFF7F0000) // Dark Red (Very Poor)
    }
}
