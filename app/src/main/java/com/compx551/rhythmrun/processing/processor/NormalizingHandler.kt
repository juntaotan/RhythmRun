package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedLocation
import com.compx551.rhythmrun.processing.model.ProcessedReading
import kotlin.math.sqrt

/** Converts one validated exercise event into a [ProcessedReading]. */
class NormalizingHandler {

    fun normalize(readings: List<RawReading>): ProcessedReading {
        var processedReading = ProcessedReading(
            timestampMillis = readings.maxOf(RawReading::timestampMillis),
            heartRateBpm = 0.0,
            accelerationPerSecond = 0.0,
            velocityMetersPerSecond = 0.0,
            stepCounterPerSecond = 0.0,
        )

        for (reading in readings.sortedBy(RawReading::timestampMillis)) {
            processedReading = when (reading) {
                is RawReading.HeartRate -> processedReading.copy(
                    heartRateBpm = reading.beatsPerMinute,
                )

                is RawReading.Acceleration -> {
                    val factor = when (reading.unit) {
                        AccelerationUnit.METERS_PER_SECOND_SQUARED -> 1.0
                        AccelerationUnit.STANDARD_GRAVITY -> 9.80665
                    }
                    val x = reading.x * factor
                    val y = reading.y * factor
                    val z = reading.z * factor
                    processedReading.copy(
                        accelerationPerSecond = sqrt(x * x + y * y + z * z),
                    )
                }

                is RawReading.Velocity -> processedReading.copy(
                    velocityMetersPerSecond = when (reading.unit) {
                        VelocityUnit.METERS_PER_SECOND -> reading.value
                        VelocityUnit.KILOMETERS_PER_HOUR -> reading.value / 3.6
                    },
                )

                is RawReading.StepCounter -> processedReading.copy(
                    stepCounterPerSecond = reading.stepsPerSecond.toDouble(),
                )

                is RawReading.Location -> processedReading.copy(
                    location = ProcessedLocation(
                        latitude = reading.latitude,
                        longitude = reading.longitude,
                        accuracyMeters = reading.accuracyMeters,
                    ),
                )
            }
        }

        return processedReading
    }
}
