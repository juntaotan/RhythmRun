package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.repository.RunningDetailsEntity

/** Timestamps are milliseconds on the same session timeline. */
data class ProcessingRequest(
    val sessionId: String,
    val readings: List<RawReading>,
    /** Session metadata/summary supplied by the session owner for persistence. */
    val sessionDetails: RunningDetailsEntity? = null,
    /** Last persisted step count before this batch, when processing a continuing session. */
    val previousStepCounter: RawReading.StepCounter? = null,
    var normalizedReadings: List<NormalizedReading> = emptyList(),
    var latestStepCounter: RawReading.StepCounter? = null,
    /** Recent unsmoothed readings from the preceding batch of this session. */
    val smoothingHistory: List<NormalizedReading> = emptyList(),
    var smoothedReadings: List<NormalizedReading> = emptyList(),
    var nextSmoothingHistory: List<NormalizedReading> = emptyList(),
    /** Last smoothed HR from the preceding batch, for pairing with the next speed reading. */
    val previousSmoothedHeartRate: NormalizedReading.HeartRate? = null,
    var latestSmoothedHeartRate: NormalizedReading.HeartRate? = null,
    /** GPS context produced by the preceding batch of this session. */
    val gpsState: GpsProcessingState = GpsProcessingState(),
    /** Accepted and smoothed route fixes, plus distance added by this batch. */
    var gpsResult: GpsProcessingResult = GpsProcessingResult(),
    var nextGpsState: GpsProcessingState = GpsProcessingState(),
    /** Derived from completed sessions before this session; null when none exist. */
    var efficiencyBaseline: EfficiencyBaseline? = null,
    var speedHeartRateRatios: List<SpeedHeartRateRatio> = emptyList(),
    /** Produced by AnalyzingHandler and consumed by PersistenceHandler. */
    var analysisResult: AnalysisResult? = null,
)

data class EfficiencyBaseline(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
    val historyCount: Int,
) {
    val speedHeartRateRatio: Double
        get() = averageSpeedMetersPerSecond / averageHeartRateBpm
}

/** Current ratio and its index relative to historical runs, when a baseline exists. */
data class SpeedHeartRateRatio(
    val timestampMillis: Long,
    val speedToHeartRateRatio: Double,
    val speedMetersPerSecond: Double,
    val heartRateBpm: Double,
    val relativeEfficiency: Double? = null,
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

    /** A GPS fix in WGS84 degrees, with the provider's horizontal 68% accuracy radius. */
    data class Location(
        override val timestampMillis: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double,
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
