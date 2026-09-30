package com.compx551.rhythmrun.ui.mapping

import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.domain.model.RunRecord
import com.compx551.rhythmrun.ui.dashboard.DashboardUiState
import com.compx551.rhythmrun.ui.dashboard.LastRunUiModel
import com.compx551.rhythmrun.ui.dashboard.WatchConnectionStatus
import com.compx551.rhythmrun.ui.history.CadenceTrendPointUiModel
import com.compx551.rhythmrun.ui.history.CalendarDayUiModel
import com.compx551.rhythmrun.ui.history.HistorySessionUiModel
import com.compx551.rhythmrun.ui.history.HistoryUiState
import com.compx551.rhythmrun.ui.runsession.RunSummaryUiState
import com.compx551.rhythmrun.ui.runsession.StageSummaryUiState
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun RunRecord.toSummaryUiState(): RunSummaryUiState = RunSummaryUiState(
    sessionId = sessionId,
    completionStatus = completion,
    totalDurationSeconds = totalDurationSeconds,
    distanceMetres = distanceMetres,
    averageHeartRateBpm = averageHeartRateBpm,
    averageCadenceSpm = averageCadenceSpm,
    cadenceSource = cadenceSource,
    targetAdherencePercent = targetAdherencePercent,
    rhythmStabilityPercent = rhythmStabilityPercent,
    averageMovementMagnitude = averageMovementMagnitude,
    heartRateResponseBpm = heartRateResponseBpm,
    cueCoveragePercent = cueCoveragePercent,
    dataCoveragePercent = dataCoveragePercent,
    stages = stages.map { stage ->
        StageSummaryUiState(
            stage = stage.stage,
            plannedDurationSeconds = stage.plannedDurationSeconds,
            actualDurationSeconds = stage.actualDurationSeconds,
            targetCadenceSpm = stage.targetCadenceSpm,
            averageCadenceSpm = stage.averageCadenceSpm,
            averageHeartRateBpm = stage.averageHeartRateBpm,
            movementMagnitude = stage.movementMagnitude,
        )
    },
    incompleteDataMessages = incompleteDataMessages,
)

fun RunRecord.toHistorySessionUiModel(): HistorySessionUiModel {
    val date = parseDate(startLocalDate)
    return HistorySessionUiModel(
        sessionId = sessionId,
        dateLabel = date?.let { "${it.day} ${monthShortName(it.month)}" }
            ?: startLocalDate,
        timeLabel = formatTime(startLocalTime),
        title = title,
        durationSeconds = totalDurationSeconds,
        distanceMetres = distanceMetres,
        averageCadenceSpm = averageCadenceSpm,
        averageHeartRateBpm = averageHeartRateBpm,
        targetAdherencePercent = targetAdherencePercent,
        dataCoveragePercent = dataCoveragePercent,
        completed = completion == RunCompletion.Completed,
    )
}

fun buildDashboardUiState(
    records: List<RunRecord>,
    hasActiveSession: Boolean = false,
): DashboardUiState {
    val latest = records.maxByOrNull(RunRecord::startEpochMillis)
    return DashboardUiState(
        // Member 2 will replace this with the real Data Layer connection status.
        watchConnectionStatus = WatchConnectionStatus.Disconnected,
        hasResumableSession = hasActiveSession,
        lastRun = latest?.let { record ->
            LastRunUiModel(
                title = record.title,
                distanceKm = record.distanceMetres?.div(1000.0),
                averageCadenceSpm = record.averageCadenceSpm,
            )
        },
    )
}

fun buildHistoryUiState(
    records: List<RunRecord>,
    year: Int,
    month: Int,
    selectedDay: Int?,
): HistoryUiState {
    val monthRecords = records.filter { record ->
        parseDate(record.startLocalDate)?.let { date ->
            date.year == year && date.month == month
        } == true
    }
    val countsByDay = monthRecords.groupingBy {
        parseDate(it.startLocalDate)?.day
    }.eachCount()
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, 1)
    }
    val leadingBlankCount = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val calendarDays = buildList {
        repeat(leadingBlankCount) { add(CalendarDayUiModel(dayOfMonth = null)) }
        (1..daysInMonth).forEach { day ->
            add(
                CalendarDayUiModel(
                    dayOfMonth = day,
                    sessionCount = countsByDay[day] ?: 0,
                    isSelected = selectedDay == day,
                ),
            )
        }
        while (size % 7 != 0) add(CalendarDayUiModel(dayOfMonth = null))
    }

    val latestDay = monthRecords.mapNotNull { parseDate(it.startLocalDate)?.day }
        .maxOrNull()
        ?: 1
    val weeklyRecords = monthRecords.filter { record ->
        val day = parseDate(record.startLocalDate)?.day ?: return@filter false
        day in (latestDay - 6).coerceAtLeast(1)..latestDay
    }
    val visibleRecords = monthRecords
        .filter { selectedDay == null || parseDate(it.startLocalDate)?.day == selectedDay }
        .sortedByDescending(RunRecord::startEpochMillis)
    val latestComparableSource = monthRecords
        .sortedBy(RunRecord::startEpochMillis)
        .lastOrNull { record ->
            record.cadenceSource != CadenceSource.Unavailable &&
                    record.dataCoveragePercent?.let { it >= 70 } == true
        }
        ?.cadenceSource
    val trendRecords = monthRecords
        .filter { record ->
            record.cadenceSource == latestComparableSource &&
                    record.dataCoveragePercent?.let { it >= 70 } == true
        }
        .sortedBy(RunRecord::startEpochMillis)
        .takeLast(4)

    return HistoryUiState(
        monthLabel = "${monthLongName(month)} $year",
        calendarDays = calendarDays,
        weeklyDurationMinutes = weeklyRecords.sumOf { it.totalDurationSeconds }.div(60L).toInt(),
        weeklyDistanceKilometres = weeklyRecords.sumOf { it.distanceMetres ?: 0.0 }.div(1000.0),
        cadenceTrend = trendRecords.mapIndexed { index, record ->
            CadenceTrendPointUiModel(
                label = "R${index + 1}",
                cadenceSpm = record.averageCadenceSpm,
                rhythmStabilityPercent = record.rhythmStabilityPercent,
            )
        },
        sessions = visibleRecords.map(RunRecord::toHistorySessionUiModel),
    )
}

private data class DateParts(val year: Int, val month: Int, val day: Int)

private fun parseDate(value: String): DateParts? {
    val parts = value.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    return DateParts(year, month, day)
}

private fun formatTime(value: String): String {
    val parts = value.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return value
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return value
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when (val normalized = hour % 12) {
        0 -> 12
        else -> normalized
    }
    return String.format(Locale.US, "%d:%02d %s", displayHour, minute, period)
}

private fun monthLongName(month: Int): String =
    DateFormatSymbols(Locale.US).months.getOrElse(month - 1) { "Unknown" }

private fun monthShortName(month: Int): String =
    DateFormatSymbols(Locale.US).shortMonths.getOrElse(month - 1) { "" }
