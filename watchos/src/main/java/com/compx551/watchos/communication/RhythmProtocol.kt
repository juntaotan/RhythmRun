package com.compx551.watchos.communication

/** Wire contract shared with the phone module. Keep both copies in sync. */
object RhythmProtocol {
    const val ROOT_PATH = "/rhythmrun/v1/"
    const val ACCEL_PATH_PREFIX = "${ROOT_PATH}accel/"
    const val HEART_RATE_PATH_PREFIX = "${ROOT_PATH}hr/"
    const val STEPS_PATH_PREFIX = "${ROOT_PATH}steps/"
    const val CADENCE_PATH_PREFIX = "${ROOT_PATH}cadence/"
    const val LOCATION_PATH_PREFIX = "${ROOT_PATH}location/"

    const val SESSION_ID = "session_id"
    const val TIMESTAMP_EPOCH_MILLIS = "timestamp_epoch_millis"
    const val SEQUENCE = "sequence"
    const val ACCEL_X = "accelerometer_x"
    const val ACCEL_Y = "accelerometer_y"
    const val ACCEL_Z = "accelerometer_z"
    const val HEART_RATE = "heart_rate_bpm"
    const val HEART_RATE_SOURCE = "heart_rate_source"
    const val STEP_COUNT = "step_count"
    const val STEP_SOURCE = "step_source"
    const val CADENCE = "steps_per_minute"
    const val CADENCE_SOURCE = "cadence_source"
    const val LATITUDE = "latitude_degrees"
    const val LONGITUDE = "longitude_degrees"
    const val LOCATION_ACCURACY = "horizontal_accuracy_metres"
    const val HAS_LOCATION_ACCURACY = "has_location_accuracy"

    fun path(prefix: String, sessionId: String, sequence: Long): String =
        "$prefix$sessionId/$sequence"
}
