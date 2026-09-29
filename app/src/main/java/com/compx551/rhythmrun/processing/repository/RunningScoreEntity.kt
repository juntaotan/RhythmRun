package com.compx551.rhythmrun.processing.repository

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "running_Score",
    primaryKeys = ["sessionId", "timestamp"],
    indices = [Index(value = ["timestamp"])],
)
data class RunningScoreEntity(
    val sessionId: String,
    val timestamp: Long,
    val speedHeartRateRatio: Double,
    /** Available after the historical ten-record baseline is defined and calculated. */
    val relativeEfficiency: Double? = null,
)
