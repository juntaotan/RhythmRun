package com.compx551.watchos.communication

/** Shared wire-level names for the watch and phone communication contract. */
object RhythmProtocol {
    const val ACCEL_PATH_PREFIX = "/rhythmrun/v1/accel/"
    const val GYRO_PATH_PREFIX = "/rhythmrun/v1/gyro/"
    const val HEART_RATE_PATH_PREFIX = "/rhythmrun/v1/hr/"
    const val STEPS_PATH_PREFIX = "/rhythmrun/v1/steps/"
    const val CADENCE_PATH_PREFIX = "/rhythmrun/v1/cadence/"
    const val LOCATION_PATH_PREFIX = "/rhythmrun/v1/location/"
    const val SESSION_START_PATH = "/rhythmrun/v1/session/start"
    const val SESSION_PAUSE_PATH = "/rhythmrun/v1/session/pause"
    const val SESSION_RESUME_PATH = "/rhythmrun/v1/session/resume"
    const val SESSION_STOP_PATH = "/rhythmrun/v1/session/stop"

    const val SESSION_ID = "session_id"
    const val TIMESTAMP = "timestamp"
    const val SEQUENCE = "sequence"
    const val ACCEL_X = "accelerometer_x"
    const val ACCEL_Y = "accelerometer_y"
    const val ACCEL_Z = "accelerometer_z"
    const val GYRO_X = "gyroscope_x"
    const val GYRO_Y = "gyroscope_y"
    const val GYRO_Z = "gyroscope_z"
    const val HEART_RATE = "heart_rate_bpm"
    const val HEART_RATE_AVAILABLE = "heart_rate_available"
    const val STEP_COUNT = "step_count"
    const val STEP_SOURCE = "step_source"
    const val CADENCE = "steps_per_minute"
    const val CADENCE_SOURCE = "cadence_source"
    const val CADENCE_CONFIDENCE = "cadence_confidence"

    fun path(prefix: String, sessionId: String, batchIndex: Long): String =
        "$prefix$sessionId/$batchIndex"
}
