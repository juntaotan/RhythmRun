package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedReading
import kotlin.math.sqrt

/** Converts validated watch readings to standard units and records complete sensor snapshots. */
class NormalizingHandler(
    private val processedReadings: MutableList<ProcessedReading> = mutableListOf(),
) : ProcessingHandler() {
    override fun process(request: ProcessingRequest): Boolean {
        val result = normalize(request.readings, request.previousStepCounter)
        request.normalizedReadings = result.readings
        request.latestStepCounter = result.latestStepCounter
        processedReadings.addAll(result.processedReadings)
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

                // Location processing is outside this validation/normalization phase.
                is RawReading.Location -> Unit
            }
        }

        return NormalizationResult(
            readings = result,
            latestStepCounter = previousSteps,
            processedReadings = createProcessedReadings(readings, previousStepCounter),
        )
    }

    /**
     * Builds one snapshot per timestamp after every required sensor has supplied a value. Values
     * are carried forward until that sensor reports again, allowing independently sampled sensors
     * to form a single [ProcessedReading].
     */
    private fun createProcessedReadings(
        readings: List<RawReading>,
        previousStepCounter: RawReading.StepCounter?,
    ): List<ProcessedReading> {
        var heartRateBpm: Double? = null
        var accelerationPerSecond: Double? = null
        var velocityMetersPerSecond: Double? = null
        var stepCounterPerSecond: Double? = previousStepCounter?.let { 0.0 }
        var stepCounterTotal: Long? = previousStepCounter?.totalSteps
        var previousSteps = previousStepCounter
        val result = mutableListOf<ProcessedReading>()

        for ((timestamp, samples) in readings
            .filterNot { it is RawReading.Location }
            .groupBy(RawReading::timestampMillis)
            .toSortedMap()
        ) {
            for (reading in samples) {
                when (reading) {
                    is RawReading.HeartRate -> heartRateBpm = reading.beatsPerMinute
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
                    is RawReading.Velocity -> velocityMetersPerSecond = when (reading.unit) {
                        VelocityUnit.METERS_PER_SECOND -> reading.value
                        VelocityUnit.KILOMETERS_PER_HOUR -> reading.value / 3.6
                    }
                    is RawReading.StepCounter -> {
                        val previous = previousSteps
                        stepCounterPerSecond = if (previous == null) {
                            0.0
                        } else {
                            val elapsedMillis = reading.timestampMillis - previous.timestampMillis
                            val stepDelta = reading.totalSteps - previous.totalSteps
                            if (elapsedMillis > 0 && stepDelta >= 0) {
                                stepDelta * 1_000.0 / elapsedMillis
                            } else {
                                0.0
                            }
                        }
                        stepCounterTotal = reading.totalSteps
                        if (previous == null || reading.timestampMillis > previous.timestampMillis) {
                            previousSteps = reading
                        }
                    }
                    is RawReading.Location -> Unit
                }
            }

            val heartRate = heartRateBpm
            val acceleration = accelerationPerSecond
            val velocity = velocityMetersPerSecond
            val stepsPerSecond = stepCounterPerSecond
            val totalSteps = stepCounterTotal
            if (heartRate != null && acceleration != null && velocity != null &&
                stepsPerSecond != null && totalSteps != null
            ) {
                result += ProcessedReading(
                    timestampMillis = timestamp,
                    heartRateBpm = heartRate,
                    accelerationPerSecond = acceleration,
                    velocityMetersPerSecond = velocity,
                    stepCounterPerSecond = stepsPerSecond,
                    stepCounterTotal = totalSteps,
                )
            }
        }
        return result
    }
}

data class NormalizationResult(
    val readings: List<NormalizedReading>,
    val latestStepCounter: RawReading.StepCounter?,
    val processedReadings: List<ProcessedReading> = emptyList(),
)
