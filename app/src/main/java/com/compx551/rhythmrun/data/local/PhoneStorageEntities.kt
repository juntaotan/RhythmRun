package com.compx551.rhythmrun.data.local

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "phone_run_sessions",
    primaryKeys = ["sessionId"],
    indices = [Index(value = ["startEpochMillis"]), Index(value = ["startLocalDate"])],
)
data class RunSessionEntity(
    val sessionId: String,
    val startEpochMillis: Long,
    val startLocalDate: String,
    val startLocalTime: String,
    val timeZoneId: String,
    val title: String,
    val state: String,
    val guidanceEnabled: Boolean,
    val outdoorRouteEnabled: Boolean,
    val endEpochMillis: Long? = null,
)

@Entity(
    tableName = "phone_run_stages",
    primaryKeys = ["sessionId", "stageOrder"],
    indices = [Index(value = ["sessionId"])],
)
data class RunStageEntity(
    val sessionId: String,
    val stageOrder: Int,
    val stage: String,
    val plannedDurationSeconds: Long,
    val targetCadenceSpm: Int?,
    val actualDurationSeconds: Long? = null,
    val averageCadenceSpm: Int? = null,
    val averageHeartRateBpm: Int? = null,
    val movementMagnitude: Double? = null,
)

@Entity(
    tableName = "phone_run_summaries",
    primaryKeys = ["sessionId"],
)
data class RunSummaryEntity(
    val sessionId: String,
    val totalDurationSeconds: Long,
    val distanceMetres: Double?,
    val averageHeartRateBpm: Int?,
    val averageCadenceSpm: Int?,
    val cadenceSource: String,
    val targetAdherencePercent: Int?,
    val rhythmStabilityPercent: Int?,
    val averageMovementMagnitude: Double?,
    val heartRateResponseBpm: Int?,
    val cueCoveragePercent: Int?,
    val dataCoveragePercent: Int?,
)

@Entity(
    tableName = "phone_raw_sensor_readings",
    primaryKeys = ["sessionId", "sensorType", "sequence"],
    indices = [Index(value = ["sessionId", "timestampEpochMillis"])],
)
data class RawSensorReadingEntity(
    val sessionId: String,
    val sensorType: String,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val x: Double?,
    val y: Double?,
    val z: Double?,
    val scalarValue: Double?,
    val unit: String,
    val source: String?,
    val available: Boolean,
)

@Entity(
    tableName = "phone_location_fixes",
    primaryKeys = ["sessionId", "sequence"],
    indices = [Index(value = ["sessionId", "timestampEpochMillis"])],
)
data class LocationFixEntity(
    val sessionId: String,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMetres: Double?,
    val available: Boolean,
    val gapBefore: Boolean,
)

@Entity(
    tableName = "phone_stage_changes",
    primaryKeys = ["sessionId", "sequence"],
    indices = [Index(value = ["sessionId", "timestampEpochMillis"])],
)
data class StageChangeEntity(
    val sessionId: String,
    val sequence: Long,
    val timestampEpochMillis: Long,
    val stage: String,
    val reason: String,
)

@Entity(
    tableName = "phone_cue_events",
    primaryKeys = ["sessionId", "sequence"],
    indices = [Index(value = ["sessionId", "intendedTimestampEpochMillis"])],
)
data class CueEventEntity(
    val sessionId: String,
    val sequence: Long,
    val intendedTimestampEpochMillis: Long,
    val actualTimestampEpochMillis: Long?,
    val stage: String,
    val delivered: Boolean,
)

@Entity(
    tableName = "phone_analysis_windows",
    primaryKeys = ["sessionId", "windowStartEpochMillis", "analysisVersion"],
    indices = [Index(value = ["sessionId", "windowStartEpochMillis"])],
)
data class AnalysisWindowEntity(
    val sessionId: String,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val analysisVersion: String,
    val cadenceSpm: Double?,
    val cadenceSource: String,
    val rhythmStabilityPercent: Double?,
    val movementMagnitude: Double?,
    val averageHeartRateBpm: Double?,
    val confidencePercent: Double?,
    val coveragePercent: Double?,
)

@Entity(
    tableName = "phone_incomplete_data_messages",
    primaryKeys = ["sessionId", "messageOrder"],
    indices = [Index(value = ["sessionId"])],
)
data class IncompleteDataMessageEntity(
    val sessionId: String,
    val messageOrder: Int,
    val message: String,
)
