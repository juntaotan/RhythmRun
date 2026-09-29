package com.compx551.rhythmrun.processing.processor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsProcessingHandlerTest {
    @Test
    fun filtersPoorAccuracyAndImpossibleSpeedBeforeSmoothing() {
        val result = GpsProcessingHandler(smoothingWindowSize = 1).processLocations(
            listOf(
                location(timestamp = 0, longitude = 0.0, accuracy = 5.0),
                location(timestamp = 1_000, longitude = 0.0001, accuracy = 30.0),
                // About 56 metres in two seconds: faster than the default 25 m/s limit.
                location(timestamp = 2_000, longitude = 0.0005, accuracy = 5.0),
                location(timestamp = 4_000, longitude = 0.0002, accuracy = 5.0),
            ),
        )

        assertEquals(listOf(0L, 4_000L), result.locations.map { it.timestampMillis })
        assertEquals(1, result.rejectedForAccuracy)
        assertEquals(1, result.rejectedAsOutlier)
        assertTrue(result.distanceMeters in 22.0..22.4)
    }

    @Test
    fun appliesARecencyWeightedMovingAverage() {
        val result = GpsProcessingHandler(
            maximumSpeedMetersPerSecond = 1_000_000.0,
            smoothingWindowSize = 3,
        ).processLocations(
            listOf(
                location(timestamp = 0, longitude = 0.0),
                location(timestamp = 1_000, longitude = 3.0),
                location(timestamp = 2_000, longitude = 6.0),
            ),
        )

        assertEquals(0.0, result.locations[0].longitude, 1e-9)
        assertEquals(2.0, result.locations[1].longitude, 1e-9)
        assertEquals(4.0, result.locations[2].longitude, 1e-9)
    }

    @Test
    fun preservesFilteringSmoothingAndDistanceAcrossBatches() {
        val handler = GpsProcessingHandler(
            maximumSpeedMetersPerSecond = 100.0,
            smoothingWindowSize = 2,
        )
        val first = handler.processLocations(
            listOf(
                location(timestamp = 0, longitude = 0.0),
                location(timestamp = 1_000, longitude = 0.0001),
            ),
        )
        val second = handler.processLocations(
            locations = listOf(location(timestamp = 2_000, longitude = 0.0002)),
            previousState = first.stateForNextBatch,
        )

        assertEquals((0.0001 + 2 * 0.0002) / 3, second.locations.single().longitude, 1e-9)
        assertTrue(first.distanceMeters > 0.0)
        assertTrue(second.distanceMeters > 0.0)
        assertEquals(2_000L, second.stateForNextBatch.lastAcceptedLocation?.timestampMillis)
    }

    @Test
    fun writesResultToRequestAndForwardsTheChain() = runBlocking {
        val handler = GpsProcessingHandler(smoothingWindowSize = 1)
        var forwarded = false
        handler.setNext(object : ProcessingHandler() {
            override fun process(request: ProcessingRequest): Boolean {
                forwarded = request.gpsResult.locations.isNotEmpty()
                return true
            }
        })
        val request = ProcessingRequest(
            sessionId = "run-1",
            readings = listOf(
                location(timestamp = 0, longitude = 0.0),
                location(timestamp = 2_000, longitude = 0.0001),
            ),
        )

        handler.handle(request)

        assertTrue(forwarded)
        assertEquals(request.gpsResult.stateForNextBatch, request.nextGpsState)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidConfiguration() {
        GpsProcessingHandler(maximumAccuracyMeters = 0.0)
    }

    private fun location(
        timestamp: Long,
        longitude: Double,
        accuracy: Double = 5.0,
    ) = RawReading.Location(
        timestampMillis = timestamp,
        latitude = 0.0,
        longitude = longitude,
        accuracyMeters = accuracy,
    )
}
