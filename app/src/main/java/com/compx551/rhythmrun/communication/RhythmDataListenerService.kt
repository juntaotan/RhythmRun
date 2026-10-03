package com.compx551.rhythmrun.communication

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives Data Layer records even when no phone Activity is running. */
class RhythmDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .forEach { event ->
                val type = RhythmProtocol.dataType(event.dataItem.uri.path) ?: return@forEach
                val reading = decode(type, DataMapItem.fromDataItem(event.dataItem).dataMap)
                    ?: return@forEach
                val key = "${reading.sessionId}:$type:${reading.sequence}"
                if (!remember(key)) return@forEach
                sendBroadcast(reading.toIntent().setPackage(packageName))
            }
    }

    private fun decode(type: RhythmDataType, map: DataMap): RhythmReading? {
        val sessionId = map.getString(RhythmProtocol.SESSION_ID)?.takeIf { it.isNotBlank() }
            ?: return invalid("Missing session ID")
        val sequence = map.getLong(RhythmProtocol.SEQUENCE)
        val timestamp = map.getLong(RhythmProtocol.TIMESTAMP_EPOCH_MILLIS)
        if (sequence < 0 || timestamp <= 0) return invalid("Invalid sequence or timestamp")

        return when (type) {
            RhythmDataType.ACCELERATION -> RhythmReading.Acceleration(
                sessionId,
                sequence,
                timestamp,
                map.getFloat(RhythmProtocol.ACCEL_X),
                map.getFloat(RhythmProtocol.ACCEL_Y),
                map.getFloat(RhythmProtocol.ACCEL_Z),
            ).takeIf {
                it.xMetersPerSecondSquared.isFinite() &&
                    it.yMetersPerSecondSquared.isFinite() &&
                    it.zMetersPerSecondSquared.isFinite()
            }

            RhythmDataType.HEART_RATE -> RhythmReading.HeartRate(
                sessionId,
                sequence,
                timestamp,
                map.getFloat(RhythmProtocol.HEART_RATE),
                map.getString(RhythmProtocol.HEART_RATE_SOURCE).orEmpty(),
            ).takeIf { it.beatsPerMinute.isFinite() && it.beatsPerMinute > 0f }

            RhythmDataType.STEPS -> RhythmReading.Steps(
                sessionId,
                sequence,
                timestamp,
                map.getLong(RhythmProtocol.STEP_COUNT),
                map.getString(RhythmProtocol.STEP_SOURCE).orEmpty(),
            ).takeIf { it.cumulativeSteps >= 0 }

            RhythmDataType.CADENCE -> RhythmReading.Cadence(
                sessionId,
                sequence,
                timestamp,
                map.getFloat(RhythmProtocol.CADENCE),
                map.getString(RhythmProtocol.CADENCE_SOURCE).orEmpty(),
            ).takeIf { it.stepsPerMinute.isFinite() && it.stepsPerMinute >= 0f }

            RhythmDataType.LOCATION -> RhythmReading.Location(
                sessionId,
                sequence,
                timestamp,
                map.getDouble(RhythmProtocol.LATITUDE),
                map.getDouble(RhythmProtocol.LONGITUDE),
                map.getFloat(RhythmProtocol.LOCATION_ACCURACY)
                    .takeIf { map.getBoolean(RhythmProtocol.HAS_LOCATION_ACCURACY) },
            ).takeIf {
                it.latitudeDegrees in -90.0..90.0 &&
                    it.longitudeDegrees in -180.0..180.0 &&
                    it.horizontalAccuracyMetres?.let { accuracy -> accuracy >= 0f } != false
            }
        }
    }

    private fun remember(key: String): Boolean = synchronized(seenRecords) {
        if (!seenRecords.add(key)) return@synchronized false
        while (seenRecords.size > MAX_RECENT_KEYS) {
            seenRecords.remove(seenRecords.first())
        }
        true
    }

    private fun RhythmReading.toIntent(): Intent =
        Intent(ACTION_READING_RECEIVED)
            .putExtra(EXTRA_READING, this::class.java.simpleName)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_SEQUENCE, sequence)
            .putExtra(EXTRA_TIMESTAMP, timestampEpochMillis)
            .also { intent ->
                when (this) {
                    is RhythmReading.Acceleration -> intent
                        .putExtra(EXTRA_X, xMetersPerSecondSquared)
                        .putExtra(EXTRA_Y, yMetersPerSecondSquared)
                        .putExtra(EXTRA_Z, zMetersPerSecondSquared)
                    is RhythmReading.HeartRate -> intent
                        .putExtra(EXTRA_VALUE, beatsPerMinute)
                        .putExtra(EXTRA_SOURCE, source)
                    is RhythmReading.Steps -> intent
                        .putExtra(EXTRA_COUNT, cumulativeSteps)
                        .putExtra(EXTRA_SOURCE, source)
                    is RhythmReading.Cadence -> intent
                        .putExtra(EXTRA_VALUE, stepsPerMinute)
                        .putExtra(EXTRA_SOURCE, source)
                    is RhythmReading.Location -> intent
                        .putExtra(EXTRA_LATITUDE, latitudeDegrees)
                        .putExtra(EXTRA_LONGITUDE, longitudeDegrees)
                        .apply {
                            horizontalAccuracyMetres?.let { putExtra(EXTRA_ACCURACY, it) }
                        }
                }
            }

    private fun invalid(reason: String): Nothing? {
        Log.w(TAG, "Ignoring Data Layer record: $reason")
        return null
    }

    companion object {
        const val ACTION_READING_RECEIVED = "com.compx551.rhythmrun.READING_RECEIVED"
        const val EXTRA_READING = "reading_type"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SEQUENCE = "sequence"
        const val EXTRA_TIMESTAMP = "timestamp_epoch_millis"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_Z = "z"
        const val EXTRA_VALUE = "value"
        const val EXTRA_COUNT = "count"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"

        private const val TAG = "RhythmDataListener"
        private const val MAX_RECENT_KEYS = 10_000
        private val seenRecords = LinkedHashSet<String>()
    }
}
