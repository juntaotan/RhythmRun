package com.compx551.rhythmrun.ui.mapping

import com.compx551.rhythmrun.data.repository.SampleRunData
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RunUiMapperTest {
    @Test
    fun weeklyTotalsIncludePreviousMonthWithinSevenDays() {
        val mayFirst = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.MAY, 1)
        }
        val recent = SampleRunData.createSampleRecords(mayFirst)
        val older = recent.first().copy(
            sessionId = "older",
            startLocalDate = "2026-04-24",
            totalDurationSeconds = 600,
            distanceMetres = 1_000.0,
        )

        val state = buildHistoryUiState(recent + older, 2026, 5, null)

        assertEquals((recent.sumOf { it.totalDurationSeconds } / 60).toInt(), state.weeklyDurationMinutes)
        assertEquals(recent.sumOf { it.distanceMetres ?: 0.0 } / 1_000, state.weeklyDistanceKilometres, 1e-6)
    }
}
