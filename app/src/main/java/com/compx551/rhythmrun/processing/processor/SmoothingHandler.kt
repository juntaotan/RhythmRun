package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedLocation
import com.compx551.rhythmrun.processing.model.ProcessedReading

/** Applies a five-sample weighted moving average to normalized readings. */
class SmoothingHandler {
    private val window = ArrayDeque<ProcessedReading>()

    fun smooth(reading: ProcessedReading): ProcessedReading {
        window.addLast(reading)
        while (window.size > WINDOW_SIZE) {
            window.removeFirst()
        }

        return reading.copy(
            heartRateBpm = window.weightedAverage { it.heartRateBpm },
            accelerationPerSecond = window.weightedAverage { it.accelerationPerSecond },
            velocityMetersPerSecond = window.weightedAverage { it.velocityMetersPerSecond },
            stepCounterPerSecond = window.weightedAverage { it.stepCounterPerSecond },
            location = smoothLocation(),
        )
    }

    private fun smoothLocation(): ProcessedLocation? {
        val locations = window.mapNotNull(ProcessedReading::location)
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
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
