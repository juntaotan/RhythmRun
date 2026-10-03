package com.compx551.rhythmrun.processing.processor

/** A trailing sample-count moving average, maintained independently for each sensor type. */
class SmoothingHandler(private val windowSize: Int = 3) : ProcessingHandler() {
    init {
        require(windowSize > 0) { "Moving-average window size must be positive" }
    }

    override fun process(request: ProcessingRequest): Boolean {
        val result = smooth(request.normalizedReadings, request.smoothingHistory)
        request.smoothedReadings = result.readings
        request.nextSmoothingHistory = result.historyForNextBatch
        return true
    }

    fun smooth(
        readings: List<NormalizedReading>,
        history: List<NormalizedReading> = emptyList(),
    ): SmoothingResult {
        val heartRates = ArrayDeque<NormalizedReading.HeartRate>()
        val accelerations = ArrayDeque<NormalizedReading.Acceleration>()
        val velocities = ArrayDeque<NormalizedReading.Velocity>()
        val cadences = ArrayDeque<NormalizedReading.Cadence>()
        val firstNewTimestampByType = readings.groupBy(::typeOf)
            .mapValues { (_, values) -> values.minOf(NormalizedReading::timestampMillis) }

        // History is input context, never output again. Ignore stale or overlapping history.
        for (reading in history.sortedBy(NormalizedReading::timestampMillis)) {
            val firstNew = firstNewTimestampByType[typeOf(reading)]
            if (firstNew != null && reading.timestampMillis >= firstNew) continue
            when (reading) {
                is NormalizedReading.HeartRate -> heartRates.pushLimited(reading)
                is NormalizedReading.Acceleration -> accelerations.pushLimited(reading)
                is NormalizedReading.Velocity -> velocities.pushLimited(reading)
                is NormalizedReading.Cadence -> cadences.pushLimited(reading)
            }
        }

        val smoothed = readings.sortedBy(NormalizedReading::timestampMillis).map { reading ->
            when (reading) {
                is NormalizedReading.HeartRate -> {
                    heartRates.pushLimited(reading)
                    reading.copy(beatsPerMinute = heartRates.map { it.beatsPerMinute }.average())
                }

                is NormalizedReading.Acceleration -> {
                    accelerations.pushLimited(reading)
                    reading.copy(
                        xMetersPerSecondSquared = accelerations.map { it.xMetersPerSecondSquared }.average(),
                        yMetersPerSecondSquared = accelerations.map { it.yMetersPerSecondSquared }.average(),
                        zMetersPerSecondSquared = accelerations.map { it.zMetersPerSecondSquared }.average(),
                        magnitudeMetersPerSecondSquared =
                            accelerations.map { it.magnitudeMetersPerSecondSquared }.average(),
                    )
                }

                is NormalizedReading.Velocity -> {
                    velocities.pushLimited(reading)
                    reading.copy(metersPerSecond = velocities.map { it.metersPerSecond }.average())
                }

                is NormalizedReading.Cadence -> {
                    cadences.pushLimited(reading)
                    reading.copy(stepsPerMinute = cadences.map { it.stepsPerMinute }.average())
                }
            }
        }

        val nextHistory = buildList {
            addAll(heartRates.takeLast(windowSize - 1))
            addAll(accelerations.takeLast(windowSize - 1))
            addAll(velocities.takeLast(windowSize - 1))
            addAll(cadences.takeLast(windowSize - 1))
        }.sortedBy(NormalizedReading::timestampMillis)

        return SmoothingResult(smoothed, nextHistory)
    }

    private fun <T> ArrayDeque<T>.pushLimited(value: T) {
        addLast(value)
        while (size > windowSize) {
            removeFirst()
        }
    }

    private fun typeOf(reading: NormalizedReading): Class<out NormalizedReading> =
        reading.javaClass
}

data class SmoothingResult(
    val readings: List<NormalizedReading>,
    val historyForNextBatch: List<NormalizedReading>,
)
