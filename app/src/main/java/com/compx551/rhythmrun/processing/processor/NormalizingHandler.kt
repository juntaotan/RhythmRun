package com.compx551.rhythmrun.processing.processor

import kotlin.math.sqrt

/** Converts validated watch readings to the units consumed by smoothing and analysis. */
class NormalizingHandler : ProcessingHandler() {
    override fun process(request: ProcessingRequest): Boolean {
        val result = normalize(request.readings, request.previousStepCounter)
        request.normalizedReadings = result.readings
        request.latestStepCounter = result.latestStepCounter
        return true
    }

    fun normalize(
        readings: List<RawReading>,
        previousStepCounter: RawReading.StepCounter? = null,
    ): NormalizationResult {
        val result = mutableListOf<NormalizedReading>()
        var previousSteps = previousStepCounter

        // A batch can contain interleaved sensors and out-of-order arrival. Preserve original
        // order for equal timestamps; cadence is calculated only between consecutive step counts.
        for (reading in readings.sortedBy(RawReading::timestampMillis)) {
            when (reading) {
                is RawReading.HeartRate -> result += NormalizedReading.HeartRate(
                    reading.timestampMillis,
                    reading.beatsPerMinute,
                )

                is RawReading.Acceleration -> {
                    val factor = when (reading.unit) {
                        AccelerationUnit.METERS_PER_SECOND_SQUARED -> 1.0
                        AccelerationUnit.STANDARD_GRAVITY -> 9.80665
                    }
                    val x = reading.x * factor
                    val y = reading.y * factor
                    val z = reading.z * factor
                    result += NormalizedReading.Acceleration(
                        reading.timestampMillis,
                        x,
                        y,
                        z,
                        sqrt(x * x + y * y + z * z),
                    )
                }

                is RawReading.Velocity -> result += NormalizedReading.Velocity(
                    reading.timestampMillis,
                    when (reading.unit) {
                        VelocityUnit.METERS_PER_SECOND -> reading.value
                        VelocityUnit.KILOMETERS_PER_HOUR -> reading.value / 3.6
                    },
                )

                is RawReading.StepCounter -> {
                    val previous = previousSteps
                    if (previous != null) {
                        val elapsedMillis = reading.timestampMillis - previous.timestampMillis
                        val stepDelta = reading.totalSteps - previous.totalSteps
                        if (elapsedMillis > 0 && stepDelta >= 0) {
                            result += NormalizedReading.Cadence(
                                timestampMillis = reading.timestampMillis,
                                stepsPerMinute = stepDelta * 60_000.0 / elapsedMillis,
                                intervalStartMillis = previous.timestampMillis,
                            )
                        }
                    }
                    // A reset/reboot starts a new baseline. Equal or older timestamps must not
                    // replace the baseline or create a zero-duration cadence interval.
                    if (previous == null || reading.timestampMillis > previous.timestampMillis) {
                        previousSteps = reading
                    }
                }
            }
        }

        return NormalizationResult(result, previousSteps)
    }
}

data class NormalizationResult(
    val readings: List<NormalizedReading>,
    val latestStepCounter: RawReading.StepCounter?,
)
