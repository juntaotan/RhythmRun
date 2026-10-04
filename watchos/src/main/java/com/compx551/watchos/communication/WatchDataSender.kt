package com.compx551.watchos.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Transmits sensor readings using dual-channel (fixed-path DataClient + MessageClient) for maximum reliability. */
class WatchDataSender(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)

    fun sendAcceleration(
        sessionId: String,
        sequence: Long,
        timestampEpochMillis: Long,
        x: Float,
        y: Float,
        z: Float,
    ) = send(RhythmProtocol.ACCEL_PATH_PREFIX, sessionId, sequence, timestampEpochMillis, "$x,$y,$z") {
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
    ) = send(RhythmProtocol.GYRO_PATH_PREFIX, sessionId, sequence, timestampEpochMillis, "$x,$y,$z") {
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
    ) = send(RhythmProtocol.HEART_RATE_PATH_PREFIX, sessionId, sequence, timestampEpochMillis, "$beatsPerMinute,true,$source") {
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
    ) = send(RhythmProtocol.STEPS_PATH_PREFIX, sessionId, sequence, timestampEpochMillis, "$cumulativeSteps,$source") {
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
    ) = send(RhythmProtocol.CADENCE_PATH_PREFIX, sessionId, sequence, timestampEpochMillis, "$stepsPerMinute,$source,1.0") {
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
    ) = send(RhythmProtocol.LOCATION_PATH_PREFIX, sessionId, sequence, timestampEpochMillis, "$latitudeDegrees,$longitudeDegrees,${horizontalAccuracyMetres ?: 0.0}") {
        putCommon(sessionId, sequence, timestampEpochMillis)
        putDouble(RhythmProtocol.LATITUDE, latitudeDegrees)
        putDouble(RhythmProtocol.LONGITUDE, longitudeDegrees)
        horizontalAccuracyMetres?.let { putDouble(RhythmProtocol.ACCURACY, it) }
    }

    private fun send(
        prefix: String,
        sessionId: String,
        sequence: Long,
        timestamp: Long,
        payloadStr: String,
        fillMap: DataMap.() -> Unit,
    ) {
        // 1. Channel A: Unique sequence path DataClient item for reliable offline buffering and auto-sync
        val dataPath = RhythmProtocol.path(prefix, sessionId, sequence)
        val request = PutDataMapRequest.create(dataPath)
        request.dataMap.fillMap()
        dataClient.putDataItem(request.asPutDataRequest().setUrgent())
            .addOnFailureListener { Log.e(TAG, "DataClient error for $dataPath", it) }

        // 2. Channel B: MessageClient direct message for low-latency live streaming when nodes are connected
        val bytes = "$timestamp;$payloadStr".toByteArray(Charsets.UTF_8)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, dataPath, bytes)
            }
        }
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
