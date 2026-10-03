package com.compx551.rhythmrun.processing.processor

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Replaces invalid sensor values without rejecting the rest of the exercise event. */
class ValidationHandler {
    private var previousLocation: RawReading.Location? = null

    fun validate(readings: List<RawReading>): List<RawReading> {
        require(readings.isNotEmpty()) { "Exercise event must contain sensor readings" }
        var latestValidLocation = previousLocation

        val validatedReadings = readings.map { reading ->
            val timestamp = reading.timestampMillis.coerceAtLeast(0L)
            when (reading) {
                is RawReading.HeartRate -> reading.copy(
                    timestampMillis = timestamp,
                    beatsPerMinute = reading.beatsPerMinute.takeIf {
                        it.isFinite() && it in 20.0..250.0
                    } ?: 0.0,
                )

                is RawReading.Acceleration -> {
                    val limit = when (reading.unit) {
                        AccelerationUnit.METERS_PER_SECOND_SQUARED -> 200.0
                        AccelerationUnit.STANDARD_GRAVITY -> 20.0
                    }
                    val isValid = listOf(reading.x, reading.y, reading.z).all {
                        it.isFinite() && it in -limit..limit
                    }
                    reading.copy(
                        timestampMillis = timestamp,
                        x = if (isValid) reading.x else 0.0,
                        y = if (isValid) reading.y else 0.0,
                        z = if (isValid) reading.z else 0.0,
                    )
                }

                is RawReading.Velocity -> {
                    val limit = when (reading.unit) {
                        VelocityUnit.METERS_PER_SECOND -> 25.0
                        VelocityUnit.KILOMETERS_PER_HOUR -> 90.0
                    }
                    reading.copy(
                        timestampMillis = timestamp,
                        value = reading.value.takeIf {
                            it.isFinite() && it in 0.0..limit
                        } ?: 0.0,
                    )
                }

                is RawReading.StepCounter -> reading.copy(
                    timestampMillis = timestamp,
                    stepsPerSecond = reading.stepsPerSecond.coerceAtLeast(0L),
                )

                is RawReading.Location -> {
                    val hasValidValues =
                        reading.latitude.isFinite() && reading.latitude in -90.0..90.0 &&
                            reading.longitude.isFinite() && reading.longitude in -180.0..180.0 &&
                            reading.accuracyMeters.isFinite() &&
                            reading.accuracyMeters in 0.0..MAXIMUM_ACCURACY_METERS
                    val hasPossibleSpeed = hasValidValues && hasPossibleSpeed(
                        previous = latestValidLocation,
                        current = reading,
                    )

                    if (hasPossibleSpeed) {
                        reading.copy(timestampMillis = timestamp).also {
                            latestValidLocation = it
                        }
                    } else {
                        RawReading.Location(
                            timestampMillis = timestamp,
                            latitude = 0.0,
                            longitude = 0.0,
                            accuracyMeters = 0.0,
                        )
                    }
                }
            }
        }

        previousLocation = latestValidLocation
        return validatedReadings
    }

    private fun hasPossibleSpeed(
        previous: RawReading.Location?,
        current: RawReading.Location,
    ): Boolean {
        if (previous == null) return true
        val elapsedSeconds = (current.timestampMillis - previous.timestampMillis) / 1_000.0
        if (elapsedSeconds <= 0.0) return false
        val speedMetersPerSecond = distanceMeters(previous, current) / elapsedSeconds
        return speedMetersPerSecond.isFinite() &&
            speedMetersPerSecond <= MAXIMUM_SPEED_METERS_PER_SECOND
    }

    private fun distanceMeters(first: RawReading.Location, second: RawReading.Location): Double {
        val latitudeDelta = (second.latitude - first.latitude).toRadians()
        val longitudeDelta = (second.longitude - first.longitude).toRadians()
        val firstLatitude = first.latitude.toRadians()
        val secondLatitude = second.latitude.toRadians()
        val value = (
            sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
                cos(firstLatitude) * cos(secondLatitude) *
                sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
            ).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(value), sqrt(1.0 - value))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private companion object {
        const val MAXIMUM_ACCURACY_METERS = 60.0
        const val MAXIMUM_SPEED_METERS_PER_SECOND = 25.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
