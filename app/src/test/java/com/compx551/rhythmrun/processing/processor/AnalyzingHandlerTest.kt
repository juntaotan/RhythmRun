package com.compx551.rhythmrun.processing.processor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzingHandlerTest {
    @Test
    fun emptyHistoryHasNoBaseline() {
        assertNull(AnalyzingHandler().calculateBaseline(emptyList()))
    }

    @Test
    fun fewerThanTenRunsUseAllAvailableHistory() {
        val baseline = AnalyzingHandler().calculateBaseline(
            listOf(
                HistoricalRunAverage(2.0, 100.0),
                HistoricalRunAverage(4.0, 120.0),
                HistoricalRunAverage(6.0, 140.0),
            ),
        )!!

        assertEquals(3, baseline.historyCount)
        assertEquals(4.0, baseline.averageSpeedMetersPerSecond, 1e-9)
        assertEquals(120.0, baseline.averageHeartRateBpm, 1e-9)
    }

    @Test
    fun moreThanTenRunsIgnoreOlderHistory() {
        val newestTen = List(10) { HistoricalRunAverage(3.0, 120.0) }
        val baseline = AnalyzingHandler().calculateBaseline(
            newestTen + listOf(
                HistoricalRunAverage(30.0, 200.0),
                HistoricalRunAverage(40.0, 220.0),
            ),
        )!!

        assertEquals(10, baseline.historyCount)
        assertEquals(3.0, baseline.averageSpeedMetersPerSecond, 1e-9)
        assertEquals(120.0, baseline.averageHeartRateBpm, 1e-9)
    }

    @Test
    fun invalidHistoricalValuesCannotCreateABaseline() {
        assertNull(
            AnalyzingHandler().calculateBaseline(
                listOf(HistoricalRunAverage(0.0, 100.0), HistoricalRunAverage(2.0, Double.NaN)),
            ),
        )
    }

    @Test
    fun computesCurrentRatioAndPairsSameTimestampRegardlessOfInputOrder() {
        val result = AnalyzingHandler().analyze(
            readings = listOf(
                NormalizedReading.Velocity(1_000, 3.0),
                NormalizedReading.HeartRate(1_000, 150.0),
                NormalizedReading.Velocity(2_000, 4.0),
                NormalizedReading.HeartRate(2_000, 100.0),
            ),
        )

        assertEquals(2, result.ratios.size)
        assertEquals(3.0 / 150.0, result.ratios[0].speedToHeartRateRatio, 1e-9)
        assertEquals(4.0 / 100.0, result.ratios[1].speedToHeartRateRatio, 1e-9)
        assertEquals(2_000L, result.ratios[1].timestampMillis)
        assertEquals(null, result.ratios[1].relativeEfficiency)
    }

    @Test
    fun calculatesRelativeEfficiencyUsingHistoricalAverages() {
        val baseline = EfficiencyBaseline(2.0, 100.0, historyCount = 3)
        val result = AnalyzingHandler().analyze(
            readings = listOf(
                NormalizedReading.HeartRate(1_000, 150.0),
                NormalizedReading.Velocity(1_000, 3.0),
                NormalizedReading.HeartRate(2_000, 100.0),
                NormalizedReading.Velocity(2_000, 4.0),
            ),
            baseline = baseline,
        )

        assertEquals(1.0, result.ratios[0].relativeEfficiency!!, 1e-9)
        assertEquals(2.0, result.ratios[1].relativeEfficiency!!, 1e-9)
    }

    @Test
    fun carriesLatestHeartRateAcrossBatchesWithoutCreatingBaseline() {
        val handler = AnalyzingHandler()
        val first = ProcessingRequest(
            sessionId = "run-1",
            readings = emptyList(),
            smoothedReadings = listOf(
                NormalizedReading.HeartRate(1_000, 100.0),
                NormalizedReading.Velocity(2_000, 0.0),
                NormalizedReading.Velocity(3_000, 2.0),
            ),
        )
        runBlocking { handler.handle(first) }

        assertEquals(2, first.speedHeartRateRatios.size)
        assertEquals(0.0, first.speedHeartRateRatios[0].speedToHeartRateRatio, 0.0)
        assertEquals(0.02, first.speedHeartRateRatios[1].speedToHeartRateRatio, 1e-9)

        val second = ProcessingRequest(
            sessionId = "run-1",
            readings = emptyList(),
            smoothedReadings = listOf(NormalizedReading.Velocity(4_000, 3.0)),
            previousSmoothedHeartRate = first.latestSmoothedHeartRate,
        )
        runBlocking { handler.handle(second) }

        assertEquals(0.03, second.speedHeartRateRatios.single().speedToHeartRateRatio, 1e-9)
    }

    @Test
    fun skipsMissingStaleAndInvalidHeartRate() {
        val result = AnalyzingHandler(maxHeartRateAgeMillis = 2_000).analyze(
            readings = listOf(
                NormalizedReading.Velocity(1_000, 2.0),
                NormalizedReading.HeartRate(2_000, 100.0),
                NormalizedReading.Velocity(5_000, 2.0),
                NormalizedReading.HeartRate(6_000, 0.0),
                NormalizedReading.Velocity(6_000, 2.0),
            ),
        )

        assertTrue(result.ratios.isEmpty())
    }

    @Test
    fun forwardsRatiosToNextHandler() {
        val handler = AnalyzingHandler()
        var forwarded: List<SpeedHeartRateRatio>? = null
        handler.setNext(object : ProcessingHandler() {
            override fun process(request: ProcessingRequest): Boolean {
                forwarded = request.speedHeartRateRatios
                return true
            }
        })
        val request = ProcessingRequest(
            sessionId = "run-1",
            readings = emptyList(),
            smoothedReadings = listOf(
                NormalizedReading.HeartRate(1_000, 100.0),
                NormalizedReading.Velocity(1_000, 2.0),
            ),
        )

        runBlocking { handler.handle(request) }

        assertEquals(request.speedHeartRateRatios, forwarded)
    }
}
