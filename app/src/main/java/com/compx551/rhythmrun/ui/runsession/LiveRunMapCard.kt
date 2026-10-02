package com.compx551.rhythmrun.ui.runsession

import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.compx551.rhythmrun.processing.model.ProcessedLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private class MapStateHolder {
    var mapView: MapView? = null
    var startMarker: Marker? = null
    var currentMarker: Marker? = null
    var polyline: Polyline? = null
    var lastRenderedPointsHash: Int = -1
    var lastRenderedLocation: ProcessedLocation? = null
    var hasFittedInitialBox: Boolean = false
}

private fun safeFitBoundingBox(
    mapView: MapView,
    geoPoints: List<GeoPoint>,
    paddingDp: Int = 40,
    animated: Boolean = false,
) {
    if (geoPoints.isEmpty()) return

    val paddingPx = (paddingDp * mapView.resources.displayMetrics.density).toInt()

    val applyZoom = {
        val availableWidth = mapView.width - 2 * paddingPx
        val availableHeight = mapView.height - 2 * paddingPx

        if (geoPoints.size == 1 || availableWidth <= 20 || availableHeight <= 20) {
            val center = geoPoints.first()
            mapView.controller.setCenter(center)
            mapView.controller.setZoom(16.5)
        } else {
            try {
                val box = BoundingBox.fromGeoPoints(geoPoints)
                if (box.latNorth == box.latSouth && box.lonEast == box.lonWest) {
                    mapView.controller.setCenter(box.centerWithDateLine)
                    mapView.controller.setZoom(16.5)
                } else {
                    mapView.zoomToBoundingBox(box, animated, paddingPx, 18.0, null)
                }
            } catch (_: Exception) {
                mapView.controller.setCenter(geoPoints.first())
            }
        }
        mapView.postInvalidate()
    }

    if (mapView.isLayoutOccurred && mapView.width > 2 * paddingPx && mapView.height > 2 * paddingPx) {
        applyZoom()
    } else {
        mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
            applyZoom()
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun LiveRunMapCard(
    routePoints: List<ProcessedLocation>,
    currentLocation: ProcessedLocation? = null,
    isLive: Boolean = true,
    customTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val holder = remember { MapStateHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.mapView?.onPause()
            holder.mapView?.onDetach()
            holder.mapView = null
        }
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        controller.setZoom(16.5)

                        // Prevent parent LazyColumn from stealing drag/pinch touch gestures
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }

                        val poly = Polyline().apply {
                            outlinePaint.color = android.graphics.Color.parseColor("#00B0FF")
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        holder.polyline = poly
                        overlays.add(poly)

                        val cMarker = Marker(this).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = if (isLive) "Current Position" else "Finish"
                        }
                        holder.currentMarker = cMarker
                        overlays.add(cMarker)

                        if (!isLive) {
                            val sMarker = Marker(this).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Start"
                            }
                            holder.startMarker = sMarker
                            overlays.add(sMarker)
                        }

                        currentLocation?.let {
                            val startPoint = GeoPoint(it.latitude, it.longitude)
                            controller.setCenter(startPoint)
                            cMarker.position = startPoint
                        }

                        holder.mapView = this
                    }
                },
                update = { mapView ->
                    val polyline = holder.polyline ?: return@AndroidView
                    val pointsHash = routePoints.hashCode()
                    val pointsChanged = pointsHash != holder.lastRenderedPointsHash
                    val locationChanged = currentLocation != holder.lastRenderedLocation

                    if (pointsChanged) {
                        holder.lastRenderedPointsHash = pointsHash
                        val geoPoints = routePoints.map { GeoPoint(it.latitude, it.longitude) }
                        polyline.setPoints(geoPoints)

                        if (!isLive) {
                            if (geoPoints.isNotEmpty()) {
                                holder.startMarker?.let { marker ->
                                    marker.position = geoPoints.first()
                                    if (!mapView.overlays.contains(marker)) {
                                        mapView.overlays.add(marker)
                                    }
                                }
                                holder.currentMarker?.let { marker ->
                                    marker.position = geoPoints.last()
                                    marker.title = "Finish"
                                    if (!mapView.overlays.contains(marker)) {
                                        mapView.overlays.add(marker)
                                    }
                                }
                                if (!holder.hasFittedInitialBox) {
                                    holder.hasFittedInitialBox = true
                                    safeFitBoundingBox(mapView, geoPoints, paddingDp = 40, animated = false)
                                }
                            } else {
                                holder.startMarker?.let { mapView.overlays.remove(it) }
                                holder.currentMarker?.let { mapView.overlays.remove(it) }
                            }
                        }
                        mapView.postInvalidate()
                    }

                    if (isLive && locationChanged) {
                        holder.lastRenderedLocation = currentLocation
                        currentLocation?.let {
                            val currentGeo = GeoPoint(it.latitude, it.longitude)
                            holder.currentMarker?.let { marker ->
                                marker.position = currentGeo
                                if (!mapView.overlays.contains(marker)) {
                                    mapView.overlays.add(marker)
                                }
                            }
                            mapView.controller.animateTo(currentGeo)
                            mapView.postInvalidate()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
            )

            // Top status overlay badge
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasData = if (isLive) currentLocation != null else routePoints.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (hasData) Color(0xFF00E676) else Color(0xFFFFB300),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = " " + (customTitle ?: when {
                            isLive && currentLocation != null -> "GPS TRACKING · ${routePoints.size} PTS"
                            isLive -> "WAITING FOR GPS"
                            routePoints.isNotEmpty() -> "ROUTE MAP · ${routePoints.size} PTS"
                            else -> "NO ROUTE RECORDED"
                        }),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Recenter / Fit button
            if (currentLocation != null || routePoints.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                ) {
                    IconButton(
                        onClick = {
                            val map = holder.mapView ?: return@IconButton
                            val geoPoints = routePoints.map { GeoPoint(it.latitude, it.longitude) }
                            if (isLive && currentLocation != null) {
                                val target = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                                map.controller.animateTo(target)
                                map.controller.setZoom(17.0)
                            } else if (geoPoints.isNotEmpty()) {
                                safeFitBoundingBox(map, geoPoints, paddingDp = 40, animated = true)
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = "⌖",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
