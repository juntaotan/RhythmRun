package com.compx551.rhythmrun.communication

/** A validated record received from one versioned Data Layer path. */
data class RhythmReading(
    val dataType: String,
    val sessionId: String,
    val sequence: Long,
    val timestamp: Long,
    val accelerationX: Float? = null,
    val accelerationY: Float? = null,
    val accelerationZ: Float? = null,
    val gyroscopeX: Float? = null,
    val gyroscopeY: Float? = null,
    val gyroscopeZ: Float? = null,
    val heartRateBpm: Float? = null,
    val heartRateAvailable: Boolean = false,
    val heartRateSource: String? = null,
    val stepCount: Long? = null,
    val stepSource: String? = null,
    val cadenceStepsPerMinute: Float? = null,
    val cadenceSource: String? = null,
    val cadenceConfidence: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
)
