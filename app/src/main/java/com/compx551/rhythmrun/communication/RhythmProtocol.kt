package com.compx551.rhythmrun.communication

/** Must stay in sync with the watch module's wire-level names. */
object RhythmProtocol {
    const val ROOT_PATH = "/rhythmrun/v1/"
    const val ACCEL_PATH_PREFIX = "${ROOT_PATH}accel/"
    const val GYRO_PATH_PREFIX = "${ROOT_PATH}gyro/"
    const val HEART_RATE_PATH_PREFIX = "${ROOT_PATH}hr/"
    const val STEPS_PATH_PREFIX = "${ROOT_PATH}steps/"
    const val CADENCE_PATH_PREFIX = "${ROOT_PATH}cadence/"
    const val LOCATION_PATH_PREFIX = "${ROOT_PATH}location/"
    const val SESSION_START_PATH = "${ROOT_PATH}session/start"
    const val SESSION_PAUSE_PATH = "${ROOT_PATH}session/pause"
    const val SESSION_RESUME_PATH = "${ROOT_PATH}session/resume"
    const val SESSION_STOP_PATH = "${ROOT_PATH}session/stop"

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
    const val HEART_RATE_SOURCE = "heart_rate_source"
    const val STEP_COUNT = "step_count"
    const val STEP_SOURCE = "step_source"
    const val CADENCE = "steps_per_minute"
    const val CADENCE_SOURCE = "cadence_source"
    const val CADENCE_CONFIDENCE = "cadence_confidence"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val ACCURACY = "accuracy"

    fun dataType(path: String?): String? = when {
        path?.startsWith(ACCEL_PATH_PREFIX) == true -> "accel"
        path?.startsWith(GYRO_PATH_PREFIX) == true -> "gyro"
        path?.startsWith(HEART_RATE_PATH_PREFIX) == true -> "hr"
        path?.startsWith(STEPS_PATH_PREFIX) == true -> "steps"
        path?.startsWith(CADENCE_PATH_PREFIX) == true -> "cadence"
        path?.startsWith(LOCATION_PATH_PREFIX) == true -> "location"
        else -> null
    }
}
