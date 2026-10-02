package com.compx551.rhythmrun.data.repository

import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.domain.model.RunRecord
import com.compx551.rhythmrun.domain.model.RunStage
import com.compx551.rhythmrun.domain.model.RunStageResult
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Realistic sample run records for database seeding and UI preview.
 */
object SampleRunData {
    fun createSampleRecords(baseCalendar: Calendar = Calendar.getInstance()): List<RunRecord> {
        val zone = baseCalendar.timeZone ?: TimeZone.getDefault()
        fun formatDate(cal: Calendar): String = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )

        val cal1 = (baseCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 0) }
        val cal2 = (baseCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -2) }
        val cal3 = (baseCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -4) }

        return listOf(
            RunRecord(
                sessionId = "sample-session-1",
                startEpochMillis = cal1.timeInMillis,
                startLocalDate = formatDate(cal1),
                startLocalTime = "18:20",
                timeZoneId = zone.id,
                title = "Tempo Cadence Run",
                completion = RunCompletion.Completed,
                totalDurationSeconds = 1440L,
                distanceMetres = 4200.0,
                averageHeartRateBpm = 152,
                averageCadenceSpm = 168,
                cadenceSource = CadenceSource.DirectStepRate,
                targetAdherencePercent = 94,
                rhythmStabilityPercent = 91,
                averageMovementMagnitude = 1.15,
                heartRateResponseBpm = 168,
                cueCoveragePercent = 96,
                dataCoveragePercent = 99,
                stages = listOf(
                    RunStageResult(RunStage.WarmUp, 300L, 300L, 150, 148, 130, 0.95),
                    RunStageResult(RunStage.Running, 900L, 900L, 170, 171, 160, 1.25),
                    RunStageResult(RunStage.SlowDown, 180L, 180L, 155, 154, 145, 1.05),
                    RunStageResult(RunStage.Recovery, 60L, 60L, 130, 132, 125, 0.85),
                ),
                incompleteDataMessages = emptyList(),
            ),
            RunRecord(
                sessionId = "sample-session-2",
                startEpochMillis = cal2.timeInMillis,
                startLocalDate = formatDate(cal2),
                startLocalTime = "07:35",
                timeZoneId = zone.id,
                title = "Aerobic Base Run",
                completion = RunCompletion.Completed,
                totalDurationSeconds = 2100L,
                distanceMetres = 5800.0,
                averageHeartRateBpm = 144,
                averageCadenceSpm = 162,
                cadenceSource = CadenceSource.DirectStepRate,
                targetAdherencePercent = 89,
                rhythmStabilityPercent = 87,
                averageMovementMagnitude = 1.08,
                heartRateResponseBpm = 155,
                cueCoveragePercent = 92,
                dataCoveragePercent = 98,
                stages = listOf(
                    RunStageResult(RunStage.WarmUp, 300L, 300L, 145, 144, 122, 0.90),
                    RunStageResult(RunStage.Running, 1500L, 1500L, 165, 164, 148, 1.15),
                    RunStageResult(RunStage.SlowDown, 240L, 240L, 150, 149, 138, 1.00),
                    RunStageResult(RunStage.Recovery, 60L, 60L, 130, 128, 120, 0.80),
                ),
                incompleteDataMessages = emptyList(),
            ),
            RunRecord(
                sessionId = "sample-session-3",
                startEpochMillis = cal3.timeInMillis,
                startLocalDate = formatDate(cal3),
                startLocalTime = "17:45",
                timeZoneId = zone.id,
                title = "Interval Rhythm Training",
                completion = RunCompletion.Completed,
                totalDurationSeconds = 1200L,
                distanceMetres = 3500.0,
                averageHeartRateBpm = 160,
                averageCadenceSpm = 174,
                cadenceSource = CadenceSource.DirectStepRate,
                targetAdherencePercent = 96,
                rhythmStabilityPercent = 94,
                averageMovementMagnitude = 1.35,
                heartRateResponseBpm = 176,
                cueCoveragePercent = 98,
                dataCoveragePercent = 100,
                stages = listOf(
                    RunStageResult(RunStage.WarmUp, 300L, 300L, 150, 152, 135, 1.00),
                    RunStageResult(RunStage.Running, 720L, 720L, 180, 182, 172, 1.45),
                    RunStageResult(RunStage.SlowDown, 120L, 120L, 160, 158, 150, 1.10),
                    RunStageResult(RunStage.Recovery, 60L, 60L, 130, 130, 128, 0.85),
                ),
                incompleteDataMessages = emptyList(),
            ),
        )
    }
}
