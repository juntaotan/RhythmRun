package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.communication.RhythmReading
import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading
import com.compx551.rhythmrun.processing.processor.RawReading
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToLong

/**
 * Bridges incoming [RhythmReading] streams from the communication API to [RhythmProcessor].
 * Aggregates sensor data, derives velocity, and drives the processing pipeline.
 */
class LiveRunProcessor(
    private val baseline: EfficiencyBaseline = EfficiencyBaseline(
        averageSpeedMetersPerSecond = 2.5,
        averageHeartRateBpm = 140.0,
        historyCount = 0,
    ),
) {
    val processor = RhythmProcessor()
    val processedReadings: StateFlow<List<ProcessedReading>> = processor.processedReadings

    var currentSessionId: String? = null
        private set

    private var latestHr: Double? = null
    private var latestAccel: Triple<Double, Double, Double>? = null
    private var latestCadenceSpm: Float? = null
    private var latestLocation: Pair<Double, Double>? = null
    private var latestAccuracy: Double? = null
    private var previousStepCount: Long? = null
    private var previousStepTimestamp: Long? = null

    fun onReading(reading: RhythmReading) {
        if (currentSessionId != reading.sessionId) {
            currentSessionId = reading.sessionId
            latestHr = null
            latestAccel = null
            latestCadenceSpm = null
            latestLocation = null
            latestAccuracy = null
            previousStepCount = null
            previousStepTimestamp = null
        }
        val ts = reading.timestamp
        reading.heartRateBpm?.let { if (it > 0f) latestHr = it.toDouble() }
        if (reading.accelerationX != null && reading.accelerationY != null && reading.accelerationZ != null) {
            latestAccel = Triple(
                reading.accelerationX.toDouble(),
                reading.accelerationY.toDouble(),
                reading.accelerationZ.toDouble(),
            )
        }
        reading.cadenceStepsPerMinute?.let { if (it > 0f) latestCadenceSpm = it }
        reading.stepCount?.let { currentSteps ->
            val prevSteps = previousStepCount
            val prevTs = previousStepTimestamp
            if (prevSteps != null && prevTs != null && currentSteps > prevSteps && ts > prevTs) {
                val deltaSteps = currentSteps - prevSteps
                val deltaSeconds = (ts - prevTs) / 1000.0
                if (deltaSeconds > 0.0) {
                    val derivedSpm = ((deltaSteps / deltaSeconds) * 60.0).toFloat()
                    if (derivedSpm > 0f) {
                        latestCadenceSpm = derivedSpm
                    }
                }
            }
            previousStepCount = currentSteps
            previousStepTimestamp = ts
        }
        if (reading.latitude != null && reading.longitude != null) {
            latestLocation = reading.latitude to reading.longitude
            latestAccuracy = reading.accuracyMeters
        }

        val readings = buildList {
            latestHr?.let { add(RawReading.HeartRate(ts, it)) }
            latestAccel?.let { (x, y, z) -> add(RawReading.Acceleration(ts, x, y, z)) }
            latestCadenceSpm?.let { spm ->
                val sps = (spm / 60.0).roundToLong().coerceAtLeast(1L)
                add(RawReading.StepCounter(ts, sps))
                add(RawReading.Velocity(ts, (spm / 60.0) * DEFAULT_STRIDE_LENGTH_METERS))
            }
            latestLocation?.let { (lat, lon) ->
                add(RawReading.Location(ts, lat, lon, latestAccuracy ?: 0.0))
            }
        }

        if (readings.isNotEmpty()) {
            processor.process(readings, baseline)
        }
    }

    companion object {
        private const val DEFAULT_STRIDE_LENGTH_METERS = 0.75
    }
}
