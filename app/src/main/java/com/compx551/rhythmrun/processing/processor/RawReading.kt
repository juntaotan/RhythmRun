package com.compx551.rhythmrun.processing.processor

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

    /** Cadence expressed as steps per second. */
    data class StepCounter(
        override val timestampMillis: Long,
        val stepsPerSecond: Double,
    ) : RawReading

    data class Location(
        override val timestampMillis: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double,
    ) : RawReading
}

enum class AccelerationUnit { METERS_PER_SECOND_SQUARED, STANDARD_GRAVITY }
enum class VelocityUnit { METERS_PER_SECOND, KILOMETERS_PER_HOUR }
