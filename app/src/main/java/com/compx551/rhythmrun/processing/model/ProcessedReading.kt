package com.compx551.rhythmrun.processing.model

data class ProcessedReading(
    val timestampMillis: Long,
    val heartRateBpm: Double,
    val accelerationPerSecond: Double,
    val velocityMetersPerSecond: Double,
    val stepCounterPerSecond: Double,
    val location: ProcessedLocation? = null,
    val efficiency: Double? = null,
)

data class ProcessedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
)
