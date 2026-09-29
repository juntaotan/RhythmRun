package com.compx551.watchos.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.compx551.watchos.presentation.theme.RhythmRunTheme
import com.compx551.watchos.permissions.HeartRatePermission
import com.compx551.watchos.sensors.CapturePhase
import com.compx551.watchos.sensors.SensorCaptureManager
import com.compx551.watchos.sensors.SensorCaptureState
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var captureState by mutableStateOf(SensorCaptureState())
    private var permissionMessage by mutableStateOf<String?>(null)
    private lateinit var sensorCaptureManager: SensorCaptureManager

    private val heartRatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            requestRemainingPermissionsAndStart()
        }

    private val sensorPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startWithCurrentPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorCaptureManager = SensorCaptureManager(this) { captureState = it }
        setContent {
            WearApp(
                state = captureState,
                permissionMessage = permissionMessage,
                onStart = ::requestPermissionsAndStart,
                onStop = sensorCaptureManager::stopCapture,
            )
        }
    }

    override fun onDestroy() {
        sensorCaptureManager.release()
        super.onDestroy()
    }

    private fun requestPermissionsAndStart() {
        if (HeartRatePermission.isGranted(this)) {
            requestRemainingPermissionsAndStart()
            return
        }

        permissionMessage =
            if (HeartRatePermission.shouldShowRationale(this)) {
                "Heart rate is used to show your response during each run stage."
            } else {
                null
            }
        heartRatePermissionLauncher.launch(HeartRatePermission.permissionForCurrentDevice())
    }

    private fun requestRemainingPermissionsAndStart() {
        val missing = nonHeartRatePermissions().filterNot(::isPermissionGranted)
        if (missing.isEmpty()) {
            startWithCurrentPermissions()
        } else {
            sensorPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startWithCurrentPermissions() {
        val heartRateGranted = HeartRatePermission.isGranted(this)
        val activityGranted = isPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)
        val locationGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val denied =
            buildList {
                if (!heartRateGranted) add("heart rate")
                if (!activityGranted) add("steps/cadence")
                if (!locationGranted) add("GPS")
            }
        permissionMessage =
            denied.takeIf { it.isNotEmpty() }?.joinToString(
                prefix = "Unavailable without permission: ",
            )
        sensorCaptureManager.startCapture(
            heartRatePermissionGranted = heartRateGranted,
            activityPermissionGranted = activityGranted,
            fineLocationPermissionGranted = locationGranted,
        )
    }

    private fun nonHeartRatePermissions(): List<String> =
        listOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

}

@Composable
fun WearApp(
    state: SensorCaptureState,
    permissionMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    RhythmRunTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                    item {
                        ListHeader(
                            modifier =
                                Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("RhythmRun sensors")
                        }
                    }
                    item { SensorLine("State", state.status, transformationSpec) }
                    permissionMessage?.let { message ->
                        item { SensorLine("Permissions", message, transformationSpec) }
                    }
                    state.error?.let { error ->
                        item { SensorLine("Error", error, transformationSpec) }
                    }
                    item {
                        val acceleration = state.acceleration
                        SensorLine(
                            "Accelerometer",
                            if (acceleration == null) {
                                "Waiting"
                            } else {
                                String.format(
                                    Locale.US,
                                    "x %.2f  y %.2f  z %.2f m/s²",
                                    acceleration.xMetersPerSecondSquared,
                                    acceleration.yMetersPerSecondSquared,
                                    acceleration.zMetersPerSecondSquared,
                                )
                            },
                            transformationSpec,
                        )
                    }
                    item {
                        SensorLine(
                            "Heart rate",
                            state.heartRateBpm?.let {
                                "${it.toInt()} bpm (${state.heartRateSource?.displayName})"
                            }
                                ?: "Waiting",
                            transformationSpec,
                        )
                    }
                    item {
                        SensorLine(
                            "Steps / cadence",
                            "${state.exerciseSteps} steps · " +
                                (state.exerciseCadenceSpm?.let { "$it spm" } ?: "cadence waiting"),
                            transformationSpec,
                        )
                    }
                    item {
                        val location = state.location
                        SensorLine(
                            "GPS",
                            if (location == null) {
                                "Waiting for fix"
                            } else {
                                String.format(
                                    Locale.US,
                                    "%.5f, %.5f · ±%.0f m",
                                    location.latitudeDegrees,
                                    location.longitudeDegrees,
                                    location.horizontalAccuracyMeters ?: Double.NaN,
                                )
                            },
                            transformationSpec,
                        )
                    }
                    if (state.unavailableMetrics.isNotEmpty()) {
                        item {
                            SensorLine(
                                "Unavailable",
                                state.unavailableMetrics.joinToString(),
                                transformationSpec,
                            )
                        }
                    }
                    item {
                        Button(
                            onClick =
                                if (state.phase == CapturePhase.IDLE) onStart else onStop,
                            modifier =
                                Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text(if (state.phase == CapturePhase.IDLE) "Start capture" else "Stop capture")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun SensorLine(
    label: String,
    value: String,
    transformationSpec: TransformationSpec,
) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@WearPreviewDevices
@Composable
fun DefaultPreview() {
    WearApp(
        state = SensorCaptureState(),
        permissionMessage = null,
        onStart = {},
        onStop = {},
    )
}
