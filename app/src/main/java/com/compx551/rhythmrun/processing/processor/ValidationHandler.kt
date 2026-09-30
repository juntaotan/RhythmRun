package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedReading
import com.compx551.rhythmrun.processing.model.ProcessingRequest

/** Rejects malformed or clearly implausible watch samples before downstream processing. */
class ValidationHandler : ProcessingHandler() {

    override fun process(request: ProcessingRequest): Boolean {

        require(request.sessionId.isNotBlank()) { "Session ID must not be blank" }

        request.readings.forEachIndexed { index, reading ->
            require(reading.timestampMillis >= 0L) {
                "Reading $index has a negative timestamp"
            }
            when (reading) {
                /** Heart rate is a positive number in the range 20 to 250 */
                is RawReading.HeartRate -> require(
                    reading.beatsPerMinute.isFinite()
                            && reading.beatsPerMinute in 20.0..250.0,
                ) { "Reading $index has an implausible heart rate" }
                /** Acceleration is in the range 20 to 250 */
                is RawReading.Acceleration -> {
                    val limit = when (reading.unit) {
                        AccelerationUnit.METERS_PER_SECOND_SQUARED -> 200.0
                        AccelerationUnit.STANDARD_GRAVITY -> 20.0
                    }
                    require(listOf(reading.x, reading.y, reading.z).all { it.isFinite()
                            && it in -limit..limit }
                    ) { "Reading $index has an implausible acceleration" }
                }
                /** Velocity is in the range 25 to 90 */
                is RawReading.Velocity -> {
                    val limit = when (reading.unit) {
                        VelocityUnit.METERS_PER_SECOND -> 25.0
                        VelocityUnit.KILOMETERS_PER_HOUR -> 90.0
                    }
                    require(reading.value.isFinite()
                            && reading.value in 0.0..limit
                    ) { "Reading $index has an implausible velocity" }
                }
                /** Step count is a non-negative number */
                is RawReading.StepCounter -> require(reading.stepsPerSecond >= 0L) {
                    "Reading $index has a negative step count"
                }
                /** Validate latitude and longitude and GPS accuracy */
                is RawReading.Location -> {
                    /** latitude is in the range from -90 to 90 */
                    require(
                        reading.latitude.isFinite() &&
                                reading.latitude in -90.0..90.0
                    ) { "Reading $index has an invalid latitude" }
                    /** latitude is in the range from -90 to 90 */
                    require(
                        reading.longitude.isFinite() &&
                                reading.longitude in -180.0..180.0
                    ) { "Reading $index has an invalid longitude" }
                    /** GPS accuracy is a positive number in the range */
                    require(
                        reading.accuracyMeters.isFinite() &&
                                reading.accuracyMeters >= 0.0
                    ) { "Reading $index has an invalid GPS accuracy" }
                }
            }
        }
        return true
    }
}
