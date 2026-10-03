package com.compx551.rhythmrun.communication

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives, validates and exposes persisted watch readings while the phone UI is closed. */
class RhythmDataListenerService : WearableListenerService() {
    private val lastSequenceByType = mutableMapOf<String, Long>()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .forEach { event ->
                val dataType = RhythmProtocol.dataType(event.dataItem.uri.path) ?: return@forEach
                val reading = decode(dataType, DataMapItem.fromDataItem(event.dataItem).dataMap)
                    ?: return@forEach
                val recordKey = "${reading.sessionId}:${reading.dataType}:${reading.sequence}"
                if (!remember(recordKey)) return@forEach

                val previousSequence = lastSequenceByType[reading.dataType]
                val missingFrom = previousSequence?.plus(1)?.takeIf { reading.sequence > it }
                val missingTo = missingFrom?.let { reading.sequence - 1 }
                lastSequenceByType[reading.dataType] = maxOf(
                    reading.sequence,
                    previousSequence ?: reading.sequence,
                )

                readingListener?.invoke(reading)
                sendBroadcast(reading.toIntent(missingFrom, missingTo).setPackage(packageName))
            }
    }

    private fun decode(dataType: String, map: DataMap): RhythmReading? {
        val sessionId = map.getString(RhythmProtocol.SESSION_ID)?.takeIf { it.isNotBlank() }
            ?: return invalid("missing session ID")
        val sequence = map.getLong(RhythmProtocol.SEQUENCE)
        val timestamp = map.getLong(RhythmProtocol.TIMESTAMP)
        if (sequence < 0 || timestamp <= 0) return invalid("invalid sequence or timestamp")

        val reading = when (dataType) {
            "accel" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                accelerationX = map.getFloat(RhythmProtocol.ACCEL_X),
                accelerationY = map.getFloat(RhythmProtocol.ACCEL_Y),
                accelerationZ = map.getFloat(RhythmProtocol.ACCEL_Z),
            )
            "gyro" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                gyroscopeX = map.getFloat(RhythmProtocol.GYRO_X),
                gyroscopeY = map.getFloat(RhythmProtocol.GYRO_Y),
                gyroscopeZ = map.getFloat(RhythmProtocol.GYRO_Z),
            )
            "hr" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                heartRateBpm = map.getFloat(RhythmProtocol.HEART_RATE),
                heartRateAvailable = map.getBoolean(RhythmProtocol.HEART_RATE_AVAILABLE),
                heartRateSource = map.getString(RhythmProtocol.HEART_RATE_SOURCE),
            )
            "steps" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                stepCount = map.getLong(RhythmProtocol.STEP_COUNT),
                stepSource = map.getString(RhythmProtocol.STEP_SOURCE),
            )
            "cadence" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                cadenceStepsPerMinute = map.getFloat(RhythmProtocol.CADENCE),
                cadenceSource = map.getString(RhythmProtocol.CADENCE_SOURCE),
                cadenceConfidence = map.getFloat(RhythmProtocol.CADENCE_CONFIDENCE),
            )
            "location" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                latitude = map.getDouble(RhythmProtocol.LATITUDE),
                longitude = map.getDouble(RhythmProtocol.LONGITUDE),
                accuracyMeters = map.getDouble(RhythmProtocol.ACCURACY)
                    .takeIf { map.containsKey(RhythmProtocol.ACCURACY) },
            )
            else -> return null
        }
        return reading.takeIf(::isValid) ?: invalid("invalid $dataType values")
    }

    private fun isValid(reading: RhythmReading): Boolean = when (reading.dataType) {
        "accel" -> listOf(reading.accelerationX, reading.accelerationY, reading.accelerationZ)
            .all { it?.isFinite() == true }
        "gyro" -> listOf(reading.gyroscopeX, reading.gyroscopeY, reading.gyroscopeZ)
            .all { it?.isFinite() == true }
        "hr" -> reading.heartRateBpm?.let { it.isFinite() && it > 0f } == true
        "steps" -> reading.stepCount?.let { it >= 0 } == true
        "cadence" -> reading.cadenceStepsPerMinute?.let { it.isFinite() && it >= 0f } == true
        "location" -> {
            val latitude = reading.latitude
            val longitude = reading.longitude
            latitude != null &&
                longitude != null &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                reading.accuracyMeters?.let { it >= 0.0 } != false
        }
        else -> false
    }

    private fun remember(key: String): Boolean = synchronized(seenRecords) {
        if (!seenRecords.add(key)) return@synchronized false
        while (seenRecords.size > MAX_RECENT_KEYS) seenRecords.remove(seenRecords.first())
        true
    }

    private fun RhythmReading.toIntent(missingFrom: Long?, missingTo: Long?): Intent =
        Intent(ACTION_SAMPLE_RECEIVED).apply {
            putExtra(EXTRA_DATA_TYPE, dataType)
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_SEQUENCE, sequence)
            putExtra(EXTRA_TIMESTAMP, timestamp)
            missingFrom?.let { putExtra(EXTRA_MISSING_FROM, it) }
            missingTo?.let { putExtra(EXTRA_MISSING_TO, it) }
            accelerationX?.let { putExtra(EXTRA_ACCEL_X, it) }
            accelerationY?.let { putExtra(EXTRA_ACCEL_Y, it) }
            accelerationZ?.let { putExtra(EXTRA_ACCEL_Z, it) }
            gyroscopeX?.let { putExtra(EXTRA_GYRO_X, it) }
            gyroscopeY?.let { putExtra(EXTRA_GYRO_Y, it) }
            gyroscopeZ?.let { putExtra(EXTRA_GYRO_Z, it) }
            heartRateBpm?.let { putExtra(EXTRA_HEART_RATE, it) }
            putExtra(EXTRA_HEART_RATE_AVAILABLE, heartRateAvailable)
            stepCount?.let { putExtra(EXTRA_STEP_COUNT, it) }
            stepSource?.let { putExtra(EXTRA_STEP_SOURCE, it) }
            cadenceStepsPerMinute?.let { putExtra(EXTRA_CADENCE, it) }
            cadenceSource?.let { putExtra(EXTRA_CADENCE_SOURCE, it) }
            cadenceConfidence?.let { putExtra(EXTRA_CADENCE_CONFIDENCE, it) }
            latitude?.let { putExtra(EXTRA_LATITUDE, it) }
            longitude?.let { putExtra(EXTRA_LONGITUDE, it) }
            accuracyMeters?.let { putExtra(EXTRA_ACCURACY, it) }
        }

    private fun invalid(reason: String): Nothing? {
        Log.w(TAG, "Ignoring Data Layer record: $reason")
        return null
    }

    companion object {
        var readingListener: ((RhythmReading) -> Unit)? = null

        const val ACTION_SAMPLE_RECEIVED = "com.compx551.rhythmrun.SAMPLE_RECEIVED"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_DATA_TYPE = "data_type"
        const val EXTRA_SEQUENCE = "sequence"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_MISSING_FROM = "missing_sequence_from"
        const val EXTRA_MISSING_TO = "missing_sequence_to"
        const val EXTRA_ACCEL_X = "accelerometer_x"
        const val EXTRA_ACCEL_Y = "accelerometer_y"
        const val EXTRA_ACCEL_Z = "accelerometer_z"
        const val EXTRA_GYRO_X = "gyroscope_x"
        const val EXTRA_GYRO_Y = "gyroscope_y"
        const val EXTRA_GYRO_Z = "gyroscope_z"
        const val EXTRA_HEART_RATE = "heart_rate_bpm"
        const val EXTRA_HEART_RATE_AVAILABLE = "heart_rate_available"
        const val EXTRA_STEP_COUNT = "step_count"
        const val EXTRA_STEP_SOURCE = "step_source"
        const val EXTRA_CADENCE = "steps_per_minute"
        const val EXTRA_CADENCE_SOURCE = "cadence_source"
        const val EXTRA_CADENCE_CONFIDENCE = "cadence_confidence"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"

        private const val TAG = "RhythmDataListener"
        private const val MAX_RECENT_KEYS = 10_000
        private val seenRecords = LinkedHashSet<String>()
    }
}
