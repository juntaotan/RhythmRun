package com.compx551.rhythmrun.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        RunSessionEntity::class,
        RunStageEntity::class,
        RunSummaryEntity::class,
        RawSensorReadingEntity::class,
        LocationFixEntity::class,
        StageChangeEntity::class,
        CueEventEntity::class,
        AnalysisWindowEntity::class,
        IncompleteDataMessageEntity::class,
    ],
    version = 1,
)
abstract class PhoneRoomDatabase : RoomDatabase() {
    abstract fun phoneRunDao(): PhoneRunDao

    companion object {
        @Volatile
        private var instance: PhoneRoomDatabase? = null

        fun getInstance(context: Context): PhoneRoomDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhoneRoomDatabase::class.java,
                    "rhythmrun-phone.db",
                ).setDriver(AndroidSQLiteDriver()).build().also { instance = it }
            }
    }
}
