package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedLocation
import com.compx551.rhythmrun.processing.model.ProcessedReading

/** Applies a five-sample weighted moving average before analysis. */
class SmoothingHandler : ProcessingHandler() {
    private val window = ArrayDeque<ProcessedReading>()

    override fun process(request: ProcessingRequest): Boolean {
        val currentReading = requireNotNull(request.processedReading) {
            "Normalization must complete before smoothing"
        }

        window.addLast(currentReading)
        while (window.size > WINDOW_SIZE) {
            window.removeFirst()
        }

        request.processedReading = currentReading.copy(
            heartRateBpm = window.weightedAverage { it.heartRateBpm },
            accelerationPerSecond = window.weightedAverage { it.accelerationPerSecond },
            velocityMetersPerSecond = window.weightedAverage { it.velocityMetersPerSecond },
            stepCounterPerSecond = window.weightedAverage { it.stepCounterPerSecond },
            location = smoothLocation(),
        )
        return true
    }

    private fun smoothLocation(): ProcessedLocation? {
        val locations = window.mapNotNull(ProcessedReading::location)
        if (locations.isEmpty()) return null

        return ProcessedLocation(
            latitude = locations.weightedAverage { it.latitude },
            longitude = locations.weightedAverage { it.longitude },
            accuracyMeters = locations.weightedAverage { it.accuracyMeters },
        )
    }

    private fun <T> Collection<T>.weightedAverage(value: (T) -> Double): Double {
        var weightedSum = 0.0
        var weightSum = 0.0
        forEachIndexed { index, item ->
            val weight = (index + 1).toDouble()
            weightedSum += value(item) * weight
            weightSum += weight
        }
        return weightedSum / weightSum
    }

    private companion object {
        const val WINDOW_SIZE = 5
    }
}
