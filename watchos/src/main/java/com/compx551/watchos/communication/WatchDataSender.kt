package com.compx551.watchos.communication

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Serialises typed readings and stores them in the Wear OS Data Layer. */
class WatchDataSender(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun sendAcceleration(sessionId: String, sequence: Long, batchIndex: Long = sequence,
        timestamp: Long, x: Float, y: Float, z: Float) {
        put(RhythmProtocol.ACCEL_PATH_PREFIX, sessionId, batchIndex) {
            putCommon(sessionId, sequence, timestamp)
            putFloat(RhythmProtocol.ACCEL_X, x); putFloat(RhythmProtocol.ACCEL_Y, y); putFloat(RhythmProtocol.ACCEL_Z, z)
        }
    }

    fun sendGyroscope(sessionId: String, sequence: Long, batchIndex: Long = sequence,
        timestamp: Long, x: Float, y: Float, z: Float) {
        put(RhythmProtocol.GYRO_PATH_PREFIX, sessionId, batchIndex) {
            putCommon(sessionId, sequence, timestamp)
            putFloat(RhythmProtocol.GYRO_X, x); putFloat(RhythmProtocol.GYRO_Y, y); putFloat(RhythmProtocol.GYRO_Z, z)
        }
    }

    fun sendHeartRate(sessionId: String, sequence: Long, batchIndex: Long = sequence,
        timestamp: Long, bpm: Float?, available: Boolean = bpm != null) {
        put(RhythmProtocol.HEART_RATE_PATH_PREFIX, sessionId, batchIndex) {
            putCommon(sessionId, sequence, timestamp)
            putBoolean(RhythmProtocol.HEART_RATE_AVAILABLE, available)
            putFloat(RhythmProtocol.HEART_RATE, bpm ?: Float.NaN)
        }
    }

    fun sendSteps(sessionId: String, sequence: Long, batchIndex: Long = sequence,
        timestamp: Long, count: Long, source: String) {
        put(RhythmProtocol.STEPS_PATH_PREFIX, sessionId, batchIndex) {
            putCommon(sessionId, sequence, timestamp)
            putLong(RhythmProtocol.STEP_COUNT, count); putString(RhythmProtocol.STEP_SOURCE, source)
        }
    }

    fun sendCadence(sessionId: String, sequence: Long, batchIndex: Long = sequence,
        timestamp: Long, stepsPerMinute: Float, source: String, confidence: Float) {
        put(RhythmProtocol.CADENCE_PATH_PREFIX, sessionId, batchIndex) {
            putCommon(sessionId, sequence, timestamp)
            putFloat(RhythmProtocol.CADENCE, stepsPerMinute)
            putString(RhythmProtocol.CADENCE_SOURCE, source)
            putFloat(RhythmProtocol.CADENCE_CONFIDENCE, confidence.coerceIn(0f, 1f))
        }
    }

    fun sendLocation(sessionId: String, sequence: Long, batchIndex: Long = sequence,
        timestamp: Long, latitude: Double, longitude: Double, accuracy: Double?) {
        put(RhythmProtocol.LOCATION_PATH_PREFIX, sessionId, batchIndex) {
            putCommon(sessionId, sequence, timestamp)
            putDouble(RhythmProtocol.LATITUDE, latitude)
            putDouble(RhythmProtocol.LONGITUDE, longitude)
            accuracy?.let { putDouble(RhythmProtocol.ACCURACY, it) }
        }
    }

    /** Compatibility helper: emits the three sensor types as independent records. */
    fun sendSample(
        sessionId: String,
        sequence: Long,
        batchIndex: Long = sequence,
        timestamp: Long,
        acceleration: FloatArray,
        gyroscope: FloatArray,
        heartRateBpm: Float?,
    ) {
        require(acceleration.size >= 3) { "Acceleration must contain x, y and z" }
        require(gyroscope.size >= 3) { "Gyroscope must contain x, y and z" }

        sendAcceleration(sessionId, sequence, batchIndex, timestamp, acceleration[0], acceleration[1], acceleration[2])
        sendGyroscope(sessionId, sequence, batchIndex, timestamp, gyroscope[0], gyroscope[1], gyroscope[2])
        sendHeartRate(sessionId, sequence, batchIndex, timestamp, heartRateBpm)
    }

    private fun put(prefix: String, sessionId: String, batchIndex: Long,
        fill: com.google.android.gms.wearable.DataMap.() -> Unit) {
        val request = PutDataMapRequest.create(RhythmProtocol.path(prefix, sessionId, batchIndex))
        request.dataMap.fill()
        dataClient.putDataItem(request.asPutDataRequest())
    }

    private fun com.google.android.gms.wearable.DataMap.putCommon(
        sessionId: String, sequence: Long, timestamp: Long
    ) {
        putString(RhythmProtocol.SESSION_ID, sessionId)
        putLong(RhythmProtocol.SEQUENCE, sequence)
        putLong(RhythmProtocol.TIMESTAMP, timestamp)
    }
}
