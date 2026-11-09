package com.worklog.app

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.File

private val SEOUL = GeoPoint(37.5665, 126.9780)

private fun newMapView(ctx: Context, start: GeoPoint, zoom: Double): MapView {
    // OpenStreetMap 지도: API 키가 필요 없다. 타일 캐시는 앱 전용 폴더에 둔다
    Configuration.getInstance().apply {
        load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = ctx.packageName
        osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
    }
    return MapView(ctx).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        controller.setZoom(zoom)
        controller.setCenter(start)
    }
}

/**
 * 지도를 움직여 가운데 핀을 직장 위에 맞추는 화면.
 * 검색 결과를 누르면 그곳으로 이동하고, 감지 반경을 원으로 보여 준다.
 */
@Composable
fun MapPickerDialog(
    initialLat: Double?,
    initialLng: Double?,
    radius: Int,
    onDismiss: () -> Unit,
    onPick: (lat: Double, lng: Double, placeName: String?) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasInitial = initialLat != null && initialLng != null
    val mapView = remember {
        newMapView(ctx, if (hasInitial) GeoPoint(initialLat!!, initialLng!!) else SEOUL, if (hasInitial) 17.5 else 15.0)
    }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<GeoHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var placeName by remember { mutableStateOf<String?>(null) }
    var placeLabel by remember { mutableStateOf<String?>(null) }

    fun moveTo(lat: Double, lng: Double, zoom: Double = 18.0) {
        mapView.controller.setZoom(zoom)
        mapView.controller.animateTo(GeoPoint(lat, lng))
    }

    fun goToMyLocation() {
        Geo.currentLocation(ctx) { p ->
            if (p != null) moveTo(p.first, p.second, 17.5) else toast(ctx, "현재 위치를 가져오지 못했어요. GPS를 켜 주세요")
        }
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) goToMyLocation()
    }

    // 감지 반경 원: 지도를 움직일 때마다 가운데를 따라간다
    DisposableEffect(mapView) {
        val circle = Polygon(mapView).apply {
            fillPaint.color = 0x337B3FE4
            outlinePaint.color = 0xFF7B3FE4.toInt()
            outlinePaint.strokeWidth = 4f
            setOnClickListener { _, _, _ -> false }
        }
        fun update() {
            val c = mapView.mapCenter
            circle.points = Polygon.pointsAsCircle(GeoPoint(c.latitude, c.longitude), radius.toDouble())
            mapView.invalidate()
        }
        mapView.overlays.add(circle)
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                update()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                update()
                return false
            }
        })
        update()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // 위치가 아직 없으면 지금 있는 곳에서 시작한다
    LaunchedEffect(Unit) {
        if (!hasInitial && Perms.location(ctx)) goToMyLocation()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        scope.launch {
            hits = withContext(Dispatchers.IO) { Geo.search(ctx, q) }
            searching = false
            if (hits.isEmpty()) toast(ctx, "검색 결과가 없어요. 지도를 직접 움직여 맞춰 주세요")
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Ink) }
                    Text("직장 위치 고르기", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(query, { query = it }, "장소 이름이나 주소 (예: 웨스틴조선호텔)", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SoftButton(if (searching) "찾는 중" else "검색") { search() }
                }
                if (hits.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        hits.forEach { h ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        moveTo(h.lat, h.lng)
                                        placeName = h.name.ifBlank { null }
                                        placeLabel = h.label
                                        hits = emptyList()
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                if (h.name.isNotBlank()) Text(h.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(h.label, fontSize = 12.sp, color = Sub, maxLines = 2)
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    // 가운데 고정 핀 (핀 끝이 지도 가운데를 가리키도록 위로 올린다)
                    Icon(
                        Icons.Filled.LocationOn, null, tint = Purple,
                        modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-24).dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .shadow(4.dp, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .clickable {
                                if (Perms.location(ctx)) goToMyLocation()
                                else locLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("내 위치", color = Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    Text(
                        "© OpenStreetMap",
                        fontSize = 10.sp, color = Sub,
                        modifier = Modifier.align(Alignment.BottomStart).background(Color.White.copy(alpha = 0.7f)).padding(4.dp),
                    )
                }
                Column(Modifier.padding(16.dp)) {
                    Text(
                        placeLabel ?: "지도를 움직여 보라색 핀을 직장 입구에 맞춰 주세요. 원은 감지 반경(${radius}m)이에요.",
                        fontSize = 13.sp, color = if (placeLabel != null) Ink else Sub, maxLines = 2,
                    )
                    Spacer(Modifier.size(10.dp))
                    PrimaryButton("이 위치로 정하기", Modifier.fillMaxWidth()) {
                        val c = mapView.mapCenter
                        onPick(c.latitude, c.longitude, placeName)
                    }
                }
            }
        }
    }
}
