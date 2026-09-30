package com.compx551.watchos.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseEvent
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAccuracy
import com.compx551.watchos.storage.TemporarySessionStorage

/** Latest values from the four capture sources retained by RhythmRun. */
data class SensorCaptureState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val acceleration: AccelerationReading? = null,
    val heartRateBpm: Double? = null,
    val heartRateSource: HeartRateSource? = null,
    val exerciseSteps: Long = 0,
    val exerciseCadenceSpm: Long? = null,
    val location: LocationReading? = null,
    val supportedExerciseMetrics: Set<String> = emptySet(),
    val unavailableMetrics: Set<String> = emptySet(),
    val status: String = "Ready",
    val error: String? = null,
)

enum class CapturePhase { IDLE, MEASURING_HEART_RATE, STARTING_EXERCISE, EXERCISING, STOPPING }

enum class HeartRateSource(val displayName: String) {
    SENSOR_MANAGER("watch sensor"),
    MEASURE_CLIENT("Health Services measure"),
    EXERCISE_CLIENT("Health Services exercise"),
}

data class AccelerationReading(
    val timestampNanosSinceBoot: Long,
    val xMetersPerSecondSquared: Float,
    val yMetersPerSecondSquared: Float,
    val zMetersPerSecondSquared: Float,
)

data class LocationReading(
    val timestampNanosSinceBoot: Long,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val horizontalAccuracyMeters: Double?,
)

/**
 * Owns registration and cleanup for the watch accelerometer and Health Services clients.
 *
 * A session begins with a short MeasureClient heart-rate sample. It then hands over to a RUNNING
 * ExerciseClient session for continuous heart rate, steps/cadence, and GPS. Unsupported exercise
 * data types are omitted after a runtime capability check and surfaced in [SensorCaptureState].
 */
class SensorCaptureManager(
    context: Context,
    private val temporaryStorage: TemporarySessionStorage,
    private val onStateChanged: (SensorCaptureState) -> Unit,
) : SensorEventListener {
    private val applicationContext = context.applicationContext
    private val sensorManager =
        applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val healthServicesClient = HealthServices.getClient(applicationContext)
    private val measureClient = healthServicesClient.measureClient
    private val exerciseClient = healthServicesClient.exerciseClient
    private val mainExecutor = ContextCompat.getMainExecutor(applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val useEmulatorHeartRateSensor =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk_gwear", ignoreCase = true)

    private var state = SensorCaptureState()
    private var captureRequested = false
    private var measureRegistrationRequested = false
    private var measureRegistered = false
    private var exerciseCallbackRegistered = false
    private var exerciseStarted = false
    private var firstMeasureHeartRateAtMillis: Long? = null
    private var intervalStepTotal = 0L
    private var heartRatePermissionGranted = false
    private var activityPermissionGranted = false
    private var fineLocationPermissionGranted = false
    private var emulatorHeartRateReceived = false
    private var currentSessionId: String? = null
    private var storageSequence = 0L

    private val measureHandoff = Runnable { finishMeasureAndStartExercise() }

    private val measureCallback =
        object : MeasureCallback {
            override fun onRegistered() {
                measureRegistered = true
                updateState { it.copy(status = "Heart-rate measurement registered") }
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                measureRegistrationRequested = false
                measureRegistered = false
                updateState {
                    it.copy(
                        status = "Short heart-rate measurement unavailable; starting exercise",
                        unavailableMetrics = it.unavailableMetrics + "measure heart rate",
                        error = throwable.message,
                    )
                }
                handler.removeCallbacks(measureHandoff)
                startExerciseCapture()
            }

            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability,
            ) {
                updateState { it.copy(status = "Heart rate: $availability") }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val reading = data.getData(DataType.HEART_RATE_BPM).lastOrNull() ?: return
                if (!emulatorHeartRateReceived) {
                    saveHeartRate(reading.value, HeartRateSource.MEASURE_CLIENT)
                }
                updateState {
                    it.copy(
                        heartRateBpm =
                            if (emulatorHeartRateReceived) it.heartRateBpm else reading.value,
                        heartRateSource =
                            if (emulatorHeartRateReceived) {
                                it.heartRateSource
                            } else {
                                HeartRateSource.MEASURE_CLIENT
                            },
                        status = "Initial heart rate received",
                    )
                }

                if (firstMeasureHeartRateAtMillis == null) {
                    firstMeasureHeartRateAtMillis = SystemClock.elapsedRealtime()
                    val elapsed = firstMeasureHeartRateAtMillis!! - measureWindowStartedAtMillis
                    val remainingUntilTimeout = (MEASURE_TOTAL_TIMEOUT_MILLIS - elapsed).coerceAtLeast(0)
                    handler.removeCallbacks(measureHandoff)
                    handler.postDelayed(
                        measureHandoff,
                        minOf(MEASURE_AFTER_FIRST_READING_MILLIS, remainingUntilTimeout),
                    )
                }
            }
        }

    private val exerciseCallback =
        object : ExerciseUpdateCallback {
            override fun onRegistered() {
                exerciseCallbackRegistered = true
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                exerciseCallbackRegistered = false
                failCapture("Exercise callback registration failed", throwable)
            }

            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                val metrics = update.latestMetrics
                val heartRate = metrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value
                val cadence = metrics.getData(DataType.STEPS_PER_MINUTE).lastOrNull()?.value
                val cumulativeSteps = metrics.getData(DataType.STEPS_TOTAL)?.total
                val intervalSteps = metrics.getData(DataType.STEPS).sumOf { it.value }
                if (cumulativeSteps == null && intervalSteps > 0L) {
                    intervalStepTotal += intervalSteps
                }
                val locationPoint = metrics.getData(DataType.LOCATION).lastOrNull()
                val location =
                    locationPoint?.let { point ->
                        val accuracy = point.accuracy as? LocationAccuracy
                        LocationReading(
                            timestampNanosSinceBoot = point.timeDurationFromBoot.toNanos(),
                            latitudeDegrees = point.value.latitude,
                            longitudeDegrees = point.value.longitude,
                            horizontalAccuracyMeters = accuracy?.horizontalPositionErrorMeters,
                        )
                    }

                if (heartRate != null && !emulatorHeartRateReceived) {
                    saveHeartRate(heartRate, HeartRateSource.EXERCISE_CLIENT)
                }
                val steps = cumulativeSteps ?: intervalStepTotal
                if (cumulativeSteps != null || intervalSteps > 0L || cadence != null) {
                    currentSessionId?.let { sessionId ->
                        temporaryStorage.saveSteps(
                            sessionId = sessionId,
                            sequence = nextStorageSequence(),
                            timestampNanosSinceBoot = SystemClock.elapsedRealtimeNanos(),
                            cumulativeSteps = steps,
                            cadenceStepsPerMinute = cadence,
                        )
                    }
                }
                if (location != null) {
                    currentSessionId?.let { sessionId ->
                        temporaryStorage.saveLocation(
                            sessionId = sessionId,
                            sequence = nextStorageSequence(),
                            reading = location,
                        )
                    }
                }

                updateState { current ->
                    val acceptHealthServicesHeartRate =
                        heartRate != null && !emulatorHeartRateReceived
                    current.copy(
                        phase = CapturePhase.EXERCISING,
                        heartRateBpm =
                            if (acceptHealthServicesHeartRate) heartRate else current.heartRateBpm,
                        heartRateSource =
                            if (acceptHealthServicesHeartRate) HeartRateSource.EXERCISE_CLIENT
                            else current.heartRateSource,
                        exerciseSteps = steps,
                        exerciseCadenceSpm = cadence ?: current.exerciseCadenceSpm,
                        location = location ?: current.location,
                        status = "Exercise ${update.exerciseStateInfo.state}",
                    )
                }
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

            override fun onAvailabilityChanged(
                dataType: DataType<*, *>,
                availability: Availability,
            ) {
                updateState { it.copy(status = "${dataType.name}: $availability") }
            }

            override fun onExerciseEventReceived(event: ExerciseEvent) = Unit
        }

    private var measureWindowStartedAtMillis = 0L

    fun startCapture(
        heartRatePermissionGranted: Boolean,
        activityPermissionGranted: Boolean,
        fineLocationPermissionGranted: Boolean,
    ) {
        if (captureRequested) return
        this.heartRatePermissionGranted = heartRatePermissionGranted
        this.activityPermissionGranted = activityPermissionGranted
        this.fineLocationPermissionGranted = fineLocationPermissionGranted
        captureRequested = true
        storageSequence = 0L
        currentSessionId = temporaryStorage.beginSession()
        intervalStepTotal = 0L
        firstMeasureHeartRateAtMillis = null
        emulatorHeartRateReceived = false
        updateState {
            SensorCaptureState(
                phase = CapturePhase.MEASURING_HEART_RATE,
                status = "Checking heart-rate measurement capability",
                unavailableMetrics =
                    if (accelerometer == null) setOf("accelerometer") else emptySet(),
            )
        }
        registerAccelerometer()
        registerRawHeartRateSensor()

        if (!heartRatePermissionGranted) {
            updateState {
                it.copy(
                    unavailableMetrics = it.unavailableMetrics + "heart rate (permission denied)",
                    status = "Heart-rate permission denied; starting remaining sensors",
                )
            }
            startExerciseCapture()
            return
        }

        val capabilitiesFuture = measureClient.getCapabilitiesAsync()
        capabilitiesFuture.addListener(
            {
                if (!captureRequested) return@addListener
                try {
                    val supported = capabilitiesFuture.get().supportedDataTypesMeasure
                    if (DataType.HEART_RATE_BPM in supported) {
                        measureWindowStartedAtMillis = SystemClock.elapsedRealtime()
                        measureRegistrationRequested = true
                        measureClient.registerMeasureCallback(
                            DataType.HEART_RATE_BPM,
                            mainExecutor,
                            measureCallback,
                        )
                        handler.postDelayed(measureHandoff, MEASURE_TOTAL_TIMEOUT_MILLIS)
                    } else {
                        updateState {
                            it.copy(
                                unavailableMetrics = it.unavailableMetrics + "measure heart rate",
                                status = "MeasureClient heart rate unsupported; starting exercise",
                            )
                        }
                        startExerciseCapture()
                    }
                } catch (throwable: Throwable) {
                    updateState {
                        it.copy(
                            unavailableMetrics = it.unavailableMetrics + "measure heart rate",
                            status = "MeasureClient unavailable; starting exercise",
                            error = throwable.cause?.message ?: throwable.message,
                        )
                    }
                    startExerciseCapture()
                }
            },
            mainExecutor,
        )
    }

    fun stopCapture() {
        if (!captureRequested && !exerciseStarted) return
        captureRequested = false
        handler.removeCallbacks(measureHandoff)
        sensorManager.unregisterListener(this)
        unregisterMeasureCallback()
        updateState { it.copy(phase = CapturePhase.STOPPING, status = "Stopping capture") }

        if (exerciseStarted) {
            val endFuture = exerciseClient.endExerciseAsync()
            endFuture.addListener(
                {
                    exerciseStarted = false
                    clearExerciseCallback()
                    val error = futureError(endFuture)
                    updateState {
                        it.copy(
                            phase = CapturePhase.IDLE,
                            status = "Capture stopped",
                            error = error,
                        )
                    }
                    if (error == null) finishTemporarySession() else interruptTemporarySession()
                },
                mainExecutor,
            )
        } else {
            clearExerciseCallback()
            updateState { it.copy(phase = CapturePhase.IDLE, status = "Capture stopped") }
            finishTemporarySession()
        }
    }

    fun release() {
        stopCapture()
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!captureRequested) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val reading =
                    AccelerationReading(
                        timestampNanosSinceBoot = event.timestamp,
                        xMetersPerSecondSquared = event.values[0],
                        yMetersPerSecondSquared = event.values[1],
                        zMetersPerSecondSquared = event.values[2],
                    )
                currentSessionId?.let { sessionId ->
                    temporaryStorage.saveAccelerometer(
                        sessionId = sessionId,
                        sequence = nextStorageSequence(),
                        reading = reading,
                    )
                }
                updateState { it.copy(acceleration = reading) }
            }

            Sensor.TYPE_HEART_RATE -> {
                if (
                    event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                        event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT
                ) {
                    return
                }
                val bpm = event.values.firstOrNull()?.toDouble() ?: return
                if (!bpm.isFinite() || bpm <= 0.0) return
                emulatorHeartRateReceived = true
                saveHeartRate(bpm, HeartRateSource.SENSOR_MANAGER)
                updateState {
                    it.copy(
                        heartRateBpm = bpm,
                        heartRateSource = HeartRateSource.SENSOR_MANAGER,
                        status = "Heart rate updated from watch sensor",
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerAccelerometer() {
        val sensor = accelerometer ?: return
        sensorManager.registerListener(this, sensor, ACCELEROMETER_PERIOD_MICROS)
    }

    /**
     * Registers the raw watch heart-rate sensor used by the emulator's Extended Controls slider.
     * Health Services remains registered separately and is the primary production data source.
     */
    private fun registerRawHeartRateSensor() {
        if (!heartRatePermissionGranted || !useEmulatorHeartRateSensor) return
        val sensor = heartRateSensor ?: return
        val registered =
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
            )
        if (!registered) {
            updateState {
                it.copy(
                    unavailableMetrics = it.unavailableMetrics + "raw heart-rate sensor",
                )
            }
        }
    }

    private fun finishMeasureAndStartExercise() {
        if (!captureRequested) return
        handler.removeCallbacks(measureHandoff)
        unregisterMeasureCallback()
        startExerciseCapture()
    }

    private fun unregisterMeasureCallback() {
        if (!measureRegistrationRequested && !measureRegistered) return
        measureRegistrationRequested = false
        measureRegistered = false
        measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, measureCallback)
    }

    private fun startExerciseCapture() {
        if (!captureRequested || state.phase == CapturePhase.STARTING_EXERCISE || exerciseStarted) {
            return
        }
        updateState { it.copy(phase = CapturePhase.STARTING_EXERCISE, status = "Checking exercise capabilities") }

        val capabilitiesFuture = exerciseClient.getCapabilitiesAsync()
        capabilitiesFuture.addListener(
            {
                if (!captureRequested) return@addListener
                try {
                    val capabilities = capabilitiesFuture.get()
                    if (ExerciseType.RUNNING !in capabilities.supportedExerciseTypes) {
                        failCapture("Running exercises are not supported on this watch")
                        return@addListener
                    }

                    val supported =
                        capabilities
                            .getExerciseTypeCapabilities(ExerciseType.RUNNING)
                            .supportedDataTypes
                    val desired = linkedSetOf<DataType<*, *>>()
                    if (heartRatePermissionGranted) desired += DataType.HEART_RATE_BPM
                    if (activityPermissionGranted) {
                        desired += DataType.STEPS
                        desired += DataType.STEPS_TOTAL
                        desired += DataType.STEPS_PER_MINUTE
                    }
                    if (fineLocationPermissionGranted) desired += DataType.LOCATION
                    val requested = desired.filterTo(linkedSetOf()) { it in supported }
                    val unavailable = (desired - requested).mapTo(linkedSetOf()) { it.name }
                    val gpsEnabled = DataType.LOCATION in requested

                    if (requested.isEmpty()) {
                        failCapture("No permitted Health Services exercise metrics are available")
                        return@addListener
                    }

                    updateState {
                        it.copy(
                            supportedExerciseMetrics = requested.mapTo(linkedSetOf()) { type -> type.name },
                            unavailableMetrics = it.unavailableMetrics + unavailable,
                            status = "Starting Health Services exercise",
                            error = null,
                        )
                    }

                    exerciseClient.setUpdateCallback(mainExecutor, exerciseCallback)
                    exerciseCallbackRegistered = true
                    val config =
                        ExerciseConfig.builder(ExerciseType.RUNNING)
                            .setDataTypes(requested)
                            .setIsAutoPauseAndResumeEnabled(false)
                            .setIsGpsEnabled(gpsEnabled)
                            .build()
                    val startFuture = exerciseClient.startExerciseAsync(config)
                    startFuture.addListener(
                        {
                            val error = futureError(startFuture)
                            if (error != null) {
                                failCapture("Unable to start Health Services exercise: $error")
                            } else if (!captureRequested) {
                                exerciseClient.endExerciseAsync()
                            } else {
                                exerciseStarted = true
                                updateState {
                                    it.copy(
                                        phase = CapturePhase.EXERCISING,
                                        status = "Sensors registered and exercise active",
                                        error = null,
                                    )
                                }
                            }
                        },
                        mainExecutor,
                    )
                } catch (throwable: Throwable) {
                    failCapture("Unable to query exercise capabilities", throwable)
                }
            },
            mainExecutor,
        )
    }

    private fun clearExerciseCallback() {
        if (!exerciseCallbackRegistered) return
        exerciseCallbackRegistered = false
        exerciseClient.clearUpdateCallbackAsync(exerciseCallback)
    }

    private fun failCapture(message: String, throwable: Throwable? = null) {
        captureRequested = false
        handler.removeCallbacks(measureHandoff)
        sensorManager.unregisterListener(this)
        unregisterMeasureCallback()
        clearExerciseCallback()
        interruptTemporarySession()
        updateState {
            it.copy(
                phase = CapturePhase.IDLE,
                status = message,
                error = throwable?.cause?.message ?: throwable?.message,
            )
        }
    }

    private fun updateState(transform: (SensorCaptureState) -> SensorCaptureState) {
        state = transform(state)
        onStateChanged(state)
    }

    private fun saveHeartRate(beatsPerMinute: Double, source: HeartRateSource) {
        currentSessionId?.let { sessionId ->
            temporaryStorage.saveHeartRate(
                sessionId = sessionId,
                sequence = nextStorageSequence(),
                timestampNanosSinceBoot = SystemClock.elapsedRealtimeNanos(),
                beatsPerMinute = beatsPerMinute,
                source = source,
            )
        }
    }

    private fun finishTemporarySession() {
        val sessionId = currentSessionId ?: return
        currentSessionId = null
        temporaryStorage.finishSession(sessionId)
    }

    private fun interruptTemporarySession() {
        val sessionId = currentSessionId ?: return
        currentSessionId = null
        temporaryStorage.interruptSession(sessionId)
    }

    private fun nextStorageSequence(): Long = storageSequence++

    private fun futureError(future: java.util.concurrent.Future<*>): String? =
        try {
            future.get()
            null
        } catch (throwable: Throwable) {
            throwable.cause?.message ?: throwable.message ?: throwable.javaClass.simpleName
        }

    private companion object {
        const val ACCELEROMETER_PERIOD_MICROS = 50_000 // 20 Hz target.
        const val MEASURE_AFTER_FIRST_READING_MILLIS = 15_000L
        const val MEASURE_TOTAL_TIMEOUT_MILLIS = 20_000L
    }
}
