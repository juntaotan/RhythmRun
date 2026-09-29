package com.compx551.rhythmrun.ui.runsession

enum class RunSessionUiMode {
    Plan,
    Live,
    Summary,
}

enum class RunStageType {
    WarmUp,
    Running,
    SlowDown,
    Recovery,
}

data class StagePlanUiState(
    val stage: RunStageType,
    val durationMinutesInput: String,
    val targetCadenceSpmInput: String?,
) {
    val hasValidDuration: Boolean
        get() = durationMinutesInput.toIntOrNull()?.let { it > 0 } == true

    val hasValidCadence: Boolean
        get() = targetCadenceSpmInput?.toIntOrNull()?.let { it > 0 } ?: true

    val isValid: Boolean
        get() = hasValidDuration && hasValidCadence
}
data class RunSessionUiState(
    val mode: RunSessionUiMode,
    val stages: List<StagePlanUiState>,
    val guidanceEnabled: Boolean,
) {
    val isPlanValid: Boolean
        get() {
            val containsEveryStage =
                stages.map { it.stage }.toSet() == RunStageType.entries.toSet()

            return containsEveryStage && stages.all { it.isValid }
        }

    val totalDurationMinutes: Int?
        get() {
            val durations = stages.map { stage ->
                val duration = stage.durationMinutesInput.toIntOrNull()
                    ?: return null

                if (duration <= 0) {
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
                stage = RunStageType.WarmUp,
                durationMinutesInput = "",
                targetCadenceSpmInput = "",
            ),
            StagePlanUiState(
                stage = RunStageType.Running,
                durationMinutesInput = "",
                targetCadenceSpmInput = "",
            ),
            StagePlanUiState(
                stage = RunStageType.SlowDown,
                durationMinutesInput = "",
                targetCadenceSpmInput = "",
            ),
            StagePlanUiState(
                stage = RunStageType.Recovery,
                durationMinutesInput = "",
                targetCadenceSpmInput = null,
            ),
        ),
        guidanceEnabled = true,
    )
}
