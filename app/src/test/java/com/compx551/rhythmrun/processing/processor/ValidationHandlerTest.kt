package com.compx551.rhythmrun.processing.processor

import org.junit.Assert.assertEquals
import org.junit.Test

class ValidationHandlerTest {
    @Test
    fun acceptsCompletePlausibleEvent() {
        ValidationHandler().validate(event())
    }

    @Test
    fun replacesOnlyInvalidSensorValue() {
        val readings = event().map {
            if (it is RawReading.HeartRate) it.copy(beatsPerMinute = 300.0) else it
        }

        val validated = ValidationHandler().validate(readings)

        assertEquals(0.0, validated.filterIsInstance<RawReading.HeartRate>().single().beatsPerMinute, 0.0)
        assertEquals(4.0, validated.filterIsInstance<RawReading.Velocity>().single().value, 0.0)
        assertEquals(3L, validated.filterIsInstance<RawReading.StepCounter>().single().stepsPerSecond)
    }

    @Test
    fun replacesImpossibleLocationWithZeroValues() {
        val handler = ValidationHandler()
        handler.validate(event(location = RawReading.Location(1_000, 0.0, 0.0, 5.0)))

        val validated = handler.validate(
            event(location = RawReading.Location(2_000, 1.0, 1.0, 5.0)),
        )

        val location = validated.filterIsInstance<RawReading.Location>().single()
        assertEquals(0.0, location.latitude, 0.0)
        assertEquals(0.0, location.longitude, 0.0)
        assertEquals(0.0, location.accuracyMeters, 0.0)
    }

    private fun event(location: RawReading.Location? = null): List<RawReading> = buildList {
        add(RawReading.HeartRate(location?.timestampMillis ?: 1_000, 140.0))
        add(RawReading.Acceleration(location?.timestampMillis ?: 1_000, 1.0, 0.0, 0.0))
        add(RawReading.Velocity(location?.timestampMillis ?: 1_000, 4.0))
        add(RawReading.StepCounter(location?.timestampMillis ?: 1_000, 3))
        location?.let(::add)
    }
}
