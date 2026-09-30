package com.compx551.rhythmrun.domain.model

enum class RunStage {
    WarmUp,
    Running,
    SlowDown,
    Recovery,
}

enum class RunCompletion {
    Completed,
    StoppedEarly,
}

enum class CadenceSource {
    DirectStepRate,
    DerivedFromStepCount,
    EstimatedWristRhythm,
    Unavailable,
}

data class RunStageResult(
    val stage: RunStage,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long,
    val targetCadenceSpm: Int?,
    val averageCadenceSpm: Int?,
    val averageHeartRateBpm: Int?,
    val movementMagnitude: Double?,
)

/**
 * Phone-owned representation of one saved run.
 *
 * Room entities and teammate processing results should be mapped to this model instead of
 * leaking database or communication classes into the Compose UI.
 */
data class RunRecord(
    val sessionId: String,
    val startEpochMillis: Long,
    val startLocalDate: String,
    val startLocalTime: String,
    val timeZoneId: String,
    val title: String,
    val completion: RunCompletion,
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
    val stages: List<RunStageResult>,
    val incompleteDataMessages: List<String>,
)

