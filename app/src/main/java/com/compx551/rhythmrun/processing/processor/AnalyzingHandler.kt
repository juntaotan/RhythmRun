package com.compx551.rhythmrun.processing.processor

/** Computes the current speed-to-heart-rate ratio from smoothed readings. */
class AnalyzingHandler(private val maxHeartRateAgeMillis: Long = 10_000L) : ProcessingHandler() {
    init {
        require(maxHeartRateAgeMillis >= 0) { "Maximum HR age cannot be negative" }
    }

    override fun process(request: ProcessingRequest): Boolean {
        val result = analyze(
            request.smoothedReadings,
            request.previousSmoothedHeartRate,
        )
        request.speedHeartRateRatios = result.ratios
        request.latestSmoothedHeartRate = result.latestHeartRate
        return true
    }

    fun analyze(
        readings: List<NormalizedReading>,
        previousHeartRate: NormalizedReading.HeartRate? = null,
    ): AnalysisResult {
        var latestHeartRate = previousHeartRate
        val ratios = mutableListOf<SpeedHeartRateRatio>()

        // Pair each speed timestamp with the most recent HR on or before it. Never use a
        // future reading or an HR older than the configured age limit.
        val ordered = readings.sortedWith(
            compareBy<NormalizedReading> { it.timestampMillis }
                .thenBy { if (it is NormalizedReading.HeartRate) 0 else 1 },
        )
        for (reading in ordered) {
            when (reading) {
                is NormalizedReading.HeartRate -> latestHeartRate = reading
                is NormalizedReading.Velocity -> {
                    val heartRate = latestHeartRate ?: continue
                    val ageMillis = reading.timestampMillis - heartRate.timestampMillis
                    val speed = reading.metersPerSecond
                    val bpm = heartRate.beatsPerMinute
                    if (ageMillis !in 0..maxHeartRateAgeMillis ||
                        !speed.isFinite() || speed < 0.0 || !bpm.isFinite() || bpm <= 0.0
                    ) continue

                    val ratio = speed / bpm
                    if (ratio.isFinite()) {
                        ratios += SpeedHeartRateRatio(reading.timestampMillis, ratio, speed, bpm)
                    }
                }
                is NormalizedReading.Acceleration, is NormalizedReading.Cadence -> Unit
            }
        }

        // TODO: Once Room can supply the preceding 10 observations, calculate average speed
        // and average HR, then divide the current ratio by (average speed / average HR).
        return AnalysisResult(ratios, latestHeartRate)
    }
}

data class AnalysisResult(
    val ratios: List<SpeedHeartRateRatio>,
    val latestHeartRate: NormalizedReading.HeartRate?,
)
