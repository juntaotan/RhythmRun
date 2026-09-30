package com.compx551.rhythmrun.processing.processor

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizingHandlerTest {
    @Test
    fun normalizesOneCompleteExerciseEvent() {
        val reading = NormalizingHandler().normalize(
            listOf(
                RawReading.HeartRate(1_000, 140.0),
                RawReading.Acceleration(
                    1_000,
                    1.0,
                    0.0,
                    0.0,
                    AccelerationUnit.STANDARD_GRAVITY,
                ),
                RawReading.Velocity(1_000, 18.0, VelocityUnit.KILOMETERS_PER_HOUR),
                RawReading.StepCounter(1_000, 3),
                RawReading.Location(1_000, -36.85, 174.76, 5.0),
            ),
        )

        assertEquals(140.0, reading.heartRateBpm, 0.0)
        assertEquals(9.80665, reading.accelerationPerSecond, 1e-9)
        assertEquals(5.0, reading.velocityMetersPerSecond, 1e-9)
        assertEquals(3.0, reading.stepCounterPerSecond, 0.0)
        assertEquals(-36.85, reading.location!!.latitude, 0.0)
    }
}
