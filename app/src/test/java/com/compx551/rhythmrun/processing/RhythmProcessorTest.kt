package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.processor.AccelerationUnit
import com.compx551.rhythmrun.processing.processor.RawReading
import com.compx551.rhythmrun.processing.processor.VelocityUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmProcessorTest {
    private val baseline = EfficiencyBaseline(
        averageSpeedMetersPerSecond = 2.0,
        averageHeartRateBpm = 100.0,
        historyCount = 1,
    )

    @Test
    fun correctReadingGroupCompletesTheWorkflow() {
        val processor = RhythmProcessor()
        val readings: List<RawReading> = listOf(
            RawReading.HeartRate(1_000, 100.0),
            RawReading.Acceleration(
                timestampMillis = 1_000,
                x = 3.0,
                y = 4.0,
                z = 0.0,
                unit = AccelerationUnit.METERS_PER_SECOND_SQUARED,
            ),
            RawReading.Velocity(1_000, 18.0, VelocityUnit.KILOMETERS_PER_HOUR),
            RawReading.StepCounter(1_000, 3),
            RawReading.Location(1_000, -36.8509, 174.7645, 5.0),
        )

        printList("Correct group sent to processor", readings)
        processor.process(readings, baseline)
        printList("Correct group received from processor", processor.processedReadings.value)

        val result = processor.processedReadings.value.single()
        assertEquals(1_000L, result.timestampMillis)
        assertEquals(100.0, result.heartRateBpm, 0.0)
        assertEquals(5.0, result.accelerationPerSecond, 1e-9)
        assertEquals(5.0, result.velocityMetersPerSecond, 1e-9)
        assertEquals(3.0, result.stepCounterPerSecond, 0.0)
        assertEquals(-36.8509, result.location!!.latitude, 0.0)
        assertEquals(174.7645, result.location!!.longitude, 0.0)
        assertEquals(5.0, result.location!!.accuracyMeters, 0.0)
        assertEquals(2.5, result.efficiency!!, 1e-9)
    }

    @Test
    fun readingGroupWithOneWrongFieldStoresZeroAndKeepsOtherValues() {
        val processor = RhythmProcessor()
        val readings: List<RawReading> = listOf(
            RawReading.HeartRate(1_000, 300.0), // Invalid: maximum accepted value is 250 bpm.
            RawReading.Acceleration(1_000, 3.0, 4.0, 0.0),
            RawReading.Velocity(1_000, 5.0),
            RawReading.StepCounter(1_000, 3),
            RawReading.Location(1_000, -36.8509, 174.7645, 5.0),
        )

        printList("Invalid group sent to processor", readings)
        processor.process(readings, baseline)
        printList("Invalid group received from processor", processor.processedReadings.value)

        val result = processor.processedReadings.value.single()
        assertEquals(0.0, result.heartRateBpm, 0.0)
        assertEquals(5.0, result.accelerationPerSecond, 1e-9)
        assertEquals(5.0, result.velocityMetersPerSecond, 0.0)
        assertEquals(3.0, result.stepCounterPerSecond, 0.0)
        assertEquals(-36.8509, result.location!!.latitude, 0.0)
        assertEquals(0.0, result.efficiency!!, 0.0)
    }

    private fun printList(label: String, values: List<*>) {
        println("$label:")
        if (values.isEmpty()) {
            println("  []")
        } else {
            values.forEach { value -> println("  $value") }
        }
    }
}
