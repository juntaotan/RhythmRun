package com.compx551.watchos.storage

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.compx551.watchos.sensors.AccelerationReading
import com.compx551.watchos.sensors.HeartRateSource
import com.compx551.watchos.sensors.LocationReading
import java.util.UUID
import java.util.concurrent.Executors

/** The lifecycle of sensor data kept temporarily on the watch. */
enum class TemporarySessionState {
    ACTIVE,
    WAITING_FOR_SYNC,
    INTERRUPTED,
}

@Entity(tableName = "temporary_sessions")
data class TemporarySessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val state: String = TemporarySessionState.ACTIVE.name,
)

@Entity(
    tableName = "temporary_accelerometer_samples",
    foreignKeys = [
        ForeignKey(
            entity = TemporarySessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TemporaryAccelerometerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sequence: Long,
    val timestampNanosSinceBoot: Long,
    val xMetersPerSecondSquared: Float,
    val yMetersPerSecondSquared: Float,
    val zMetersPerSecondSquared: Float,
)

@Entity(
    tableName = "temporary_heart_rate_samples",
    foreignKeys = [
        ForeignKey(
            entity = TemporarySessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TemporaryHeartRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sequence: Long,
    val timestampNanosSinceBoot: Long,
    val beatsPerMinute: Double,
    val source: String,
)

@Entity(
    tableName = "temporary_step_samples",
    foreignKeys = [
        ForeignKey(
            entity = TemporarySessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TemporaryStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sequence: Long,
    val timestampNanosSinceBoot: Long,
    val cumulativeSteps: Long,
    val cadenceStepsPerMinute: Long?,
)

@Entity(
    tableName = "temporary_location_samples",
    foreignKeys = [
        ForeignKey(
            entity = TemporarySessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class TemporaryLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sequence: Long,
    val timestampNanosSinceBoot: Long,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val horizontalAccuracyMeters: Double?,
)

@Dao
interface TemporarySessionDao {
    @Insert
    fun insertSession(session: TemporarySessionEntity)

    @Insert
    fun insertAccelerometer(sample: TemporaryAccelerometerEntity)

    @Insert
    fun insertHeartRate(sample: TemporaryHeartRateEntity)

    @Insert
    fun insertSteps(sample: TemporaryStepEntity)

    @Insert
    fun insertLocation(sample: TemporaryLocationEntity)

    @Query(
        """
        UPDATE temporary_sessions
        SET state = :interruptedState, endedAtEpochMillis = :endedAtEpochMillis
        WHERE state = :activeState
        """,
    )
    fun markActiveSessionsInterrupted(
        endedAtEpochMillis: Long,
        activeState: String = "ACTIVE",
        interruptedState: String = "INTERRUPTED",
    )

    @Query(
        """
        UPDATE temporary_sessions
        SET state = :waitingState, endedAtEpochMillis = :endedAtEpochMillis
        WHERE sessionId = :sessionId AND state = :activeState
        """,
    )
    fun markWaitingForSync(
        sessionId: String,
        endedAtEpochMillis: Long,
        activeState: String = "ACTIVE",
        waitingState: String = "WAITING_FOR_SYNC",
    )

    @Query(
        """
        UPDATE temporary_sessions
        SET state = :interruptedState, endedAtEpochMillis = :endedAtEpochMillis
        WHERE sessionId = :sessionId AND state = :activeState
        """,
    )
    fun markInterrupted(
        sessionId: String,
        endedAtEpochMillis: Long,
        activeState: String = "ACTIVE",
        interruptedState: String = "INTERRUPTED",
    )

    @Query(
        """
        SELECT * FROM temporary_sessions
        WHERE state IN ('WAITING_FOR_SYNC', 'INTERRUPTED')
        ORDER BY startedAtEpochMillis ASC
        """,
    )
    fun getSessionsPendingSync(): List<TemporarySessionEntity>

    @Query("SELECT * FROM temporary_accelerometer_samples WHERE sessionId = :sessionId ORDER BY sequence")
    fun getAccelerometerSamples(sessionId: String): List<TemporaryAccelerometerEntity>

    @Query("SELECT * FROM temporary_heart_rate_samples WHERE sessionId = :sessionId ORDER BY sequence")
    fun getHeartRateSamples(sessionId: String): List<TemporaryHeartRateEntity>

    @Query("SELECT * FROM temporary_step_samples WHERE sessionId = :sessionId ORDER BY sequence")
    fun getStepSamples(sessionId: String): List<TemporaryStepEntity>

    @Query("SELECT * FROM temporary_location_samples WHERE sessionId = :sessionId ORDER BY sequence")
    fun getLocationSamples(sessionId: String): List<TemporaryLocationEntity>

    /** Call only after the phone has acknowledged this complete session. */
    @Query("DELETE FROM temporary_sessions WHERE sessionId = :sessionId AND state != 'ACTIVE'")
    fun deleteAcknowledgedSession(sessionId: String): Int
}

@Database(
    entities = [
        TemporarySessionEntity::class,
        TemporaryAccelerometerEntity::class,
        TemporaryHeartRateEntity::class,
        TemporaryStepEntity::class,
        TemporaryLocationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class RhythmRunDatabase : RoomDatabase() {
    abstract fun temporarySessionDao(): TemporarySessionDao

    companion object {
        @Volatile private var instance: RhythmRunDatabase? = null

        fun getInstance(context: Context): RhythmRunDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RhythmRunDatabase::class.java,
                    "rhythm_run.db",
                ).build().also { instance = it }
            }
    }
}

/**
 * Asynchronous persistence boundary used by sensor capture.
 *
 * Every operation is written on one ordered executor. Therefore the session row is committed before
 * its samples, and the final state is committed only after all earlier samples have been written.
 */
class TemporarySessionStorage(
    context: Context,
) {
    private val dao = RhythmRunDatabase.getInstance(context).temporarySessionDao()

    init {
        // An ACTIVE row left by process death is retained and made eligible for later sync.
        write { dao.markActiveSessionsInterrupted(System.currentTimeMillis()) }
    }

    fun beginSession(startedAtEpochMillis: Long = System.currentTimeMillis()): String {
        val sessionId = UUID.randomUUID().toString()
        write {
            dao.insertSession(
                TemporarySessionEntity(
                    sessionId = sessionId,
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        }
        return sessionId
    }

    fun saveAccelerometer(sessionId: String, sequence: Long, reading: AccelerationReading) =
        write {
            dao.insertAccelerometer(
                TemporaryAccelerometerEntity(
                    sessionId = sessionId,
                    sequence = sequence,
                    timestampNanosSinceBoot = reading.timestampNanosSinceBoot,
                    xMetersPerSecondSquared = reading.xMetersPerSecondSquared,
                    yMetersPerSecondSquared = reading.yMetersPerSecondSquared,
                    zMetersPerSecondSquared = reading.zMetersPerSecondSquared,
                ),
            )
        }

    fun saveHeartRate(
        sessionId: String,
        sequence: Long,
        timestampNanosSinceBoot: Long,
        beatsPerMinute: Double,
        source: HeartRateSource,
    ) = write {
        dao.insertHeartRate(
            TemporaryHeartRateEntity(
                sessionId = sessionId,
                sequence = sequence,
                timestampNanosSinceBoot = timestampNanosSinceBoot,
                beatsPerMinute = beatsPerMinute,
                source = source.name,
            ),
        )
    }

    fun saveSteps(
        sessionId: String,
        sequence: Long,
        timestampNanosSinceBoot: Long,
        cumulativeSteps: Long,
        cadenceStepsPerMinute: Long?,
    ) = write {
        dao.insertSteps(
            TemporaryStepEntity(
                sessionId = sessionId,
                sequence = sequence,
                timestampNanosSinceBoot = timestampNanosSinceBoot,
                cumulativeSteps = cumulativeSteps,
                cadenceStepsPerMinute = cadenceStepsPerMinute,
            ),
        )
    }

    fun saveLocation(sessionId: String, sequence: Long, reading: LocationReading) =
        write {
            dao.insertLocation(
                TemporaryLocationEntity(
                    sessionId = sessionId,
                    sequence = sequence,
                    timestampNanosSinceBoot = reading.timestampNanosSinceBoot,
                    latitudeDegrees = reading.latitudeDegrees,
                    longitudeDegrees = reading.longitudeDegrees,
                    horizontalAccuracyMeters = reading.horizontalAccuracyMeters,
                ),
            )
        }

    fun finishSession(sessionId: String, endedAtEpochMillis: Long = System.currentTimeMillis()) =
        write { dao.markWaitingForSync(sessionId, endedAtEpochMillis) }

    fun interruptSession(sessionId: String, endedAtEpochMillis: Long = System.currentTimeMillis()) =
        write { dao.markInterrupted(sessionId, endedAtEpochMillis) }

    private fun write(operation: () -> Unit) {
        writer.execute {
            try {
                operation()
            } catch (throwable: Throwable) {
                Log.e(TAG, "Unable to persist temporary sensor data", throwable)
            }
        }
    }

    private companion object {
        const val TAG = "TemporaryStorage"

        // One process-wide queue keeps writes ordered across Activity recreation.
        val writer = Executors.newSingleThreadExecutor()
    }
}
