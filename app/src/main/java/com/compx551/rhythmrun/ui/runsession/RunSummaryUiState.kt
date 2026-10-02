package com.compx551.rhythmrun.ui.runsession

import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.domain.model.RunStage
import com.compx551.rhythmrun.processing.model.ProcessedLocation

data class StageSummaryUiState(
    val stage: RunStage,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long,

    val targetCadenceSpm: Int?,
    val averageCadenceSpm: Int?,
    val averageHeartRateBpm: Int?,

    val movementMagnitude: Double?,
)

data class RunSummaryUiState(
    val sessionId: String,
    val completionStatus: RunCompletion,

    val totalDurationSeconds: Long,
    val distanceMetres: Double?,

    val averageHeartRateBpm: Int?,
    val averageCadenceSpm: Int?,
    val cadenceSource: CadenceSource,

    val targetAdherencePercent: Int?,
    val rhythmStabilityPercent: Int?,
    val averageMovementMagnitude: Double?,
    val heartRateResponseBpm: Int?,
    val cueCoveragePercent: Int?,
    val dataCoveragePercent: Int?,

    val stages: List<StageSummaryUiState>,

    val incompleteDataMessages: List<String> = emptyList(),
    val routePoints: List<ProcessedLocation> = emptyList(),
) {
    val hasIncompleteData: Boolean
        get() {
            val coverageIsIncomplete =
                dataCoveragePercent?.let { it < 100 } ?: true

            return coverageIsIncomplete ||
                    incompleteDataMessages.isNotEmpty()
        }
}
