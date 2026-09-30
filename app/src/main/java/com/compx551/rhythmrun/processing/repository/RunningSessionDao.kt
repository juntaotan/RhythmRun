package com.compx551.rhythmrun.processing.repository

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface RunningSessionDao {
    @Upsert
    suspend fun upsert(session: RunningDetailsEntity)

    @Query("SELECT * FROM running_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun findById(sessionId: String): RunningDetailsEntity?

    @Query("SELECT * FROM running_sessions ORDER BY startTime DESC, sessionId DESC")
    suspend fun getAllNewestFirst(): List<RunningDetailsEntity>

    @Query("SELECT * FROM running_sessions WHERE startTime < :beforeStartTime AND endTime > startTime ORDER BY startTime DESC, sessionId DESC LIMIT :limit")
    suspend fun getRecentBefore(beforeStartTime: Long, limit: Int = 10): List<RunningDetailsEntity>

    @Query("DELETE FROM running_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)
}
