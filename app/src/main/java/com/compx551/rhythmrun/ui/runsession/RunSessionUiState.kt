package com.compx551.rhythmrun.ui.runsession

import com.compx551.rhythmrun.domain.model.RunStage

enum class RunSessionUiMode {
    Plan,
    Live,
    Summary,
}

data class StagePlanUiState(
    val stage: RunStage,
    val durationMinutesInput: String,
    val targetCadenceSpmInput: String?,
) {
    val hasValidDuration: Boolean
        get() = durationMinutesInput.toDoubleOrNull()?.let { it > 0.0 } == true

    val hasValidCadence: Boolean
        get() = targetCadenceSpmInput?.toIntOrNull()?.let { it > 0 } ?: true

    val isValid: Boolean
        get() = hasValidDuration && hasValidCadence
}
data class RunSessionUiState(
    val mode: RunSessionUiMode,
    val stages: List<StagePlanUiState>,
    val guidanceEnabled: Boolean,
    val liveRun: LiveRunUiState = LiveRunUiState(),
    val summary: RunSummaryUiState? = null,
) {
    val isPlanValid: Boolean
        get() {
            val containsEveryStage =
                stages.map { it.stage }.toSet() == RunStage.entries.toSet()

            return containsEveryStage && stages.all { it.isValid }
        }

    val totalDurationMinutes: Double?
        get() {
            val durations = stages.map { stage ->
                val duration = stage.durationMinutesInput.toDoubleOrNull()
                    ?: return null

                if (duration <= 0.0) {
                    return null
                }

                duration
            }

            return durations.sum()
        }
}

fun createInitialRunSessionUiState(): RunSessionUiState {
    return RunSessionUiState(
        mode = RunSessionUiMode.Plan,
        stages = listOf(
            StagePlanUiState(
                stage = RunStage.WarmUp,
                durationMinutesInput = "",
                targetCadenceSpmInput = "",
            ),
            StagePlanUiState(
                stage = RunStage.Running,
                durationMinutesInput = "",
                targetCadenceSpmInput = "",
            ),
            StagePlanUiState(
                stage = RunStage.SlowDown,
                durationMinutesInput = "",
                targetCadenceSpmInput = "",
            ),
            StagePlanUiState(
                stage = RunStage.Recovery,
                durationMinutesInput = "",
                targetCadenceSpmInput = null,
            ),
        ),
        guidanceEnabled = true,
    )
}
