package com.compx551.rhythmrun.ui.history

data class CalendarDayUiModel(
    val dayOfMonth: Int?,
    val sessionCount: Int = 0,
    val isSelected: Boolean = false,
)

data class HistorySessionUiModel(
    val sessionId: String,
    val dateLabel: String,
    val timeLabel: String,
    val title: String,
    val durationSeconds: Long,
    val distanceMetres: Double?,
    val averageCadenceSpm: Int?,
    val averageHeartRateBpm: Int?,
    val targetAdherencePercent: Int?,
    val dataCoveragePercent: Int?,
    val completed: Boolean,
)

data class CadenceTrendPointUiModel(
    val label: String,
    val cadenceSpm: Int?,
    val rhythmStabilityPercent: Int? = null,
)

data class HistoryUiState(
    val monthLabel: String,
    val calendarDays: List<CalendarDayUiModel>,
    val weeklyDurationMinutes: Int,
    val weeklyDistanceKilometres: Double,
    val cadenceTrend: List<CadenceTrendPointUiModel>,
    val sessions: List<HistorySessionUiModel>,
)
