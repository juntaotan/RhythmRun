package com.compx551.rhythmrun.domain.repository

import com.compx551.rhythmrun.domain.model.AnalysisWindowRecord
import com.compx551.rhythmrun.domain.model.CueEventRecord
import com.compx551.rhythmrun.domain.model.LocationFixRecord
import com.compx551.rhythmrun.domain.model.RawSensorRecord
import com.compx551.rhythmrun.domain.model.RunRecord
import com.compx551.rhythmrun.domain.model.RunDataBatch
import com.compx551.rhythmrun.domain.model.RunPlan
import com.compx551.rhythmrun.domain.model.RunSessionState
import com.compx551.rhythmrun.domain.model.StageChangeRecord
import kotlinx.coroutines.flow.StateFlow

interface RunRepository {
    val records: StateFlow<List<RunRecord>>
    val activePlan: StateFlow<RunPlan?>

    suspend fun upsert(record: RunRecord)

    suspend fun findById(sessionId: String): RunRecord?

    suspend fun createSession(plan: RunPlan)

    suspend fun updateSessionState(
        sessionId: String,
        state: RunSessionState,
        endEpochMillis: Long? = null,
    )

    /** Transactional, replay-safe persistence entry point for Parts 2 and 3. */
    suspend fun persistBatch(batch: RunDataBatch)

    /** Part 3 reads the stored source data through these methods. */
    suspend fun rawReadings(sessionId: String): List<RawSensorRecord>
    suspend fun locationFixes(sessionId: String): List<LocationFixRecord>
    suspend fun stageChanges(sessionId: String): List<StageChangeRecord>
    suspend fun cueEvents(sessionId: String): List<CueEventRecord>
    suspend fun analysisWindows(sessionId: String): List<AnalysisWindowRecord>
}
