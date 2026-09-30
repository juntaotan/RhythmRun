package com.compx551.rhythmrun.processing.processor

/** Legacy persistence model retained for the existing running repository. */
data class SpeedHeartRateRatio(
    val timestampMillis: Long,
    val speedToHeartRateRatio: Double,
    val speedMetersPerSecond: Double,
    val heartRateBpm: Double,
    val relativeEfficiency: Double? = null,
)
