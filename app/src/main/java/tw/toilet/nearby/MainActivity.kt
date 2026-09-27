package tw.toilet.nearby

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.events.MapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var toilets by mutableStateOf<List<Toilet>>(emptyList())
    private var location by mutableStateOf<Location?>(null)
    private var loading by mutableStateOf(true)
    private var loadError by mutableStateOf(false)
    private var permissionGranted by mutableStateOf(false)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location = it }
        }
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions.values.any { it } || hasLocationPermission()
        if (permissionGranted) startLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted = hasLocationPermission()
        Configuration.getInstance().userAgentValue = packageName
        Thread {
            val result = runCatching { ToiletRepository.load(applicationContext) }
            runOnUiThread {
                result.onSuccess { toilets = it }.onFailure { loadError = true }
                loading = false
            }
        }.start()
        val preferences = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            var mode by remember { mutableIntStateOf(preferences.getInt("display_mode", 0)) }
            var preset by remember { mutableIntStateOf(preferences.getInt("theme_preset", 0).coerceIn(0, 6)) }
            var hue by remember { mutableFloatStateOf(preferences.getFloat("theme_hue", 195f).coerceIn(0f, 360f)) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            BackHandler(showSettings) { showSettings = false }
            AppTheme(mode, hue, window) {
                val nearby = remember(toilets, location) {
                    location?.let { ToiletRepository.nearby(toilets, it) }.orEmpty()
                }
                if (showSettings) AppearanceSettings(
                    mode = mode, preset = preset, hue = hue,
                    onBack = { showSettings = false },
                    onModeChange = {
                        mode = it
                        preferences.edit().putInt("display_mode", it).apply()
                    },
                    onPresetChange = { choice, selectedHue ->
                        preset = choice
                        hue = selectedHue
                        preferences.edit().putInt("theme_preset", choice).putFloat("theme_hue", selectedHue).apply()
                    },
                    onHueChange = {
                        hue = it
                        preset = 6
                    },
                    onHueChangeFinished = {
                        preferences.edit().putInt("theme_preset", 6).putFloat("theme_hue", hue).apply()
                    },
                ) else ToiletApp(
                    toilets = toilets, nearby = nearby, location = location, loading = loading,
                    loadError = loadError, hasPermission = permissionGranted,
                    onRequestLocation = ::requestLocation,
                    onLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onBrowse = ::browse,
                    onSettings = { showSettings = true },
                )
            }
        }
        requestLocation()
    }

    override fun onStart() {
        super.onStart()
        permissionGranted = hasLocationPermission()
        if (permissionGranted) startLocation() else location = null
    }

    override fun onStop() {
        locationClient.removeLocationUpdates(locationCallback)
        super.onStop()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (hasLocationPermission()) startLocation()
        else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun startLocation() {
        if (!hasLocationPermission()) return
        try {
            if (location != null && !isRecent(location!!)) location = null
            locationClient.lastLocation.addOnSuccessListener { last ->
                if (location == null && last != null && isRecent(last)) location = last
            }
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { current -> if (current != null) location = current }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                .setMinUpdateIntervalMillis(5_000L).build()
            locationClient.removeLocationUpdates(locationCallback)
            locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            location = null
        }
    }

    private fun isRecent(value: Location) =
        SystemClock.elapsedRealtimeNanos() - value.elapsedRealtimeNanos in 0..300_000_000_000L

    private fun browse(toilet: Toilet) {
        val coordinates = "${toilet.latitude},${toilet.longitude}"
        val googleMaps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$coordinates"))
            .setPackage("com.google.android.apps.maps")
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=$coordinates"
        ))
        try {
            startActivity(googleMaps)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(browser)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "找不到可開啟地圖的應用程式", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun ToiletApp(
    toilets: List<Toilet>, nearby: List<NearbyToilet>, location: Location?, loading: Boolean, loadError: Boolean,
    hasPermission: Boolean, onRequestLocation: () -> Unit, onLocationSettings: () -> Unit,
    onBrowse: (Toilet) -> Unit, onSettings: () -> Unit,
) {
    var showMap by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<NearbyToilet?>(null) }
    val visibleSelection = selected?.let { item -> nearby.firstOrNull { it.toilet.id == item.toilet.id } ?: item }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().zIndex(1f).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("台南上廁所", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "設定",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showMap) OutlinedButton(onClick = { showMap = false }) { Text("清單") }
                else Button(onClick = { showMap = false }) { Text("清單") }
                if (showMap) Button(onClick = { showMap = true }) { Text("地圖") }
                else OutlinedButton(onClick = { showMap = true }) { Text("地圖") }
                Spacer(Modifier.weight(1f))
                if (!showMap) OutlinedButton(onClick = onRequestLocation) { Text("重整") }
            }
        }
        when {
            loading -> EmptyMessage("載入廁所資料中…")
            loadError -> EmptyMessage("廁所資料載入失敗，請重新開啟 App。")
            location == null -> Column(
                Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (hasPermission) "正在取得位置，請確認裝置定位已開啟。" else "允許定位後即可查看附近的廁所。",
                    color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = if (hasPermission) onLocationSettings else onRequestLocation) {
                    Text(if (hasPermission) "開啟定位設定" else "允許定位")
                }
            }
            nearby.isEmpty() -> EmptyMessage("附近沒有收錄的廁所。")
            showMap -> ToiletMap(toilets, location, visibleSelection,
                onSelect = { selected = it }, onCloseSelection = { selected = null }, onBrowse = onBrowse)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(nearby, key = { it.toilet.id }) { item ->
                    ToiletCard(item, onBrowse = { onBrowse(item.toilet) },
                        onClick = { selected = item; showMap = true },
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToiletCard(
    item: NearbyToilet, onClick: () -> Unit, onBrowse: () -> Unit,
    modifier: Modifier = Modifier, onClose: (() -> Unit)? = null,
) {
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(item.toilet.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "關閉廁所資訊" }) {
                        Text("×", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Text(item.toilet.region, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.toilet.categories.forEach { category ->
                    Badge(category, MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.distanceText, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Button(onClick = onBrowse) { Text("Google Maps 瀏覽") }
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = foreground, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToiletMap(
    toilets: List<Toilet>, location: Location, selected: NearbyToilet?,
    onSelect: (NearbyToilet) -> Unit, onCloseSelection: () -> Unit, onBrowse: (Toilet) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(15.0)
        }
    }
    var centered by remember { mutableStateOf(false) }
    var lastSelectedId by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<List<Toilet>>(emptyList()) }
    var detailsHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val coordinateGroups = remember(toilets) {
        toilets.groupBy { it.latitude to it.longitude }.values.toList()
    }
    val cachedClusterIcons = remember(mapView) { mutableMapOf<Int, android.graphics.drawable.Drawable>() }
    val drawMarkers: (MapView) -> Unit = { map ->
        map.overlays.clear()
        // 地圖空白處才會收到此事件；標記位於它的上層，仍可正常切換廁所卡片。
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean {
                val hadDetails = selected != null || selectedGroup.isNotEmpty()
                if (selected != null) onCloseSelection()
                selectedGroup = emptyList()
                return hadDetails
            }

            override fun longPressHelper(point: GeoPoint?): Boolean = false
        }))
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(location.latitude, location.longitude)
            title = "目前位置"
            icon = ContextCompat.getDrawable(context, R.drawable.ic_user_location)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> false }
        })
        clustersInView(map, coordinateGroups).forEach { cluster ->
            map.overlays.add(Marker(map).apply {
                position = cluster.position
                title = if (cluster.toilets.size == 1) cluster.toilets[0].name else "${cluster.toilets.size} 筆廁所"
                icon = if (cluster.toilets.size == 1) ContextCompat.getDrawable(context, R.drawable.ic_map_toilet)
                    else cachedClusterIcons.getOrPut(cluster.toilets.size) {
                        clusterIcon(map, cluster.toilets.size)
                    }.constantState?.newDrawable()?.mutate() ?: clusterIcon(map, cluster.toilets.size)
                setAnchor(Marker.ANCHOR_CENTER,
                    if (cluster.toilets.size == 1) Marker.ANCHOR_BOTTOM else Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    when {
                        cluster.toilets.size == 1 -> {
                            selectedGroup = emptyList()
                            val toilet = cluster.toilets[0]
                            val result = FloatArray(1)
                            Location.distanceBetween(location.latitude, location.longitude,
                                toilet.latitude, toilet.longitude, result)
                            onSelect(NearbyToilet(toilet, result[0]))
                            map.controller.animateTo(cluster.position)
                        }
                        !cluster.sameCoordinates && map.zoomLevelDouble < 19.5 -> {
                            map.controller.setZoom((map.zoomLevelDouble + 2).coerceAtMost(19.5))
                            map.controller.animateTo(cluster.position)
                        }
                        else -> {
                            onCloseSelection()
                            selectedGroup = cluster.toilets
                        }
                    }
                    true
                }
            })
        }
        map.invalidate()
    }
    val latestDrawMarkers by rememberUpdatedState(drawMarkers)
    DisposableEffect(mapView, lifecycleOwner) {
        val refresh = Runnable { latestDrawMarkers(mapView) }
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.removeCallbacks(refresh)
                mapView.postDelayed(refresh, 120)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                mapView.removeCallbacks(refresh)
                mapView.postDelayed(refresh, 120)
                return false
            }
        }
        mapView.addMapListener(mapListener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            mapView.removeCallbacks(refresh)
            mapView.removeMapListener(mapListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().clipToBounds(), update = { map ->
            if (!centered) {
                map.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                centered = true
            }
            if (map.width == 0 || map.height == 0) {
                map.post { if (map.isAttachedToWindow) latestDrawMarkers(map) }
            } else drawMarkers(map)
            if (selected?.toilet?.id != lastSelectedId) {
                lastSelectedId = selected?.toilet?.id
                selected?.let { map.controller.animateTo(GeoPoint(it.toilet.latitude, it.toilet.longitude)) }
            }
        })
        Text("© OpenStreetMap contributors", modifier = Modifier.align(Alignment.TopEnd)
            .padding(8.dp).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp), color = Color.Black,
            style = MaterialTheme.typography.labelSmall)
        if (selectedGroup.isNotEmpty()) {
            Card(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                .onSizeChanged { detailsHeightPx = it.height }) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedGroup.size} 筆廁所 · 點選查看", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { selectedGroup = emptyList() },
                            modifier = Modifier.semantics { contentDescription = "關閉廁所清單" }) {
                            Text("×", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(selectedGroup, key = { it.id }) { toilet ->
                            Column(Modifier.fillMaxWidth().clickable {
                                val result = FloatArray(1)
                                Location.distanceBetween(location.latitude, location.longitude,
                                    toilet.latitude, toilet.longitude, result)
                                onSelect(NearbyToilet(toilet, result[0]))
                                selectedGroup = emptyList()
                            }.padding(10.dp)) {
                                Text(toilet.name, fontWeight = FontWeight.SemiBold)
                                Text("${toilet.categories.joinToString("、")} · ${toilet.region}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else selected?.let { item ->
            ToiletCard(item, onClick = {}, onBrowse = { onBrowse(item.toilet) },
                onClose = onCloseSelection,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    .onSizeChanged { detailsHeightPx = it.height })
        }
        Button(onClick = {
            mapView.controller.setZoom(mapView.zoomLevelDouble.coerceAtLeast(19.0))
            mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
        }, modifier = Modifier.align(Alignment.BottomEnd).padding(
            end = 16.dp, bottom = if (selected != null || selectedGroup.isNotEmpty()) {
                with(density) { detailsHeightPx.toDp() } + 28.dp
            } else 16.dp,
        )) { Text("◎ 定位") }
    }
}
