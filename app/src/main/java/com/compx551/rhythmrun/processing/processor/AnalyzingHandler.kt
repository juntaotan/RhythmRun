package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading

/** Adds the current reading's efficiency index. */
class AnalyzingHandler {

    fun analyze(
        reading: ProcessedReading,
        baseline: EfficiencyBaseline,
    ): ProcessedReading {
        val denominator = if (baseline.historyCount == 0) 1.0 else baseline.speedHeartRateRatio
        return reading.copy(
            efficiency = if (reading.heartRateBpm > 0.0 && denominator > 0.0) {
                (reading.velocityMetersPerSecond / reading.heartRateBpm) / denominator
            } else {
                0.0
            },
        )
    }
}

data class HistoricalRunAverage(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
)
