package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.communication.RhythmReading
import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading
import com.compx551.rhythmrun.processing.processor.RawReading
import kotlinx.coroutines.flow.StateFlow

private const val CADENCE_MAX_AGE_MILLIS = 10_000L

/**
 * Bridges incoming [RhythmReading] streams from the communication API to [RhythmProcessor].
 * Aggregates sensor data, derives velocity, and drives the processing pipeline.
 */
class LiveRunProcessor(
    private var baseline: EfficiencyBaseline = EfficiencyBaseline(0.0, 0.0, 0),
) {
    val processor = RhythmProcessor()
    val processedReadings: StateFlow<List<ProcessedReading>> = processor.processedReadings

    var currentSessionId: String? = null
        private set

    private var latestHr: Double? = null
    private var latestAccel: Triple<Double, Double, Double>? = null
    private var latestDirectCadenceSpm: Float? = null
    private var latestDirectCadenceTimestamp: Long? = null
    private var latestDerivedCadenceSpm: Float? = null
    private var latestDerivedCadenceTimestamp: Long? = null
    private var latestLocation: Pair<Double, Double>? = null
    private var latestAccuracy: Double? = null
    private var previousStepCount: Long? = null
    private var previousStepTimestamp: Long? = null

    fun setBaseline(value: EfficiencyBaseline?) {
        baseline = value ?: EfficiencyBaseline(0.0, 0.0, 0)
    }

    fun onReading(reading: RhythmReading) {
        if (currentSessionId != reading.sessionId) {
            currentSessionId = reading.sessionId
            latestHr = null
            latestAccel = null
            latestDirectCadenceSpm = null
            latestDirectCadenceTimestamp = null
            latestDerivedCadenceSpm = null
            latestDerivedCadenceTimestamp = null
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
        reading.cadenceStepsPerMinute?.let {
            latestDirectCadenceSpm = it
            latestDirectCadenceTimestamp = ts
        }
        reading.stepCount?.let { currentSteps ->
            val prevSteps = previousStepCount
            val prevTs = previousStepTimestamp
            if (prevSteps != null && prevTs != null && currentSteps >= prevSteps && ts > prevTs) {
                latestDerivedCadenceSpm = ((currentSteps - prevSteps) * 60_000.0 / (ts - prevTs)).toFloat()
                latestDerivedCadenceTimestamp = ts
            }
            previousStepCount = currentSteps
            previousStepTimestamp = ts
        }
        if (reading.latitude != null && reading.longitude != null) {
            latestLocation = reading.latitude to reading.longitude
            latestAccuracy = reading.accuracyMeters
        }

        val cadenceSpm = when {
            latestDirectCadenceTimestamp?.let { ts - it <= CADENCE_MAX_AGE_MILLIS } == true -> latestDirectCadenceSpm
            latestDerivedCadenceTimestamp?.let { ts - it <= CADENCE_MAX_AGE_MILLIS } == true -> latestDerivedCadenceSpm
            latestDirectCadenceSpm != null || latestDerivedCadenceSpm != null -> 0f
            else -> null
        }
        val readings = buildList {
            latestHr?.let { add(RawReading.HeartRate(ts, it)) }
            latestAccel?.let { (x, y, z) -> add(RawReading.Acceleration(ts, x, y, z)) }
            cadenceSpm?.let { spm ->
                add(RawReading.StepCounter(ts, spm / 60.0))
                add(RawReading.Velocity(ts, (spm / 60.0) * EfficiencyBaseline.STRIDE_LENGTH_METERS))
            }
            latestLocation?.let { (lat, lon) ->
                add(RawReading.Location(ts, lat, lon, latestAccuracy ?: 0.0))
            }
        }

        if (readings.isNotEmpty()) {
            processor.process(readings, baseline)
        }
    }

}
