package com.compx551.rhythmrun.processing.processor

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Validates one raw exercise event before normalization. */
class ValidationHandler {
    private var previousLocation: RawReading.Location? = null

    fun validate(readings: List<RawReading>) {
        require(readings.isNotEmpty()) { "Exercise event must contain sensor readings" }
        require(readings.any { it is RawReading.HeartRate }) {
            "Exercise event must contain heart-rate data"
        }
        require(readings.any { it is RawReading.Acceleration }) {
            "Exercise event must contain acceleration data"
        }
        require(readings.any { it is RawReading.Velocity }) {
            "Exercise event must contain velocity data"
        }
        require(readings.any { it is RawReading.StepCounter }) {
            "Exercise event must contain step data"
        }

        var latestLocation = previousLocation
        readings.sortedBy(RawReading::timestampMillis).forEachIndexed { index, reading ->
            require(reading.timestampMillis >= 0L) {
                "Reading $index has a negative timestamp"
            }

            when (reading) {
                is RawReading.HeartRate -> require(
                    reading.beatsPerMinute.isFinite() &&
                        reading.beatsPerMinute in 20.0..250.0,
                ) { "Reading $index has an implausible heart rate" }

                is RawReading.Acceleration -> {
                    val limit = when (reading.unit) {
                        AccelerationUnit.METERS_PER_SECOND_SQUARED -> 200.0
                        AccelerationUnit.STANDARD_GRAVITY -> 20.0
                    }
                    require(
                        listOf(reading.x, reading.y, reading.z).all {
                            it.isFinite() && it in -limit..limit
                        },
                    ) { "Reading $index has an implausible acceleration" }
                }

                is RawReading.Velocity -> {
                    val limit = when (reading.unit) {
                        VelocityUnit.METERS_PER_SECOND -> 25.0
                        VelocityUnit.KILOMETERS_PER_HOUR -> 90.0
                    }
                    require(reading.value.isFinite() && reading.value in 0.0..limit) {
                        "Reading $index has an implausible velocity"
                    }
                }

                is RawReading.StepCounter -> require(reading.stepsPerSecond >= 0L) {
                    "Reading $index has a negative step count"
                }

                is RawReading.Location -> {
                    require(reading.latitude.isFinite() && reading.latitude in -90.0..90.0) {
                        "Reading $index has an invalid latitude"
                    }
                    require(reading.longitude.isFinite() && reading.longitude in -180.0..180.0) {
                        "Reading $index has an invalid longitude"
                    }
                    require(
                        reading.accuracyMeters.isFinite() &&
                            reading.accuracyMeters in 0.0..MAXIMUM_ACCURACY_METERS,
                    ) { "Reading $index has an invalid GPS accuracy" }

                    val previous = latestLocation
                    if (previous != null) {
                        val elapsedSeconds =
                            (reading.timestampMillis - previous.timestampMillis) / 1_000.0
                        require(elapsedSeconds > 0.0) {
                            "Reading $index has an invalid GPS timestamp"
                        }
                        val speedMetersPerSecond = distanceMeters(previous, reading) / elapsedSeconds
                        require(
                            speedMetersPerSecond.isFinite() &&
                                speedMetersPerSecond <= MAXIMUM_SPEED_METERS_PER_SECOND,
                        ) { "Reading $index has an impossible GPS speed" }
                    }
                    latestLocation = reading
                }
            }
        }
        previousLocation = latestLocation
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
        const val MAXIMUM_ACCURACY_METERS = 25.0
        const val MAXIMUM_SPEED_METERS_PER_SECOND = 25.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
