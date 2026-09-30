package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedReading
import com.compx551.rhythmrun.processing.model.ProcessedLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

/** Normalizes validated sensor data directly into the exercise event's reading list. */
class NormalizingHandler(
    private val processedReadings: MutableStateFlow<List<ProcessedReading>>,
) : ProcessingHandler() {

    override fun process(request: ProcessingRequest): Boolean {
        var heartRateBpm: Double? = null
        var accelerationPerSecond: Double? = null
        var velocityMetersPerSecond: Double? = null
        var stepCounterPerSecond: Double? = null
        var location: ProcessedLocation? = null

        for ((timestamp, readingsAtTimestamp) in request.readings
            .groupBy(RawReading::timestampMillis)
            .toSortedMap()
        ) {
            for (reading in readingsAtTimestamp) {
                when (reading) {
                    is RawReading.HeartRate -> heartRateBpm = reading.beatsPerMinute
                    /** Normalize acceleration to m/s² */
                    is RawReading.Acceleration -> {
                        val factor = when (reading.unit) {
                            AccelerationUnit.METERS_PER_SECOND_SQUARED -> 1.0
                            AccelerationUnit.STANDARD_GRAVITY -> 9.80665
                        }
                        val x = reading.x * factor
                        val y = reading.y * factor
                        val z = reading.z * factor
                        accelerationPerSecond = sqrt(x * x + y * y + z * z)
                    }
                    /** Normalize velocity to g */
                    is RawReading.Velocity -> velocityMetersPerSecond = when (reading.unit) {
                        VelocityUnit.METERS_PER_SECOND -> reading.value
                        VelocityUnit.KILOMETERS_PER_HOUR -> reading.value / 3.6
                    }
                    /** Normalize step count to per second */
                    is RawReading.StepCounter ->
                        stepCounterPerSecond = reading.stepsPerSecond.toDouble()
                    /** Normalize location to custom data structure */
                    is RawReading.Location -> location = ProcessedLocation(
                        latitude = reading.latitude,
                        longitude = reading.longitude,
                        accuracyMeters = reading.accuracyMeters,
                    )
                }
            }

            if (heartRateBpm != null &&
                accelerationPerSecond != null &&
                velocityMetersPerSecond != null &&
                stepCounterPerSecond != null
            ) {
                val processedReading = ProcessedReading(
                    timestampMillis = timestamp,
                    heartRateBpm = heartRateBpm,
                    accelerationPerSecond = accelerationPerSecond,
                    velocityMetersPerSecond = velocityMetersPerSecond,
                    stepCounterPerSecond = stepCounterPerSecond,
                    location = location,
                )
                processedReadings.update { currentReadings ->
                    currentReadings + processedReading
                }
            }
        }

        return true
    }
}
