package com.compx551.rhythmrun.processing.repository

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "running_sessions", indices = [Index(value = ["startTime"])])
data class RunningDetailsEntity(
    @PrimaryKey
    val sessionId: String,
    val timestamp: Long,

    val runingId: String,

    val startTime: Long,
    val endTime: Long,

    val averageHeartRate: Double,
    val averageAccelerate: Double,
    val averageVelocity: Double,
    val averageCadence: Double,
    /** Null for indoor runs or when location was unavailable. */
    val latitude: Double? = null,
    val longitude: Double? = null,

)
