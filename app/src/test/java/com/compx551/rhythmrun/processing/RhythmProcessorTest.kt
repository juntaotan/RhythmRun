package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.processor.RawReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RhythmProcessorTest {
    @Test
    fun processesAndPersistsOneCompleteReading() {
        val processor = RhythmProcessor()

        processor.process(
            readings = event(speed = 4.0, heartRate = 100.0),
            baseline = EfficiencyBaseline(2.0, 100.0, 1),
        )

        val reading = processor.processedReadings.value.single()
        assertEquals(4.0, reading.velocityMetersPerSecond, 0.0)
        assertEquals(2.0, reading.efficiency!!, 1e-9)
    }

    @Test
    fun invalidEventIsNotPersisted() {
        val processor = RhythmProcessor()

        assertThrows(IllegalArgumentException::class.java) {
            processor.process(
                readings = event(speed = 4.0, heartRate = 300.0),
                baseline = EfficiencyBaseline(2.0, 100.0, 1),
            )
        }

        assertEquals(emptyList<Any>(), processor.processedReadings.value)
    }

    private fun event(speed: Double, heartRate: Double): List<RawReading> = listOf(
        RawReading.HeartRate(1_000, heartRate),
        RawReading.Acceleration(1_000, 1.0, 0.0, 0.0),
        RawReading.Velocity(1_000, speed),
        RawReading.StepCounter(1_000, 3),
    )
}
