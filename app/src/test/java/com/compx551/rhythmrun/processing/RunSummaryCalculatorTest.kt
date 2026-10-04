package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RawSensorRecord
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.domain.model.RunRecord
import com.compx551.rhythmrun.domain.model.RunStage
import com.compx551.rhythmrun.domain.model.RunStageResult
import com.compx551.rhythmrun.domain.model.StageChangeRecord
import com.compx551.rhythmrun.domain.model.StoredSensorType
import org.junit.Assert.assertEquals
import org.junit.Test

class RunSummaryCalculatorTest {
    @Test
    fun pausedReadingsDoNotAffectAveragesOrCoverage() {
        val record = RunRecord(
            sessionId = "run", startEpochMillis = 1_000, startLocalDate = "2026-10-04",
            startLocalTime = "10:00", timeZoneId = "UTC", title = "Run",
            completion = RunCompletion.Completed, totalDurationSeconds = 40,
            distanceMetres = null, averageHeartRateBpm = 500, averageCadenceSpm = 500,
            cadenceSource = CadenceSource.Unavailable, targetAdherencePercent = null,
            rhythmStabilityPercent = null, averageMovementMagnitude = null,
            heartRateResponseBpm = null, cueCoveragePercent = null, dataCoveragePercent = null,
            stages = listOf(RunStageResult(RunStage.Running, 40, 40, 160, null, null, null)),
            incompleteDataMessages = emptyList(),
        )
        val changes = listOf(
            change(1_000, "START"), change(21_000, "PAUSE"),
            change(31_000, "RESUME"), change(51_000, "STOP"),
        )
        val readings = listOf(
            reading(2_000, StoredSensorType.StepCadence, 160.0),
            reading(12_000, StoredSensorType.StepCadence, 164.0),
            reading(26_000, StoredSensorType.StepCadence, 500.0),
            reading(32_000, StoredSensorType.StepCadence, 200.0),
            reading(2_000, StoredSensorType.HeartRate, 100.0),
            reading(26_000, StoredSensorType.HeartRate, 500.0),
            reading(32_000, StoredSensorType.HeartRate, 140.0),
        )

        val summary = RunSummaryCalculator.update(record, readings, changes)

        assertEquals(175, summary.averageCadenceSpm)
        assertEquals(120, summary.averageHeartRateBpm)
        assertEquals(67, summary.targetAdherencePercent)
        assertEquals(90, summary.rhythmStabilityPercent)
        assertEquals(75, summary.dataCoveragePercent)
        assertEquals(CadenceSource.DirectStepRate, summary.cadenceSource)
        assertEquals(175, summary.stages.single().averageCadenceSpm)
    }

    private fun change(timestamp: Long, reason: String) =
        StageChangeRecord("run", timestamp, timestamp, RunStage.Running, reason)

    private fun reading(timestamp: Long, type: StoredSensorType, value: Double) =
        RawSensorRecord(
            sessionId = "run", sensorType = type, sequence = timestamp,
            timestampEpochMillis = timestamp, scalarValue = value,
            unit = if (type == StoredSensorType.HeartRate) "bpm" else "spm",
            source = if (type == StoredSensorType.StepCadence) "STEPS_PER_MINUTE" else null,
        )
}
