package com.compx551.rhythmrun.ui.runsession

import android.graphics.Paint
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.compx551.rhythmrun.processing.model.ProcessedLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun LiveRunMapCard(
    routePoints: List<ProcessedLocation>,
    currentLocation: ProcessedLocation?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    val polyline = remember {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.parseColor("#00B0FF")
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }

    val currentMarker = remember {
        mutableStateOf<Marker?>(null)
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewInstance?.onDetach()
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

                        val marker = Marker(this).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "Current Position"
                        }
                        currentMarker.value = marker
                        overlays.add(polyline)
                        overlays.add(marker)

                        currentLocation?.let {
                            val startPoint = GeoPoint(it.latitude, it.longitude)
                            controller.setCenter(startPoint)
                            marker.position = startPoint
                        }

                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    val geoPoints = routePoints.map { GeoPoint(it.latitude, it.longitude) }
                    polyline.setPoints(geoPoints)

                    currentLocation?.let {
                        val currentGeo = GeoPoint(it.latitude, it.longitude)
                        currentMarker.value?.let { marker ->
                            marker.position = currentGeo
                            if (!mapView.overlays.contains(marker)) {
                                mapView.overlays.add(marker)
                            }
                        }
                        mapView.controller.animateTo(currentGeo)
                    }

                    if (!mapView.overlays.contains(polyline)) {
                        mapView.overlays.add(0, polyline)
                    }
                    mapView.invalidate()
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
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (currentLocation != null) Color(0xFF00E676) else Color(0xFFFFB300),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = if (currentLocation != null) {
                            " GPS TRACKING · ${routePoints.size} PTS"
                        } else {
                            " WAITING FOR GPS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Recenter button
            if (currentLocation != null) {
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
                            val target = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                            mapViewInstance?.controller?.animateTo(target)
                            mapViewInstance?.controller?.setZoom(17.0)
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
