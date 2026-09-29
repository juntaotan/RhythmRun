package com.compx551.rhythmrun.processing.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzingHandlerTest {
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
        handler.handle(first)

        assertEquals(2, first.speedHeartRateRatios.size)
        assertEquals(0.0, first.speedHeartRateRatios[0].speedToHeartRateRatio, 0.0)
        assertEquals(0.02, first.speedHeartRateRatios[1].speedToHeartRateRatio, 1e-9)

        val second = ProcessingRequest(
            sessionId = "run-1",
            readings = emptyList(),
            smoothedReadings = listOf(NormalizedReading.Velocity(4_000, 3.0)),
            previousSmoothedHeartRate = first.latestSmoothedHeartRate,
        )
        handler.handle(second)

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

        handler.handle(request)

        assertEquals(request.speedHeartRateRatios, forwarded)
    }
}
