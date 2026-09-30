package com.compx551.rhythmrun.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PhoneRunDao {
    @Query("SELECT * FROM phone_run_sessions ORDER BY startEpochMillis DESC")
    abstract fun observeSessions(): Flow<List<RunSessionEntity>>

    @Query("SELECT * FROM phone_run_sessions WHERE sessionId = :sessionId LIMIT 1")
    abstract suspend fun findSession(sessionId: String): RunSessionEntity?

    @Query("SELECT * FROM phone_run_stages WHERE sessionId = :sessionId ORDER BY stageOrder")
    abstract suspend fun findStages(sessionId: String): List<RunStageEntity>

    @Query("SELECT * FROM phone_run_summaries WHERE sessionId = :sessionId LIMIT 1")
    abstract suspend fun findSummary(sessionId: String): RunSummaryEntity?

    @Query("SELECT * FROM phone_incomplete_data_messages WHERE sessionId = :sessionId ORDER BY messageOrder")
    abstract suspend fun findIncompleteMessages(sessionId: String): List<IncompleteDataMessageEntity>

    @Query("SELECT * FROM phone_raw_sensor_readings WHERE sessionId = :sessionId ORDER BY timestampEpochMillis, sensorType, sequence")
    abstract suspend fun findRawReadings(sessionId: String): List<RawSensorReadingEntity>

    @Query("SELECT * FROM phone_location_fixes WHERE sessionId = :sessionId ORDER BY timestampEpochMillis, sequence")
    abstract suspend fun findLocationFixes(sessionId: String): List<LocationFixEntity>

    @Query("SELECT * FROM phone_stage_changes WHERE sessionId = :sessionId ORDER BY timestampEpochMillis, sequence")
    abstract suspend fun findStageChanges(sessionId: String): List<StageChangeEntity>

    @Query("SELECT * FROM phone_cue_events WHERE sessionId = :sessionId ORDER BY intendedTimestampEpochMillis, sequence")
    abstract suspend fun findCueEvents(sessionId: String): List<CueEventEntity>

    @Query("SELECT * FROM phone_analysis_windows WHERE sessionId = :sessionId ORDER BY windowStartEpochMillis")
    abstract suspend fun findAnalysisWindows(sessionId: String): List<AnalysisWindowEntity>

    @Upsert
    abstract suspend fun upsertSession(session: RunSessionEntity)

    @Upsert
    abstract suspend fun upsertStages(stages: List<RunStageEntity>)

    @Upsert
    abstract suspend fun upsertSummary(summary: RunSummaryEntity)

    @Upsert
    abstract suspend fun upsertRawReadings(readings: List<RawSensorReadingEntity>)

    @Upsert
    abstract suspend fun upsertLocationFixes(fixes: List<LocationFixEntity>)

    @Upsert
    abstract suspend fun upsertStageChanges(changes: List<StageChangeEntity>)

    @Upsert
    abstract suspend fun upsertCueEvents(events: List<CueEventEntity>)

    @Upsert
    abstract suspend fun upsertAnalysisWindows(windows: List<AnalysisWindowEntity>)

    @Upsert
    abstract suspend fun upsertIncompleteMessages(messages: List<IncompleteDataMessageEntity>)

    @Query("DELETE FROM phone_run_stages WHERE sessionId = :sessionId")
    abstract suspend fun deleteStages(sessionId: String)

    @Query("DELETE FROM phone_incomplete_data_messages WHERE sessionId = :sessionId")
    abstract suspend fun deleteIncompleteMessages(sessionId: String)

    @Transaction
    open suspend fun replacePlan(
        session: RunSessionEntity,
        stages: List<RunStageEntity>,
    ) {
        upsertSession(session)
        deleteStages(session.sessionId)
        if (stages.isNotEmpty()) upsertStages(stages)
    }

    @Transaction
    open suspend fun replaceCompletedRun(
        session: RunSessionEntity,
        stages: List<RunStageEntity>,
        summary: RunSummaryEntity,
        messages: List<IncompleteDataMessageEntity>,
    ) {
        upsertSession(session)
        deleteStages(session.sessionId)
        if (stages.isNotEmpty()) upsertStages(stages)
        upsertSummary(summary)
        deleteIncompleteMessages(session.sessionId)
        if (messages.isNotEmpty()) upsertIncompleteMessages(messages)
    }

    /** All lists are written in one transaction; replay is idempotent because keys are stable. */
    @Transaction
    open suspend fun persistIncomingBatch(
        rawReadings: List<RawSensorReadingEntity>,
        locationFixes: List<LocationFixEntity>,
        stageChanges: List<StageChangeEntity>,
        cueEvents: List<CueEventEntity>,
        analysisWindows: List<AnalysisWindowEntity>,
    ) {
        if (rawReadings.isNotEmpty()) upsertRawReadings(rawReadings)
        if (locationFixes.isNotEmpty()) upsertLocationFixes(locationFixes)
        if (stageChanges.isNotEmpty()) upsertStageChanges(stageChanges)
        if (cueEvents.isNotEmpty()) upsertCueEvents(cueEvents)
        if (analysisWindows.isNotEmpty()) upsertAnalysisWindows(analysisWindows)
    }
}
