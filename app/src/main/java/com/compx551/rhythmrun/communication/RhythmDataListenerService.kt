package com.compx551.rhythmrun.communication

import android.content.Intent
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives persisted typed readings even when the phone dashboard is not visible. */
class RhythmDataListenerService : WearableListenerService() {
    private val seenRecords = LinkedHashSet<String>()
    private val lastSequenceByType = mutableMapOf<String, Long>()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .mapNotNull { event ->
                val dataType = RhythmProtocol.dataType(event.dataItem.uri.path) ?: return@mapNotNull null
                dataType to event
            }
            .forEach { (dataType, event) ->
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val sessionId = map.getString(RhythmProtocol.SESSION_ID) ?: return@forEach
                val sequence = map.getLong(RhythmProtocol.SEQUENCE)
                val reading = RhythmReading(
                    dataType = dataType,
                    sessionId = sessionId,
                    sequence = sequence,
                    timestamp = map.getLong(RhythmProtocol.TIMESTAMP),
                    accelerationX = map.getFloat(RhythmProtocol.ACCEL_X),
                    accelerationY = map.getFloat(RhythmProtocol.ACCEL_Y),
                    accelerationZ = map.getFloat(RhythmProtocol.ACCEL_Z),
                    gyroscopeX = map.getFloat(RhythmProtocol.GYRO_X),
                    gyroscopeY = map.getFloat(RhythmProtocol.GYRO_Y),
                    gyroscopeZ = map.getFloat(RhythmProtocol.GYRO_Z),
                    heartRateBpm = map.getFloat(RhythmProtocol.HEART_RATE),
                    heartRateAvailable = map.getBoolean(RhythmProtocol.HEART_RATE_AVAILABLE),
                    stepCount = map.getLong(RhythmProtocol.STEP_COUNT),
                    stepSource = map.getString(RhythmProtocol.STEP_SOURCE),
                    cadenceStepsPerMinute = map.getFloat(RhythmProtocol.CADENCE),
                    cadenceSource = map.getString(RhythmProtocol.CADENCE_SOURCE),
                    cadenceConfidence = map.getFloat(RhythmProtocol.CADENCE_CONFIDENCE),
                )

                val recordKey = "${reading.sessionId}:${reading.dataType}:${reading.sequence}"
                if (!seenRecords.add(recordKey)) return@forEach

                val previousSequence = lastSequenceByType[reading.dataType]
                val missingFrom = previousSequence?.plus(1)
                    ?.takeIf { reading.sequence > it }
                val missingTo = missingFrom?.let { reading.sequence - 1 }
                lastSequenceByType[reading.dataType] = maxOf(
                    reading.sequence,
                    previousSequence ?: reading.sequence,
                )

                readingListener?.invoke(reading)

                // Temporary hand-off for Member 4. Replace this broadcast with the Room repository call.
                sendBroadcast(Intent(ACTION_SAMPLE_RECEIVED).apply {
                    putExtra(EXTRA_DATA_TYPE, reading.dataType)
                    putExtra(EXTRA_SESSION_ID, reading.sessionId)
                    putExtra(EXTRA_SEQUENCE, reading.sequence)
                    putExtra(EXTRA_TIMESTAMP, reading.timestamp)
                    missingFrom?.let { putExtra(EXTRA_MISSING_FROM, it) }
                    missingTo?.let { putExtra(EXTRA_MISSING_TO, it) }
                    reading.accelerationX?.let { putExtra(EXTRA_ACCEL_X, it) }
                    reading.accelerationY?.let { putExtra(EXTRA_ACCEL_Y, it) }
                    reading.accelerationZ?.let { putExtra(EXTRA_ACCEL_Z, it) }
                    reading.gyroscopeX?.let { putExtra(EXTRA_GYRO_X, it) }
                    reading.gyroscopeY?.let { putExtra(EXTRA_GYRO_Y, it) }
                    reading.gyroscopeZ?.let { putExtra(EXTRA_GYRO_Z, it) }
                    reading.heartRateBpm?.let { putExtra(EXTRA_HEART_RATE, it) }
                    putExtra(EXTRA_HEART_RATE_AVAILABLE, reading.heartRateAvailable)
                    reading.stepCount?.let { putExtra(EXTRA_STEP_COUNT, it) }
                    reading.stepSource?.let { putExtra(EXTRA_STEP_SOURCE, it) }
                    reading.cadenceStepsPerMinute?.let { putExtra(EXTRA_CADENCE, it) }
                    reading.cadenceSource?.let { putExtra(EXTRA_CADENCE_SOURCE, it) }
                    reading.cadenceConfidence?.let { putExtra(EXTRA_CADENCE_CONFIDENCE, it) }
                })
            }
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
    }
}
