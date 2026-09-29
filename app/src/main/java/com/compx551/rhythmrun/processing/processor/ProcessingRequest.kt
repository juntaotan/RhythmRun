package com.compx551.rhythmrun.processing.processor

/** Timestamps are milliseconds on the same session timeline. */
data class ProcessingRequest(
    val sessionId: String,
    val readings: List<RawReading>,
    /** Last persisted step count before this batch, when processing a continuing session. */
    val previousStepCounter: RawReading.StepCounter? = null,
    var normalizedReadings: List<NormalizedReading> = emptyList(),
    var latestStepCounter: RawReading.StepCounter? = null,
    /** Recent unsmoothed readings from the preceding batch of this session. */
    val smoothingHistory: List<NormalizedReading> = emptyList(),
    var smoothedReadings: List<NormalizedReading> = emptyList(),
    var nextSmoothingHistory: List<NormalizedReading> = emptyList(),
)

sealed interface RawReading {
    val timestampMillis: Long

    data class HeartRate(
        override val timestampMillis: Long,
        val beatsPerMinute: Double,
    ) : RawReading

    data class Acceleration(
        override val timestampMillis: Long,
        val x: Double,
        val y: Double,
        val z: Double,
        val unit: AccelerationUnit = AccelerationUnit.METERS_PER_SECOND_SQUARED,
    ) : RawReading

    data class Velocity(
        override val timestampMillis: Long,
        val value: Double,
        val unit: VelocityUnit = VelocityUnit.METERS_PER_SECOND,
    ) : RawReading

    /** Sensor.TYPE_STEP_COUNTER reports a cumulative count, not steps per minute. */
    data class StepCounter(
        override val timestampMillis: Long,
        val totalSteps: Long,
    ) : RawReading
}

enum class AccelerationUnit { METERS_PER_SECOND_SQUARED, STANDARD_GRAVITY }
enum class VelocityUnit { METERS_PER_SECOND, KILOMETERS_PER_HOUR }

sealed interface NormalizedReading {
    val timestampMillis: Long

    data class HeartRate(
        override val timestampMillis: Long,
        val beatsPerMinute: Double,
    ) : NormalizedReading

    /** Components and magnitude are in m/s². Magnitude still includes gravity. */
    data class Acceleration(
        override val timestampMillis: Long,
        val xMetersPerSecondSquared: Double,
        val yMetersPerSecondSquared: Double,
        val zMetersPerSecondSquared: Double,
        val magnitudeMetersPerSecondSquared: Double,
    ) : NormalizedReading

    data class Velocity(
        override val timestampMillis: Long,
        val metersPerSecond: Double,
    ) : NormalizedReading

    /** Cadence belongs to the interval ending at this reading's timestamp. */
    data class Cadence(
        override val timestampMillis: Long,
        val stepsPerMinute: Double,
        val intervalStartMillis: Long,
    ) : NormalizedReading
}
