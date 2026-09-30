package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading

/** Calculates efficiency for normalized readings using the exercise baseline. */
class AnalyzingHandler : ProcessingHandler() {

    override fun process(request: ProcessingRequest): Boolean {
        val baseline = requireNotNull(request.efficiencyBaseline) {
            "Efficiency baseline is required for analysis"
        }

        val reading = requireNotNull(request.processedReading) {
            "Normalization must complete before analysis"
        }
        request.processedReading = reading.copy(
            efficiency = calculateEfficiency(reading, baseline),
        )
        return true
    }

    fun calculateEfficiency(
        reading: ProcessedReading,
        baseline: EfficiencyBaseline,
    ): Double =
        (reading.velocityMetersPerSecond / reading.heartRateBpm) /
            (baseline.averageSpeedMetersPerSecond / baseline.averageHeartRateBpm)
}

data class AnalysisResult(
    val ratios: List<SpeedHeartRateRatio>,
    val latestHeartRate: NormalizedReading.HeartRate?,
)

data class HistoricalRunAverage(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
)
