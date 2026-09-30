package com.compx551.rhythmrun.processing.processor

import org.junit.Assert.assertThrows
import org.junit.Test

class ValidationHandlerTest {
    @Test
    fun acceptsCompletePlausibleEvent() {
        ValidationHandler().validate(event())
    }

    @Test
    fun rejectsMissingRequiredSensor() {
        assertThrows(IllegalArgumentException::class.java) {
            ValidationHandler().validate(event().filterNot { it is RawReading.StepCounter })
        }
    }

    @Test
    fun rejectsImpossibleLocationSpeed() {
        val handler = ValidationHandler()
        handler.validate(event(location = RawReading.Location(1_000, 0.0, 0.0, 5.0)))

        assertThrows(IllegalArgumentException::class.java) {
            handler.validate(event(location = RawReading.Location(2_000, 1.0, 1.0, 5.0)))
        }
    }

    private fun event(location: RawReading.Location? = null): List<RawReading> = buildList {
        add(RawReading.HeartRate(location?.timestampMillis ?: 1_000, 140.0))
        add(RawReading.Acceleration(location?.timestampMillis ?: 1_000, 1.0, 0.0, 0.0))
        add(RawReading.Velocity(location?.timestampMillis ?: 1_000, 4.0))
        add(RawReading.StepCounter(location?.timestampMillis ?: 1_000, 3))
        location?.let(::add)
    }
}
