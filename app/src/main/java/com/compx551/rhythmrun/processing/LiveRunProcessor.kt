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

    private var latestHr: Double? = null
    private var latestAccel: Triple<Double, Double, Double>? = null
    private var latestCadenceSpm: Float? = null

    fun onReading(reading: RhythmReading) {
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

        val readings = buildList {
            latestHr?.let { add(RawReading.HeartRate(ts, it)) }
            latestAccel?.let { (x, y, z) -> add(RawReading.Acceleration(ts, x, y, z)) }
            latestCadenceSpm?.let { spm ->
                val sps = (spm / 60.0).roundToLong().coerceAtLeast(1L)
                add(RawReading.StepCounter(ts, sps))
                add(RawReading.Velocity(ts, (spm / 60.0) * DEFAULT_STRIDE_LENGTH_METERS))
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
