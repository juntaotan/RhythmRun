package com.compx551.rhythmrun.processing.processor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ValidationHandlerTest {
    private val handler = ValidationHandler()

    @Test
    fun acceptsPlausibleOutOfOrderSamplesAndStepCounterReset() = runBlocking {
        var forwarded = false
        handler.setNext(object : ProcessingHandler() {
            override fun process(request: ProcessingRequest): Boolean {
                forwarded = true
                return true
            }
        })

        handler.handle(
            ProcessingRequest(
                sessionId = "run-1",
                readings = listOf(
                    RawReading.StepCounter(2_000, 4),
                    RawReading.HeartRate(1_000, 145.0),
                    RawReading.Acceleration(1_000, 0.0, 1.0, 0.0, AccelerationUnit.STANDARD_GRAVITY),
                    RawReading.Velocity(1_000, 18.0, VelocityUnit.KILOMETERS_PER_HOUR),
                    RawReading.StepCounter(1_500, 100),
                ),
            ),
        )

        assertEquals(true, forwarded)
    }

    @Test
    fun rejectsMalformedOrImplausibleSensorSamples() {
        val invalid = listOf(
            RawReading.HeartRate(1_000, Double.NaN),
            RawReading.HeartRate(1_000, 300.0),
            RawReading.Acceleration(1_000, Double.POSITIVE_INFINITY, 0.0, 0.0),
            RawReading.Acceleration(1_000, 21.0, 0.0, 0.0, AccelerationUnit.STANDARD_GRAVITY),
            RawReading.Velocity(1_000, -1.0),
            RawReading.Velocity(1_000, 91.0, VelocityUnit.KILOMETERS_PER_HOUR),
            RawReading.StepCounter(1_000, -1),
            RawReading.HeartRate(-1, 100.0),
        )

        invalid.forEach { reading ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { handler.handle(ProcessingRequest("run-1", listOf(reading))) }
            }
        }
    }

    @Test
    fun rejectsBlankSessionId() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { handler.handle(ProcessingRequest(" ", emptyList())) }
        }
    }

    @Test
    fun leavesGpsQualityChecksToGpsHandler() = runBlocking {
        handler.handle(
            ProcessingRequest(
                sessionId = "run-1",
                readings = listOf(RawReading.Location(1_000, 95.0, 0.0, 500.0)),
            ),
        )
    }
}
