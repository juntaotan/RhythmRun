package com.compx551.rhythmrun.communication

data class RhythmSample(
    val sessionId: String,
    val sequence: Long,
    val timestamp: Long,
    val accelerationX: Float,
    val accelerationY: Float,
    val accelerationZ: Float,
    val gyroscopeX: Float,
    val gyroscopeY: Float,
    val gyroscopeZ: Float,
    val heartRateBpm: Float?,
)
