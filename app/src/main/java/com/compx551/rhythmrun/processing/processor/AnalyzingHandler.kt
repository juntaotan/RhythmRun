package com.compx551.rhythmrun.processing.processor

/** Computes the current ratio and, when available, its historical relative-efficiency index. */
class AnalyzingHandler(private val maxHeartRateAgeMillis: Long = 10_000L) : ProcessingHandler() {
    init {
        require(maxHeartRateAgeMillis >= 0) { "Maximum HR age cannot be negative" }
    }

    /** History is newest first. Use up to ten prior runs; return null when none are usable. */
    fun calculateBaseline(historyNewestFirst: List<HistoricalRunAverage>): EfficiencyBaseline? {
        val recent = historyNewestFirst.asSequence()
            .take(10)
            .filter { it.averageSpeedMetersPerSecond.isFinite() &&
                it.averageSpeedMetersPerSecond > 0.0 &&
                it.averageHeartRateBpm.isFinite() && it.averageHeartRateBpm > 0.0 }
            .toList()
        if (recent.isEmpty()) return null

        val averageSpeed = recent.map { it.averageSpeedMetersPerSecond }.average()
        val averageHeartRate = recent.map { it.averageHeartRateBpm }.average()
        if (!averageSpeed.isFinite() || !averageHeartRate.isFinite()) return null
        return EfficiencyBaseline(averageSpeed, averageHeartRate, recent.size)
    }

    override fun process(request: ProcessingRequest): Boolean {
        val result = analyze(
            request.smoothedReadings,
            request.previousSmoothedHeartRate,
            request.efficiencyBaseline,
        )
        request.speedHeartRateRatios = result.ratios
        request.latestSmoothedHeartRate = result.latestHeartRate
        return true
    }

    fun analyze(
        readings: List<NormalizedReading>,
        previousHeartRate: NormalizedReading.HeartRate? = null,
        baseline: EfficiencyBaseline? = null,
    ): AnalysisResult {
        require(baseline == null ||
            (baseline.historyCount in 1..10 &&
                baseline.averageSpeedMetersPerSecond.isFinite() && baseline.averageSpeedMetersPerSecond > 0.0 &&
                baseline.averageHeartRateBpm.isFinite() && baseline.averageHeartRateBpm > 0.0)
        ) { "Baseline must contain 1 to 10 positive, finite historical averages" }

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
                        val index = baseline?.let { ratio / it.speedHeartRateRatio }
                        ratios += SpeedHeartRateRatio(
                            timestampMillis = reading.timestampMillis,
                            speedToHeartRateRatio = ratio,
                            speedMetersPerSecond = speed,
                            heartRateBpm = bpm,
                            relativeEfficiency = index?.takeIf(Double::isFinite),
                        )
                    }
                }
                is NormalizedReading.Acceleration, is NormalizedReading.Cadence -> Unit
            }
        }

        return AnalysisResult(ratios, latestHeartRate)
    }
}

data class AnalysisResult(
    val ratios: List<SpeedHeartRateRatio>,
    val latestHeartRate: NormalizedReading.HeartRate?,
)

data class HistoricalRunAverage(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
)
