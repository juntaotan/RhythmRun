package com.compx551.rhythmrun.processing.processor

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filters, smooths and measures raw GPS fixes.
 *
 * The default speed limit is equivalent to a 50 metre change between fixes two seconds apart.
 * Coordinates are smoothed with a trailing weighted moving average in which newer fixes receive
 * more weight. [GpsProcessingState] lets callers continue the calculation across transfer batches.
 */
class GpsProcessingHandler(
    private val maximumAccuracyMeters: Double = 25.0,
    private val maximumSpeedMetersPerSecond: Double = 25.0,
    private val smoothingWindowSize: Int = 3,
) : ProcessingHandler() {
    init {
        require(maximumAccuracyMeters > 0.0 && maximumAccuracyMeters.isFinite()) {
            "Maximum GPS accuracy must be positive and finite"
        }
        require(maximumSpeedMetersPerSecond > 0.0 && maximumSpeedMetersPerSecond.isFinite()) {
            "Maximum GPS speed must be positive and finite"
        }
        require(smoothingWindowSize > 0) { "GPS smoothing window size must be positive" }
    }

    override fun process(request: ProcessingRequest): Boolean {
        val result = processLocations(
            locations = request.readings.filterIsInstance<RawReading.Location>(),
            previousState = request.gpsState,
        )
        request.gpsResult = result
        request.nextGpsState = result.stateForNextBatch
        return true
    }

    fun processLocations(
        locations: List<RawReading.Location>,
        previousState: GpsProcessingState = GpsProcessingState(),
    ): GpsProcessingResult {
        val acceptedHistory = ArrayDeque<RawReading.Location>()
        previousState.smoothingHistory
            .sortedBy(RawReading.Location::timestampMillis)
            .takeLast(smoothingWindowSize - 1)
            .forEach(acceptedHistory::addLast)

        var lastAccepted = previousState.lastAcceptedLocation
        var lastSmoothed = previousState.lastSmoothedLocation
        var rejectedForAccuracy = 0
        var rejectedAsOutlier = 0
        var distanceMeters = 0.0
        val processed = mutableListOf<ProcessedGpsLocation>()

        for (location in locations.sortedBy(RawReading.Location::timestampMillis)) {
            if (!location.hasValidCoordinates() ||
                !location.accuracyMeters.isFinite() ||
                location.accuracyMeters < 0.0 ||
                location.accuracyMeters > maximumAccuracyMeters
            ) {
                rejectedForAccuracy++
                continue
            }

            val previous = lastAccepted
            if (previous != null) {
                val elapsedSeconds = (location.timestampMillis - previous.timestampMillis) / 1_000.0
                if (elapsedSeconds <= 0.0) {
                    rejectedAsOutlier++
                    continue
                }
                val speed = distanceBetween(previous, location) / elapsedSeconds
                if (!speed.isFinite() || speed > maximumSpeedMetersPerSecond) {
                    rejectedAsOutlier++
                    continue
                }
            }

            lastAccepted = location
            acceptedHistory.addLast(location)
            while (acceptedHistory.size > smoothingWindowSize) acceptedHistory.removeFirst()

            val smoothed = acceptedHistory.weightedAverage(location)
            val priorSmoothed = lastSmoothed
            if (priorSmoothed != null && smoothed.timestampMillis > priorSmoothed.timestampMillis) {
                distanceMeters += distanceBetween(priorSmoothed, smoothed)
            }
            processed += smoothed
            lastSmoothed = smoothed
        }

        val nextHistory = acceptedHistory.takeLast(smoothingWindowSize - 1)
        return GpsProcessingResult(
            locations = processed,
            distanceMeters = distanceMeters,
            rejectedForAccuracy = rejectedForAccuracy,
            rejectedAsOutlier = rejectedAsOutlier,
            stateForNextBatch = GpsProcessingState(
                lastAcceptedLocation = lastAccepted,
                smoothingHistory = nextHistory,
                lastSmoothedLocation = lastSmoothed,
            ),
        )
    }

    private fun RawReading.Location.hasValidCoordinates(): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0

    private fun ArrayDeque<RawReading.Location>.weightedAverage(
        current: RawReading.Location,
    ): ProcessedGpsLocation {
        var latitudeSum = 0.0
        var longitudeSum = 0.0
        var weightSum = 0.0
        forEachIndexed { index, fix ->
            val weight = (index + 1).toDouble()
            latitudeSum += fix.latitude * weight
            longitudeSum += fix.longitude * weight
            weightSum += weight
        }
        return ProcessedGpsLocation(
            timestampMillis = current.timestampMillis,
            latitude = latitudeSum / weightSum,
            longitude = longitudeSum / weightSum,
            accuracyMeters = current.accuracyMeters,
        )
    }

    private fun distanceBetween(first: RawReading.Location, second: RawReading.Location): Double =
        haversineDistanceMeters(first.latitude, first.longitude, second.latitude, second.longitude)

    private fun distanceBetween(first: ProcessedGpsLocation, second: ProcessedGpsLocation): Double =
        haversineDistanceMeters(first.latitude, first.longitude, second.latitude, second.longitude)

    private fun haversineDistanceMeters(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double,
    ): Double {
        val latitudeDelta = (secondLatitude - firstLatitude).toRadians()
        val longitudeDelta = (secondLongitude - firstLongitude).toRadians()
        val firstLatitudeRadians = firstLatitude.toRadians()
        val secondLatitudeRadians = secondLatitude.toRadians()
        val a = (sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

data class ProcessedGpsLocation(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
)

data class GpsProcessingState(
    val lastAcceptedLocation: RawReading.Location? = null,
    val smoothingHistory: List<RawReading.Location> = emptyList(),
    val lastSmoothedLocation: ProcessedGpsLocation? = null,
)

data class GpsProcessingResult(
    val locations: List<ProcessedGpsLocation> = emptyList(),
    /** Distance added by this batch, including its connection to the preceding batch. */
    val distanceMeters: Double = 0.0,
    val rejectedForAccuracy: Int = 0,
    val rejectedAsOutlier: Int = 0,
    val stateForNextBatch: GpsProcessingState = GpsProcessingState(),
)
