package com.compx551.rhythmrun.ui.runsession

import com.compx551.rhythmrun.domain.model.RunStage

enum class LiveRunSyncStatus {
    WaitingForWatch,
    ReceivingData,
    ConnectionLost,
}

data class LiveRunUiState(
    val sessionId: String? = null,

    val currentStage: RunStage = RunStage.WarmUp,
    val currentStageNumber: Int = 1,
    val totalStageCount: Int = 4,

    val stageElapsedSeconds: Long = 0L,
    val stageDurationSeconds: Long = 0L,
    val totalElapsedSeconds: Long = 0L,

    val heartRateBpm: Int? = null,
    val cadenceSpm: Int? = null,
    val targetCadenceSpm: Int? = null,
    val speedKilometresPerHour: Double? = null,
    val distanceMetres: Double = 0.0,
    val accelerationMagnitude: Double? = null,

    val isPaused: Boolean = false,
    val syncStatus: LiveRunSyncStatus =
        LiveRunSyncStatus.WaitingForWatch,
) {
    val stageRemainingSeconds: Long
        get() = (stageDurationSeconds - stageElapsedSeconds)
            .coerceAtLeast(0L)

    val stageProgress: Float
        get() {
            if (stageDurationSeconds <= 0L) {
                return 0f
            }

            return (
                    stageElapsedSeconds.toFloat() /
                            stageDurationSeconds.toFloat()
                    ).coerceIn(0f, 1f)
        }

    val cadenceDifferenceSpm: Int?
        get() {
            val currentCadence = cadenceSpm ?: return null
            val targetCadence = targetCadenceSpm ?: return null

            return currentCadence - targetCadence
        }
}
