package com.compx551.rhythmrun.processing.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SmoothingHandlerTest {
    @Test
    fun averagesEachSignalSeparatelyWithoutChangingTimestamps() {
        val result = SmoothingHandler(windowSize = 3).smooth(
            listOf(
                NormalizedReading.HeartRate(1_000, 100.0),
                NormalizedReading.Velocity(1_000, 2.0),
                NormalizedReading.Acceleration(1_000, 1.0, 2.0, 3.0, 4.0),
                NormalizedReading.Cadence(1_000, 120.0, 0),
                NormalizedReading.HeartRate(2_000, 110.0),
                NormalizedReading.Velocity(2_000, 4.0),
                NormalizedReading.Acceleration(2_000, 3.0, 4.0, 5.0, 6.0),
                NormalizedReading.Cadence(2_000, 160.0, 1_000),
                NormalizedReading.HeartRate(3_000, 130.0),
                NormalizedReading.HeartRate(4_000, 160.0),
            ),
        ).readings

        assertEquals(100.0, (result[0] as NormalizedReading.HeartRate).beatsPerMinute, 1e-9)
        assertEquals(105.0, (result[4] as NormalizedReading.HeartRate).beatsPerMinute, 1e-9)
        assertEquals(340.0 / 3, (result[8] as NormalizedReading.HeartRate).beatsPerMinute, 1e-9)
        assertEquals(400.0 / 3, (result[9] as NormalizedReading.HeartRate).beatsPerMinute, 1e-9)
        assertEquals(3.0, (result[5] as NormalizedReading.Velocity).metersPerSecond, 1e-9)
        val acceleration = result[6] as NormalizedReading.Acceleration
        assertEquals(2.0, acceleration.xMetersPerSecondSquared, 1e-9)
        assertEquals(3.0, acceleration.yMetersPerSecondSquared, 1e-9)
        assertEquals(4.0, acceleration.zMetersPerSecondSquared, 1e-9)
        assertEquals(5.0, acceleration.magnitudeMetersPerSecondSquared, 1e-9)
        val cadence = result[7] as NormalizedReading.Cadence
        assertEquals(140.0, cadence.stepsPerMinute, 1e-9)
        assertEquals(2_000L, cadence.timestampMillis)
        assertEquals(1_000L, cadence.intervalStartMillis)
    }

    @Test
    fun carriesRawHistoryAcrossBatchesAndForwardsSmoothedReadings() {
        val handler = SmoothingHandler(windowSize = 3)
        val first = ProcessingRequest(
            "run-1",
            emptyList(),
            normalizedReadings = listOf(
                NormalizedReading.HeartRate(1_000, 100.0),
                NormalizedReading.HeartRate(2_000, 110.0),
                NormalizedReading.HeartRate(3_000, 120.0),
            ),
        )
        handler.handle(first)

        var forwarded: List<NormalizedReading>? = null
        handler.setNext(object : ProcessingHandler() {
            override fun process(request: ProcessingRequest): Boolean {
                forwarded = request.smoothedReadings
                return true
            }
        })
        val second = ProcessingRequest(
            "run-1",
            emptyList(),
            normalizedReadings = listOf(NormalizedReading.HeartRate(4_000, 130.0)),
            smoothingHistory = first.nextSmoothingHistory,
        )
        handler.handle(second)

        assertEquals(
            listOf(NormalizedReading.HeartRate(2_000, 110.0), NormalizedReading.HeartRate(3_000, 120.0)),
            first.nextSmoothingHistory,
        )
        assertEquals(120.0, (second.smoothedReadings.single() as NormalizedReading.HeartRate).beatsPerMinute, 1e-9)
        assertEquals(second.smoothedReadings, forwarded)
        assertEquals(2, second.nextSmoothingHistory.size)
    }

    @Test
    fun windowOfOneLeavesValuesUnchangedAndKeepsNoHistory() {
        val reading = NormalizedReading.Velocity(1_000, 5.0)
        val result = SmoothingHandler(windowSize = 1).smooth(listOf(reading))

        assertEquals(listOf(reading), result.readings)
        assertEquals(emptyList<NormalizedReading>(), result.historyForNextBatch)
    }

    @Test
    fun rejectsNonPositiveWindow() {
        assertThrows(IllegalArgumentException::class.java) { SmoothingHandler(windowSize = 0) }
    }
}
