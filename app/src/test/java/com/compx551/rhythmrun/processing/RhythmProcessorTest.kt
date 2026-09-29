package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.processor.HistoricalRunAverage
import com.compx551.rhythmrun.processing.processor.NormalizedReading
import com.compx551.rhythmrun.processing.processor.PersistenceHandler
import com.compx551.rhythmrun.processing.processor.ProcessingRequest
import com.compx551.rhythmrun.processing.processor.SpeedHeartRateRatio
import com.compx551.rhythmrun.processing.repository.RunningDetailsEntity
import com.compx551.rhythmrun.processing.repository.RunningStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RhythmProcessorTest {
    @Test
    fun chainSavesSessionAndRelativeEfficiencyTogether() = runBlocking {
        val store = FakeRunningStore(listOf(HistoricalRunAverage(1.0, 100.0)))
        val session = session("run-1")
        val request = ProcessingRequest(
            sessionId = session.sessionId,
            sessionDetails = session,
            readings = emptyList(),
            smoothedReadings = listOf(
                NormalizedReading.HeartRate(1_000, 100.0),
                NormalizedReading.Velocity(1_000, 2.0),
            ),
        )

        RhythmProcessor(store).calculateAndStoreEfficiency(request)

        assertEquals(1, store.persistCalls)
        assertEquals(request.analysisResult?.ratios, store.savedRatios)
        assertEquals(session, store.savedSession)
        assertEquals(1_000L, store.requestedHistoryBefore)
        assertEquals(1, store.savedRatios.size)
        assertEquals(0.02, store.savedRatios.single().speedToHeartRateRatio, 1e-9)
        assertEquals(2.0, store.savedRatios.single().relativeEfficiency!!, 1e-9)
    }

    @Test
    fun invalidRequestDoesNotReachPersistence() {
        val store = FakeRunningStore(emptyList())
        val request = ProcessingRequest(
            sessionId = "run-1",
            sessionDetails = session("different-id"),
            readings = emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { RhythmProcessor(store).calculateAndStoreEfficiency(request) }
        }
        assertEquals(0, store.persistCalls)
    }

    @Test
    fun noHistoryStillSavesSessionWithNullEfficiencyIndex() = runBlocking {
        val store = FakeRunningStore(emptyList())
        val request = ProcessingRequest(
            sessionId = "run-1",
            sessionDetails = session("run-1"),
            readings = emptyList(),
            smoothedReadings = listOf(
                NormalizedReading.HeartRate(1_000, 100.0),
                NormalizedReading.Velocity(1_000, 2.0),
            ),
        )

        RhythmProcessor(store).calculateAndStoreEfficiency(request)

        assertEquals(1, store.persistCalls)
        assertEquals(null, store.savedRatios.single().relativeEfficiency)
    }

    @Test
    fun persistenceRequiresThePreviousAnalysisStep() {
        val store = FakeRunningStore(emptyList())
        val request = ProcessingRequest(
            sessionId = "run-1",
            sessionDetails = session("run-1"),
            readings = emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { PersistenceHandler(store).handle(request) }
        }
        assertEquals(0, store.persistCalls)
    }

    private fun session(id: String) = RunningDetailsEntity(
        sessionId = id,
        timestamp = 1_000,
        runingId = id,
        startTime = 1_000,
        endTime = 2_000,
        averageHeartRate = 100.0,
        averageAccelerate = 1.0,
        averageVelocity = 2.0,
        averageCadence = 160.0,
    )

    private class FakeRunningStore(
        private val history: List<HistoricalRunAverage>,
    ) : RunningStore {
        var requestedHistoryBefore: Long? = null
        var persistCalls = 0
        var savedSession: RunningDetailsEntity? = null
        var savedRatios: List<SpeedHeartRateRatio> = emptyList()

        override suspend fun getHistoricalAverages(beforeStartTime: Long): List<HistoricalRunAverage> {
            requestedHistoryBefore = beforeStartTime
            return history
        }

        override suspend fun persistBatch(session: RunningDetailsEntity, ratios: List<SpeedHeartRateRatio>) {
            persistCalls++
            savedSession = session
            savedRatios = ratios
        }
    }
}
