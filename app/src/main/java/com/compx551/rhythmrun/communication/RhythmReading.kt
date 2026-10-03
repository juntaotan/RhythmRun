package com.compx551.rhythmrun.communication

/** Validated reading delivered by the phone-side background API listener. */
sealed interface RhythmReading {
    val sessionId: String
    val sequence: Long
    val timestampEpochMillis: Long

    data class Acceleration(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampEpochMillis: Long,
        val xMetersPerSecondSquared: Float,
        val yMetersPerSecondSquared: Float,
        val zMetersPerSecondSquared: Float,
    ) : RhythmReading

    data class HeartRate(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampEpochMillis: Long,
        val beatsPerMinute: Float,
        val source: String,
    ) : RhythmReading

    data class Steps(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampEpochMillis: Long,
        val cumulativeSteps: Long,
        val source: String,
    ) : RhythmReading

    data class Cadence(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampEpochMillis: Long,
        val stepsPerMinute: Float,
        val source: String,
    ) : RhythmReading

    data class Location(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampEpochMillis: Long,
        val latitudeDegrees: Double,
        val longitudeDegrees: Double,
        val horizontalAccuracyMetres: Float?,
    ) : RhythmReading
}
