package com.compx551.rhythmrun.processing.repository

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface RunningScoreDao {
    @Upsert
    suspend fun upsertAll(scores: List<RunningScoreEntity>)

    @Query("SELECT * FROM running_Score WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: String): List<RunningScoreEntity>

    @Query("SELECT * FROM running_Score WHERE sessionId = :sessionId AND timestamp = :timestamp LIMIT 1")
    suspend fun findAt(sessionId: String, timestamp: Long): RunningScoreEntity?

    @Query("DELETE FROM running_Score WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
