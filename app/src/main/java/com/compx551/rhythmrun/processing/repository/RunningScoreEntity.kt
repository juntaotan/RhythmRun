package com.compx551.rhythmrun.processing.repository

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "running_Score")
data class RunningScoreEntity (
    @PrimaryKey
    val sessionId: String,
    val timestamp: Long,

    val relativeEfficiency: Long,
)