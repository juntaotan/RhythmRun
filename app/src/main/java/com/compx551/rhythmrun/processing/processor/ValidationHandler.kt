package com.compx551.rhythmrun.processing.processor

/** Rejects malformed or clearly implausible watch samples before downstream processing. */
class ValidationHandler : ProcessingHandler() {
    override fun process(request: ProcessingRequest): Boolean {
        require(request.sessionId.isNotBlank()) { "Session ID must not be blank" }

        request.readings.forEachIndexed { index, reading ->
            require(reading.timestampMillis >= 0L) {
                "Reading $index has a negative timestamp"
            }
            when (reading) {
                is RawReading.HeartRate -> require(
                    reading.beatsPerMinute.isFinite() && reading.beatsPerMinute in 20.0..250.0,
                ) { "Reading $index has an implausible heart rate" }

                is RawReading.Acceleration -> {
                    val limit = when (reading.unit) {
                        AccelerationUnit.METERS_PER_SECOND_SQUARED -> 200.0
                        AccelerationUnit.STANDARD_GRAVITY -> 20.0
                    }
                    require(listOf(reading.x, reading.y, reading.z).all { it.isFinite() && it in -limit..limit }) {
                        "Reading $index has an implausible acceleration"
                    }
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

                is RawReading.StepCounter -> require(reading.totalSteps >= 0L) {
                    "Reading $index has a negative step count"
                }

                // GPS accuracy and coordinate outliers are handled per fix by GpsProcessingHandler,
                // so one bad fix does not discard otherwise valid sensor data in the batch.
                is RawReading.Location -> Unit
            }
        }
        return true
    }
}
