package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading

/** Adds the current reading's efficiency index. */
class AnalyzingHandler {

    fun analyze(
        reading: ProcessedReading,
        baseline: EfficiencyBaseline,
    ): ProcessedReading = reading.copy(
        efficiency = if (reading.heartRateBpm > 0.0 &&
            baseline.averageSpeedMetersPerSecond > 0.0 &&
            baseline.averageHeartRateBpm > 0.0
        ) {
            (reading.velocityMetersPerSecond / reading.heartRateBpm) /
                (baseline.averageSpeedMetersPerSecond / baseline.averageHeartRateBpm)
        } else {
            0.0
        },
    )
}

data class HistoricalRunAverage(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
)
