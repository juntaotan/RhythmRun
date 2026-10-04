package com.compx551.rhythmrun.processing.model

import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.domain.model.RunRecord

data class EfficiencyBaseline(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
    val historyCount: Int,
) {
    val speedHeartRateRatio: Double
        get() = averageSpeedMetersPerSecond / averageHeartRateBpm

    companion object {
        // Match the cadence-based speed used by LiveRunProcessor.
        const val STRIDE_LENGTH_METERS = 0.75

        fun fromPreviousRuns(records: List<RunRecord>): EfficiencyBaseline? {
            val runs = records.asSequence()
                .filter {
                    it.completion == RunCompletion.Completed &&
                        it.cadenceSource in setOf(CadenceSource.DirectStepRate, CadenceSource.DerivedFromStepCount) &&
                        (it.dataCoveragePercent ?: 0) >= 70 &&
                        (it.averageCadenceSpm ?: 0) > 0 &&
                        (it.averageHeartRateBpm ?: 0) > 0
                }
                .take(10)
                .toList()
            if (runs.isEmpty()) return null
            return EfficiencyBaseline(
                averageSpeedMetersPerSecond = runs.map { it.averageCadenceSpm!! * STRIDE_LENGTH_METERS / 60.0 }.average(),
                averageHeartRateBpm = runs.map { it.averageHeartRateBpm!!.toDouble() }.average(),
                historyCount = runs.size,
            )
        }
    }
}
