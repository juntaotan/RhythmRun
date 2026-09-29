package com.compx551.rhythmrun.processing.repository

import androidx.room3.withWriteTransaction
import com.compx551.rhythmrun.processing.processor.HistoricalRunAverage
import com.compx551.rhythmrun.processing.processor.SpeedHeartRateRatio

/** App-facing persistence API for the two current Room tables. */
class RunningRepository(private val database: RhythmRunDatabase) : RunningStore {
    suspend fun saveSession(session: RunningDetailsEntity) {
        database.runningSessionDao().upsert(session)
    }

    suspend fun saveRatios(sessionId: String, ratios: List<SpeedHeartRateRatio>) {
        val entities = ratios.toEntities(sessionId)
        if (entities.isNotEmpty()) database.runningScoreDao().upsertAll(entities)
    }

    /** Upsert the session and this batch's scores atomically, preserving earlier score batches. */
    override suspend fun persistBatch(session: RunningDetailsEntity, ratios: List<SpeedHeartRateRatio>) {
        val entities = ratios.toEntities(session.sessionId)
        database.withWriteTransaction {
            database.runningSessionDao().upsert(session)
            if (entities.isNotEmpty()) database.runningScoreDao().upsertAll(entities)
        }
    }

    /** Replace a completed session and its full ratio timeline in one transaction. */
    suspend fun saveSessionWithRatios(
        session: RunningDetailsEntity,
        ratios: List<SpeedHeartRateRatio>,
    ) {
        val entities = ratios.toEntities(session.sessionId)
        database.withWriteTransaction {
            database.runningSessionDao().upsert(session)
            database.runningScoreDao().deleteBySession(session.sessionId)
            if (entities.isNotEmpty()) database.runningScoreDao().upsertAll(entities)
        }
    }

    suspend fun getSession(sessionId: String): RunningDetailsEntity? =
        database.runningSessionDao().findById(sessionId)

    suspend fun getSessionsNewestFirst(): List<RunningDetailsEntity> =
        database.runningSessionDao().getAllNewestFirst()

    suspend fun getRatios(sessionId: String): List<RunningScoreEntity> =
        database.runningScoreDao().getBySession(sessionId)

    /** The preceding completed runs, newest first, excluding the current session. */
    suspend fun getPreviousSessions(beforeStartTime: Long, limit: Int = 10): List<RunningDetailsEntity> {
        require(limit > 0)
        return database.runningSessionDao().getRecentBefore(beforeStartTime, limit)
    }

    override suspend fun getHistoricalAverages(beforeStartTime: Long): List<HistoricalRunAverage> =
        getPreviousSessions(beforeStartTime, limit = 10).map { session ->
            HistoricalRunAverage(session.averageVelocity, session.averageHeartRate)
        }

    suspend fun deleteSession(sessionId: String) {
        database.withWriteTransaction {
            database.runningScoreDao().deleteBySession(sessionId)
            database.runningSessionDao().deleteById(sessionId)
        }
    }

    private fun List<SpeedHeartRateRatio>.toEntities(sessionId: String): List<RunningScoreEntity> =
        map { ratio ->
            require(ratio.speedToHeartRateRatio.isFinite())
            RunningScoreEntity(
                sessionId = sessionId,
                timestamp = ratio.timestampMillis,
                speedHeartRateRatio = ratio.speedToHeartRateRatio,
                relativeEfficiency = ratio.relativeEfficiency,
            )
        }
}
