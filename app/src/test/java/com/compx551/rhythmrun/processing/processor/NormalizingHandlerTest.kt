package com.compx551.rhythmrun.processing.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizingHandlerTest {
    private val handler = NormalizingHandler()

    @Test
    fun convertsSensorUnitsAndDerivesCadenceFromCumulativeSteps() {
        val result = handler.normalize(
            listOf(
                RawReading.StepCounter(10_000, 100),
                RawReading.Velocity(5_000, 18.0, VelocityUnit.KILOMETERS_PER_HOUR),
                RawReading.Acceleration(5_000, 1.0, 0.0, 0.0, AccelerationUnit.STANDARD_GRAVITY),
                RawReading.HeartRate(5_000, 150.0),
                RawReading.StepCounter(20_000, 126),
            ),
        )

        assertEquals(4, result.readings.size)
        assertEquals(150.0, (result.readings[2] as NormalizedReading.HeartRate).beatsPerMinute, 0.0)
        assertEquals(5.0, (result.readings[0] as NormalizedReading.Velocity).metersPerSecond, 1e-9)
        val acceleration = result.readings[1] as NormalizedReading.Acceleration
        assertEquals(9.80665, acceleration.xMetersPerSecondSquared, 1e-9)
        assertEquals(9.80665, acceleration.magnitudeMetersPerSecondSquared, 1e-9)
        val cadence = result.readings[3] as NormalizedReading.Cadence
        assertEquals(156.0, cadence.stepsPerMinute, 1e-9)
        assertEquals(10_000L, cadence.intervalStartMillis)
        assertEquals(RawReading.StepCounter(20_000, 126), result.latestStepCounter)
    }

    @Test
    fun carriesStepBaselineAcrossBatchesAndSkipsResetInterval() {
        val request = ProcessingRequest(
            sessionId = "run-1",
            previousStepCounter = RawReading.StepCounter(10_000, 100),
            readings = listOf(
                RawReading.StepCounter(20_000, 126),
                RawReading.StepCounter(30_000, 2),
                RawReading.StepCounter(40_000, 27),
            ),
        )

        handler.handle(request)

        assertEquals(2, request.normalizedReadings.size)
        assertEquals(156.0, (request.normalizedReadings[0] as NormalizedReading.Cadence).stepsPerMinute, 1e-9)
        assertEquals(150.0, (request.normalizedReadings[1] as NormalizedReading.Cadence).stepsPerMinute, 1e-9)
        assertEquals(RawReading.StepCounter(40_000, 27), request.latestStepCounter)
    }

    @Test
    fun firstStepCounterDoesNotInventCadence() {
        val result = handler.normalize(listOf(RawReading.StepCounter(10_000, 100)))

        assertEquals(emptyList<NormalizedReading>(), result.readings)
        assertEquals(RawReading.StepCounter(10_000, 100), result.latestStepCounter)
    }

    @Test
    fun equalTimestampCannotCreateCadence() {
        val result = handler.normalize(
            listOf(RawReading.StepCounter(10_000, 100)),
            RawReading.StepCounter(10_000, 90),
        )

        assertEquals(emptyList<NormalizedReading>(), result.readings)
        assertEquals(RawReading.StepCounter(10_000, 90), result.latestStepCounter)
    }

    @Test
    fun emptyBatchHasNoStepBaseline() {
        assertNull(handler.normalize(emptyList()).latestStepCounter)
    }

    @Test
    fun passesNormalizedReadingsToNextHandler() {
        var downstreamValue: Double? = null
        handler.setNext(object : ProcessingHandler() {
            override fun process(request: ProcessingRequest): Boolean {
                downstreamValue =
                    (request.normalizedReadings.single() as NormalizedReading.HeartRate).beatsPerMinute
                return true
            }
        })

        handler.handle(ProcessingRequest("run-1", listOf(RawReading.HeartRate(1_000, 145.0))))

        assertEquals(145.0, downstreamValue!!, 0.0)
    }
}
