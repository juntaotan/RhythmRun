package com.compx551.watchos.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Serialises sensor readings and stores them in the persistent Wear Data Layer. */
class WatchDataSender(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun sendAcceleration(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
        x: Float,
        y: Float,
        z: Float,
    ) = put(RhythmProtocol.ACCEL_PATH_PREFIX, sessionId, sequence) {
        putCommon(sessionId, sequence, timestampEpochMillis)
        putFloat(RhythmProtocol.ACCEL_X, x)
        putFloat(RhythmProtocol.ACCEL_Y, y)
        putFloat(RhythmProtocol.ACCEL_Z, z)
    }

    fun sendGyroscope(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
        x: Float,
        y: Float,
        z: Float,
    ) = put(RhythmProtocol.GYRO_PATH_PREFIX, sessionId, sequence) {
        putCommon(sessionId, sequence, timestampEpochMillis)
        putFloat(RhythmProtocol.GYRO_X, x)
        putFloat(RhythmProtocol.GYRO_Y, y)
        putFloat(RhythmProtocol.GYRO_Z, z)
    }

    fun sendHeartRate(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
        beatsPerMinute: Double,
        source: String,
    ) = put(RhythmProtocol.HEART_RATE_PATH_PREFIX, sessionId, sequence) {
        putCommon(sessionId, sequence, timestampEpochMillis)
        putBoolean(RhythmProtocol.HEART_RATE_AVAILABLE, true)
        putFloat(RhythmProtocol.HEART_RATE, beatsPerMinute.toFloat())
        putString(RhythmProtocol.HEART_RATE_SOURCE, source)
    }

    fun sendSteps(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
        cumulativeSteps: Long,
        source: String,
    ) = put(RhythmProtocol.STEPS_PATH_PREFIX, sessionId, sequence) {
        putCommon(sessionId, sequence, timestampEpochMillis)
        putLong(RhythmProtocol.STEP_COUNT, cumulativeSteps)
        putString(RhythmProtocol.STEP_SOURCE, source)
    }

    fun sendCadence(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
        stepsPerMinute: Long,
        source: String,
    ) = put(RhythmProtocol.CADENCE_PATH_PREFIX, sessionId, sequence) {
        putCommon(sessionId, sequence, timestampEpochMillis)
        putFloat(RhythmProtocol.CADENCE, stepsPerMinute.toFloat())
        putString(RhythmProtocol.CADENCE_SOURCE, source)
        putFloat(RhythmProtocol.CADENCE_CONFIDENCE, 1f)
    }

    fun sendLocation(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
        latitudeDegrees: Double,
        longitudeDegrees: Double,
        horizontalAccuracyMetres: Double?,
    ) = put(RhythmProtocol.LOCATION_PATH_PREFIX, sessionId, sequence) {
        putCommon(sessionId, sequence, timestampEpochMillis)
        putDouble(RhythmProtocol.LATITUDE, latitudeDegrees)
        putDouble(RhythmProtocol.LONGITUDE, longitudeDegrees)
        horizontalAccuracyMetres?.let { putDouble(RhythmProtocol.ACCURACY, it) }
    }

    private fun put(
        prefix: String,
        sessionId: String,
        sequence: Long,
        fill: DataMap.() -> Unit,
    ) {
        val request = PutDataMapRequest.create(RhythmProtocol.path(prefix, sessionId, sequence))
        request.dataMap.fill()
        dataClient.putDataItem(request.asPutDataRequest())
            .addOnFailureListener { error -> Log.e(TAG, "Unable to enqueue Data Layer item", error) }
    }

    private fun DataMap.putCommon(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
    ) {
        putString(RhythmProtocol.SESSION_ID, sessionId)
        putLong(RhythmProtocol.SEQUENCE, sequence)
        putLong(RhythmProtocol.TIMESTAMP, timestampEpochMillis)
    }

    private companion object {
        const val TAG = "WatchDataSender"
    }
}
