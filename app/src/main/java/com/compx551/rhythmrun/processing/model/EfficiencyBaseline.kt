package com.compx551.rhythmrun.processing.model

data class EfficiencyBaseline(
    val averageSpeedMetersPerSecond: Double,
    val averageHeartRateBpm: Double,
    val historyCount: Int,
) {
    val speedHeartRateRatio: Double
        get() = averageSpeedMetersPerSecond / averageHeartRateBpm
}
