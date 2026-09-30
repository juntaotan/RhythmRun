package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading

/** Adds the current reading's efficiency index. */
class AnalyzingHandler {

    fun analyze(
        reading: ProcessedReading,
        baseline: EfficiencyBaseline,
    ): ProcessedReading = reading.copy(
        efficiency = (reading.velocityMetersPerSecond / reading.heartRateBpm) /
            (baseline.averageSpeedMetersPerSecond / baseline.averageHeartRateBpm),
    )
}

data class HistoricalRunAverage(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
)
