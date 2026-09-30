package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedReading
import org.junit.Assert.assertEquals
import org.junit.Test

class SmoothingHandlerTest {
    @Test
    fun usesFiveSamplesWithIncreasingWeights() {
        val handler = SmoothingHandler()
        var result = reading(0.0)

        for (value in 1..5) {
            result = handler.smooth(reading(value.toDouble()))
        }

        assertEquals(55.0 / 15.0, result.heartRateBpm, 1e-9)
        assertEquals(55.0 / 15.0, result.velocityMetersPerSecond, 1e-9)
    }

    private fun reading(value: Double) = ProcessedReading(
        timestampMillis = value.toLong(),
        heartRateBpm = value,
        accelerationPerSecond = value,
        velocityMetersPerSecond = value,
        stepCounterPerSecond = value,
    )
}
