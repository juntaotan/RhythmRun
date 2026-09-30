package com.compx551.rhythmrun.domain.model

enum class RunSessionState {
    Planned,
    Active,
    Paused,
    Completed,
    StoppedEarly,
}

data class RunPlanStage(
    val stage: RunStage,
    val order: Int,
    val durationSeconds: Long,
    val targetCadenceSpm: Int?,
)

data class RunPlan(
    val sessionId: String,
    val startEpochMillis: Long,
    val startLocalDate: String,
    val startLocalTime: String,
    val timeZoneId: String,
    val guidanceEnabled: Boolean,
    val outdoorRouteEnabled: Boolean = false,
    val state: RunSessionState = RunSessionState.Planned,
    val stages: List<RunPlanStage>,
)

/**
 * Part 4 persistence input. Member 2 can map its versioned Data Layer DTOs into these records
 * without making the Room schema depend on the communication implementation.
 */
enum class StoredSensorType {
    Accelerometer,
    Gyroscope,
    HeartRate,
    StepCount,
    StepCadence,
}

data class RawSensorRecord(
    val sessionId: String,
    val sensorType: StoredSensorType,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val scalarValue: Double? = null,
    val unit: String,
    val source: String? = null,
    val available: Boolean = true,
)

data class LocationFixRecord(
    val sessionId: String,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMetres: Double?,
    val available: Boolean,
    val gapBefore: Boolean = false,
)

data class StageChangeRecord(
    val sessionId: String,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val stage: RunStage,
    val reason: String,
)

data class CueEventRecord(
    val sessionId: String,
    val sequence: Long,
    val intendedTimestampEpochMillis: Long,
    val actualTimestampEpochMillis: Long?,
    val stage: RunStage,
    val delivered: Boolean,
)

data class AnalysisWindowRecord(
    val sessionId: String,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val analysisVersion: String,
    val cadenceSpm: Double?,
    val cadenceSource: CadenceSource,
    val rhythmStabilityPercent: Double?,
    val movementMagnitude: Double?,
    val averageHeartRateBpm: Double?,
    val confidencePercent: Double?,
    val coveragePercent: Double?,
)

data class RunDataBatch(
    val rawReadings: List<RawSensorRecord> = emptyList(),
    val locationFixes: List<LocationFixRecord> = emptyList(),
    val stageChanges: List<StageChangeRecord> = emptyList(),
    val cueEvents: List<CueEventRecord> = emptyList(),
    val analysisWindows: List<AnalysisWindowRecord> = emptyList(),
)
