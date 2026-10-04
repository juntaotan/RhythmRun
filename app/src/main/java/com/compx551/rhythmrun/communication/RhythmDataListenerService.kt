package com.compx551.rhythmrun.communication

import android.content.Intent
import android.util.Log
import com.compx551.rhythmrun.data.local.PhoneRoomDatabase
import com.compx551.rhythmrun.data.repository.RoomRunRepository
import com.compx551.rhythmrun.domain.model.LocationFixRecord
import com.compx551.rhythmrun.domain.model.RawSensorRecord
import com.compx551.rhythmrun.domain.model.RunDataBatch
import com.compx551.rhythmrun.domain.model.StoredSensorType
import com.compx551.rhythmrun.location.PhoneLocationService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives, validates and exposes persisted watch readings while the phone UI is closed. */
class RhythmDataListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy {
        RoomRunRepository(PhoneRoomDatabase.getInstance(applicationContext))
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .forEach { event ->
                val dataType = RhythmProtocol.dataType(event.dataItem.uri.path) ?: return@forEach
                val reading = decode(dataType, DataMapItem.fromDataItem(event.dataItem).dataMap)
                    ?: return@forEach
                processReading(reading)
            }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val pathParts = event.path.split('/')
        if (pathParts.size < 6) return
        val dataType = pathParts[3]
        val sessionId = pathParts[4]
        val sequence = pathParts[5].toLongOrNull() ?: return

        val payloadStr = String(event.data, Charsets.UTF_8)
        val parts = payloadStr.split(';')
        if (parts.size < 2) return
        val timestamp = parts[0].toLongOrNull() ?: return
        val params = parts[1].split(',')

        val reading = when (dataType) {
            "accel" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                accelerationX = params.getOrNull(0)?.toFloatOrNull(),
                accelerationY = params.getOrNull(1)?.toFloatOrNull(),
                accelerationZ = params.getOrNull(2)?.toFloatOrNull(),
            )
            "gyro" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                gyroscopeX = params.getOrNull(0)?.toFloatOrNull(),
                gyroscopeY = params.getOrNull(1)?.toFloatOrNull(),
                gyroscopeZ = params.getOrNull(2)?.toFloatOrNull(),
            )
            "hr" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                heartRateBpm = params.getOrNull(0)?.toFloatOrNull(),
                heartRateAvailable = params.getOrNull(1)?.toBooleanStrictOrNull() ?: true,
                heartRateSource = params.getOrNull(2),
            )
            "steps" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                stepCount = params.getOrNull(0)?.toLongOrNull(),
                stepSource = params.getOrNull(1),
            )
            "cadence" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                cadenceStepsPerMinute = params.getOrNull(0)?.toFloatOrNull(),
                cadenceSource = params.getOrNull(1),
                cadenceConfidence = params.getOrNull(2)?.toFloatOrNull(),
            )
            "location" -> RhythmReading(
                dataType = dataType,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                latitude = params.getOrNull(0)?.toDoubleOrNull(),
                longitude = params.getOrNull(1)?.toDoubleOrNull(),
                accuracyMeters = params.getOrNull(2)?.toDoubleOrNull(),
            )
            else -> return
        }

        if (isValid(reading)) {
            processReading(reading)
        }
    }

    private fun processReading(reading: RhythmReading) {
        if (reading.dataType == "location" &&
            PhoneLocationService.isPhoneRouteSession(this, reading.sessionId)
        ) return
        val recordKey = "${reading.sessionId}:${reading.dataType}:${reading.sequence}"
        if (!remember(recordKey)) return

        serviceScope.launch {
            repository.persistBatch(reading.toRunDataBatch())
        }

        readingListener?.invoke(reading)
        sendBroadcast(reading.toIntent().setPackage(packageName))
    }

    private fun RhythmReading.toRunDataBatch(): RunDataBatch = when (dataType) {
        "accel" -> RunDataBatch(
            rawReadings = listOf(
                RawSensorRecord(
                    sessionId = sessionId,
                    sensorType = StoredSensorType.Accelerometer,
                    sequence = sequence,
                    timestampEpochMillis = timestamp,
                    x = accelerationX?.toDouble(),
                    y = accelerationY?.toDouble(),
                    z = accelerationZ?.toDouble(),
                    unit = "m/s^2",
                    available = true,
                )
            )
        )
        "gyro" -> RunDataBatch(
            rawReadings = listOf(
                RawSensorRecord(
                    sessionId = sessionId,
                    sensorType = StoredSensorType.Gyroscope,
                    sequence = sequence,
                    timestampEpochMillis = timestamp,
                    x = gyroscopeX?.toDouble(),
                    y = gyroscopeY?.toDouble(),
                    z = gyroscopeZ?.toDouble(),
                    unit = "rad/s",
                    available = true,
                )
            )
        )
        "hr" -> RunDataBatch(
            rawReadings = listOf(
                RawSensorRecord(
                    sessionId = sessionId,
                    sensorType = StoredSensorType.HeartRate,
                    sequence = sequence,
                    timestampEpochMillis = timestamp,
                    scalarValue = heartRateBpm?.toDouble(),
                    unit = "bpm",
                    source = heartRateSource,
                    available = heartRateAvailable,
                )
            )
        )
        "steps" -> RunDataBatch(
            rawReadings = listOf(
                RawSensorRecord(
                    sessionId = sessionId,
                    sensorType = StoredSensorType.StepCount,
                    sequence = sequence,
                    timestampEpochMillis = timestamp,
                    scalarValue = stepCount?.toDouble(),
                    unit = "steps",
                    source = stepSource,
                    available = true,
                )
            )
        )
        "cadence" -> RunDataBatch(
            rawReadings = listOf(
                RawSensorRecord(
                    sessionId = sessionId,
                    sensorType = StoredSensorType.StepCadence,
                    sequence = sequence,
                    timestampEpochMillis = timestamp,
                    scalarValue = cadenceStepsPerMinute?.toDouble(),
                    unit = "spm",
                    source = cadenceSource,
                    available = true,
                )
            )
        )
        "location" -> RunDataBatch(
            locationFixes = listOf(
                LocationFixRecord(
                    sessionId = sessionId,
                    sequence = sequence,
                    timestampEpochMillis = timestamp,
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMetres = accuracyMeters,
                    available = true,
                )
            )
        )
        else -> RunDataBatch()
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

    private fun RhythmReading.toIntent(): Intent =
        Intent(ACTION_SAMPLE_RECEIVED).apply {
            putExtra(EXTRA_DATA_TYPE, dataType)
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_SEQUENCE, sequence)
            putExtra(EXTRA_TIMESTAMP, timestamp)
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
        @Volatile
        var readingListener: ((RhythmReading) -> Unit)? = null

        const val ACTION_SAMPLE_RECEIVED = "com.compx551.rhythmrun.SAMPLE_RECEIVED"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_DATA_TYPE = "data_type"
        const val EXTRA_SEQUENCE = "sequence"
        const val EXTRA_TIMESTAMP = "timestamp"
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
