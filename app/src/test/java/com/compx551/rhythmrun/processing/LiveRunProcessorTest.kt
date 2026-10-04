package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.communication.RhythmReading
import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRunProcessorTest {
    @Test
    fun onReading_aggregatesSensorDataAndProducesProcessedReadingWithEfficiency() {
        val baseline = EfficiencyBaseline(
            averageSpeedMetersPerSecond = 2.0,
            averageHeartRateBpm = 120.0,
            historyCount = 1,
        )
        val processor = LiveRunProcessor(baseline = baseline)

        // Simulate incoming RhythmReading from watch/API
        val hrReading = RhythmReading(
            dataType = "hr",
            sessionId = "test-session",
            sequence = 1L,
            timestamp = 1_000L,
            heartRateBpm = 130f,
            heartRateAvailable = true,
        )
        val accelReading = RhythmReading(
            dataType = "accel",
            sessionId = "test-session",
            sequence = 2L,
            timestamp = 1_000L,
            accelerationX = 3f,
            accelerationY = 4f,
            accelerationZ = 0f,
        )
        val cadenceReading = RhythmReading(
            dataType = "cadence",
            sessionId = "test-session",
            sequence = 3L,
            timestamp = 1_000L,
            cadenceStepsPerMinute = 160f,
        )

        processor.onReading(hrReading)
        processor.onReading(accelReading)
        processor.onReading(cadenceReading)

        val results = processor.processedReadings.value
        assertTrue("Processed readings should not be empty", results.isNotEmpty())
        assertEquals("test-session", processor.currentSessionId)

        val latest = results.last()
        assertEquals(130.0, latest.heartRateBpm, 0.01)
        // 3 samples weighted average: (0*1 + 5*2 + 5*3) / 6 = 4.167 m/s²
        assertEquals(25.0 / 6.0, latest.accelerationPerSecond, 0.01)
        assertTrue("Speed should be greater than 0", latest.velocityMetersPerSecond > 0.0)
        assertNotNull("Efficiency should be calculated", latest.efficiency)
        assertTrue("Efficiency should be greater than 0", latest.efficiency!! > 0.0)
    }

    @Test
    fun onReading_withLocation_producesProcessedLocation() {
        val processor = LiveRunProcessor()

        val locationReading = RhythmReading(
            dataType = "location",
            sessionId = "test-session",
            sequence = 1L,
            timestamp = 1_000L,
            latitude = -36.8509,
            longitude = 174.7645,
            accuracyMeters = 5.0,
        )

        processor.onReading(locationReading)

        val results = processor.processedReadings.value
        assertTrue("Processed readings should not be empty", results.isNotEmpty())

        val latest = results.last()
        assertNotNull("Location should be present", latest.location)
        assertEquals(-36.8509, latest.location!!.latitude, 0.001)
        assertEquals(174.7645, latest.location!!.longitude, 0.001)
    }

    @Test
    fun startsWithoutEfficiencyThenUsesHistoricalBaseline() {
        val processor = LiveRunProcessor()
        processor.onReading(RhythmReading("hr", "run", 0, 1_000, heartRateBpm = 120f))
        processor.onReading(RhythmReading("cadence", "run", 1, 2_000, cadenceStepsPerMinute = 160f))
        assertNull(processor.processedReadings.value.last().efficiency)

        processor.setBaseline(EfficiencyBaseline(2.0, 120.0, 3))
        processor.onReading(RhythmReading("cadence", "run", 2, 3_000, cadenceStepsPerMinute = 160f))
        assertNotNull(processor.processedReadings.value.last().efficiency)
    }
}
