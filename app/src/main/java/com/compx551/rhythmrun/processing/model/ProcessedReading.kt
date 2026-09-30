package com.compx551.rhythmrun.processing.model

data class ProcessedReading(
    val timestampMillis: Long,
    val heartRateBpm: Double,
    val accelerationPerSecond: Double,
    val velocityMetersPerSecond: Double,
    val stepCounterPerSecond: Double,
    val stepCounterTotal: Long,
)
