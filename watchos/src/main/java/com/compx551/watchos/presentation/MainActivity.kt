package com.compx551.watchos.presentation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.compx551.watchos.communication.RhythmProtocol
import com.compx551.watchos.communication.WatchDataSender
import com.compx551.watchos.communication.WatchSessionCommandService
import com.compx551.watchos.sensors.CapturePhase
import com.compx551.watchos.sensors.SensorCaptureManager
import com.compx551.watchos.sensors.SensorCaptureState
import com.compx551.watchos.storage.TemporarySessionStorage
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var captureState by mutableStateOf(SensorCaptureState())
    private var permissionMessage by mutableStateOf<String?>(null)
    private var showExerciseHistory by mutableStateOf(false)
    private var pendingSessionId: String? = null
    private var pendingResumeSessionId: String? = null
    private lateinit var sensorCaptureManager: SensorCaptureManager

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(WatchSessionCommandService.EXTRA_PATH)
            val sessionId = intent?.getStringExtra(WatchSessionCommandService.EXTRA_SESSION_ID)
            handleSessionCommand(path, sessionId)
        }
    }

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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val temporaryStorage = TemporarySessionStorage(this)
        val watchDataSender = WatchDataSender(this)
        sensorCaptureManager =
            SensorCaptureManager(this, temporaryStorage, watchDataSender) {
                captureState = it
                if (it.phase == CapturePhase.IDLE) {
                    pendingResumeSessionId?.let { id ->
                        pendingResumeSessionId = null
                        requestPermissionsAndStart(id)
                    }
                }
            }

        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            IntentFilter(WatchSessionCommandService.ACTION_SESSION_COMMAND),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            WearApp(
                state = captureState,
                permissionMessage = permissionMessage,
                onStart = {
                    pendingSessionId = null
                    requestPermissionsAndStart(null)
                },
                onStop = { sensorCaptureManager.stopCapture() },
                showExerciseHistory = showExerciseHistory,
                onViewExerciseHistory = { showExerciseHistory = true },
                onCloseExerciseHistory = { showExerciseHistory = false },
            )
        }

        val path = intent?.getStringExtra(WatchSessionCommandService.EXTRA_PATH)
        val sessionId = intent?.getStringExtra(WatchSessionCommandService.EXTRA_SESSION_ID)
        handleSessionCommand(path, sessionId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val path = intent.getStringExtra(WatchSessionCommandService.EXTRA_PATH)
        val sessionId = intent.getStringExtra(WatchSessionCommandService.EXTRA_SESSION_ID)
        handleSessionCommand(path, sessionId)
    }

    override fun onDestroy() {
        unregisterReceiver(commandReceiver)
        sensorCaptureManager.release()
        super.onDestroy()
    }

    private fun handleSessionCommand(path: String?, sessionId: String? = null) {
        if (!sessionId.isNullOrBlank()) {
            pendingSessionId = sessionId
        }
        when (path) {
            RhythmProtocol.SESSION_START_PATH, RhythmProtocol.SESSION_RESUME_PATH -> {
                if (captureState.phase == CapturePhase.IDLE) {
                    requestPermissionsAndStart(sessionId)
                } else if (path == RhythmProtocol.SESSION_RESUME_PATH && captureState.phase == CapturePhase.STOPPING) {
                    pendingResumeSessionId = sessionId
                }
            }
            RhythmProtocol.SESSION_PAUSE_PATH -> {
                pendingResumeSessionId = null
                if (captureState.phase != CapturePhase.IDLE) {
                    sensorCaptureManager.pauseCapture()
                }
            }
            RhythmProtocol.SESSION_STOP_PATH -> {
                pendingResumeSessionId = null
                sensorCaptureManager.stopCapture()
            }
        }
    }

    private fun requestPermissionsAndStart(sessionId: String? = pendingSessionId) {
        if (!sessionId.isNullOrBlank()) {
            pendingSessionId = sessionId
        }
        if (HeartRatePermission.isGranted(this)) {
            requestRemainingPermissionsAndStart(sessionId)
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

    private fun requestRemainingPermissionsAndStart(sessionId: String? = pendingSessionId) {
        val missing = nonHeartRatePermissions().filterNot(::isPermissionGranted)
        if (missing.isEmpty()) {
            startWithCurrentPermissions(sessionId)
        } else {
            sensorPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startWithCurrentPermissions(sessionId: String? = pendingSessionId) {
        val targetSessionId = sessionId ?: pendingSessionId
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
            sessionId = targetSessionId,
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
    showExerciseHistory: Boolean,
    onViewExerciseHistory: () -> Unit,
    onCloseExerciseHistory: () -> Unit,
) {
    RhythmRunTheme {
        AppScaffold {
            if (showExerciseHistory) {
                ExerciseHistoryScreen(
                    sessions = demoExerciseHistory,
                    onBack = onCloseExerciseHistory,
                )
                return@AppScaffold
            }
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
                    item {
                        Button(
                            onClick = onViewExerciseHistory,
                            modifier =
                                Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Exercise history")
                        }
                    }
                }
            }
        }
    }
}

private data class ExerciseHistoryItem(
    val dateLabel: String,
    val durationMinutes: Int,
    val distanceKilometres: Double,
    val steps: Int,
    val averageCadence: Int,
    val averageHeartRate: Int,
    val maximumHeartRate: Int,
)

private val demoExerciseHistory =
    listOf(
        ExerciseHistoryItem("Today · 18:20", 32, 5.4, 6_420, 158, 146, 178),
        ExerciseHistoryItem("28 Sep · 07:35", 25, 4.1, 5_080, 154, 139, 169),
        ExerciseHistoryItem("26 Sep · 17:50", 41, 7.0, 8_230, 160, 151, 184),
        ExerciseHistoryItem("24 Sep · 06:55", 29, 4.8, 5_910, 156, 143, 174),
        ExerciseHistoryItem("21 Sep · 09:10", 53, 9.2, 10_740, 162, 154, 188),
    )

@Composable
private fun ExerciseHistoryScreen(
    sessions: List<ExerciseHistoryItem>,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("Exercise history")
                }
            }
            item { SensorLine("Preview", "Latest five exercises", transformationSpec) }
            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("Back")
                }
            }
            sessions.forEach { session ->
                item {
                    SensorLine(
                        label = session.dateLabel,
                        value =
                            "${session.durationMinutes} min · ${session.distanceKilometres} km\n" +
                                "${String.format(Locale.US, "%,d", session.steps)} steps · " +
                                "${session.averageCadence} spm\n" +
                                "HR ${session.averageHeartRate} avg · ${session.maximumHeartRate} max",
                        transformationSpec = transformationSpec,
                    )
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
        showExerciseHistory = false,
        onViewExerciseHistory = {},
        onCloseExerciseHistory = {},
    )
}
