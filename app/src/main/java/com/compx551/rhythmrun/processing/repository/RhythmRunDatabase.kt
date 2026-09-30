package com.compx551.rhythmrun.processing.repository

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [RunningDetailsEntity::class, RunningScoreEntity::class],
    version = 1,
)
abstract class RhythmRunDatabase : RoomDatabase() {
    abstract fun runningSessionDao(): RunningSessionDao
    abstract fun runningScoreDao(): RunningScoreDao

    companion object {
        @Volatile
        private var instance: RhythmRunDatabase? = null

        fun getInstance(context: Context): RhythmRunDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RhythmRunDatabase::class.java,
                    "rhythmrun.db",
                ).setDriver(AndroidSQLiteDriver()).build().also { instance = it }
            }
    }
}
