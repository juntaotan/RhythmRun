package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RawSensorRecord
import com.compx551.rhythmrun.domain.model.RunRecord
import com.compx551.rhythmrun.domain.model.RunStage
import com.compx551.rhythmrun.domain.model.StageChangeRecord
import com.compx551.rhythmrun.domain.model.StoredSensorType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Rebuilds a saved summary from watch samples and the phone's active-stage timeline. */
object RunSummaryCalculator {
    private const val WINDOW_MILLIS = 10_000L

    fun update(
        record: RunRecord,
        readings: List<RawSensorRecord>,
        changes: List<StageChangeRecord>,
    ): RunRecord {
        val timeline = changes.sortedBy { it.timestampEpochMillis }
        val spans = timeline.zipWithNext().mapNotNull { (start, end) ->
            if (start.reason == "PAUSE" || start.reason == "STOP" || end.timestampEpochMillis <= start.timestampEpochMillis) null
            else Span(start.stage, start.timestampEpochMillis, end.timestampEpochMillis)
        }
        if (spans.isEmpty()) return record.copy(
            averageHeartRateBpm = null,
            averageCadenceSpm = null,
            cadenceSource = CadenceSource.Unavailable,
            targetAdherencePercent = null,
            rhythmStabilityPercent = null,
            dataCoveragePercent = null,
            stages = record.stages.map { it.copy(averageHeartRateBpm = null, averageCadenceSpm = null) },
        )

        val heartRates = readings.filter { it.sensorType == StoredSensorType.HeartRate && it.available && it.scalarValue?.let { value -> value.isFinite() && value > 0 } == true }
        val directCadence = readings.filter { it.sensorType == StoredSensorType.StepCadence && it.available && it.scalarValue?.let { value -> value.isFinite() && value > 0 } == true }.inSpans(spans)
        val cadence = if (directCadence.isNotEmpty()) directCadence else deriveCadence(readings, spans)
        val acceleration = readings.filter { it.sensorType == StoredSensorType.Accelerometer && it.available }
            .mapNotNull { reading ->
                if (reading.x == null || reading.y == null || reading.z == null) null
                else reading.timestampEpochMillis to sqrt(reading.x * reading.x + reading.y * reading.y + reading.z * reading.z)
            }
        val source = when {
            directCadence.any { it.source == "STEPS_PER_MINUTE" } -> CadenceSource.DirectStepRate
            directCadence.any { it.source == "ESTIMATED_WRIST_RHYTHM" } -> CadenceSource.EstimatedWristRhythm
            directCadence.isNotEmpty() -> CadenceSource.DerivedFromStepCount
            cadence.isNotEmpty() -> CadenceSource.DerivedFromStepCount
            else -> CadenceSource.Unavailable
        }
        val windows = spans.flatMap { span ->
            generateSequence(span.start) { previous -> (previous + WINDOW_MILLIS).takeIf { it < span.end } }
                .map { from ->
                    val to = minOf(from + WINDOW_MILLIS, span.end)
                    Window(span.stage, cadence.filter { it.timestampEpochMillis in from until to }.mapNotNull { it.scalarValue }.averageOrNull())
                }.toList()
        }
        val measured = windows.mapNotNull { it.cadence }
        val adherenceWindows = windows.filter { window -> record.stages.any { it.stage == window.stage && it.targetCadenceSpm != null } && window.cadence != null }
        val adherence = adherenceWindows.takeIf { it.isNotEmpty() }?.let { valid ->
            percent(valid.count { window ->
                val target = record.stages.first { it.stage == window.stage }.targetCadenceSpm!!
                abs(window.cadence!! - target) <= target * 0.05
            }, valid.size)
        }
        val stability = measured.takeIf { it.size >= 2 }?.let { values ->
            val mean = values.average()
            val deviation = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            (100.0 * (1.0 - deviation / mean)).roundToInt().coerceIn(0, 100)
        }
        val stages = record.stages.map { stage ->
            val intervals = spans.filter { it.stage == stage.stage }
            val stageCadence = cadence.inSpans(intervals).mapNotNull { it.scalarValue }
            val stageHeartRate = heartRates.inSpans(intervals).mapNotNull { it.scalarValue }
            val stageMovement = acceleration.filter { sample -> intervals.any { sample.first in it.start until it.end } }.map { it.second }
            stage.copy(
                averageCadenceSpm = stageCadence.averageOrNull()?.roundToInt(),
                averageHeartRateBpm = stageHeartRate.averageOrNull()?.roundToInt(),
                movementMagnitude = stageMovement.averageOrNull(),
            )
        }
        val validCadence = cadence.inSpans(spans).mapNotNull { it.scalarValue }
        val validHeartRate = heartRates.inSpans(spans).mapNotNull { it.scalarValue }
        val validMovement = acceleration.filter { sample -> spans.any { sample.first in it.start until it.end } }.map { it.second }
        return record.copy(
            averageCadenceSpm = measured.averageOrNull()?.roundToInt(),
            averageHeartRateBpm = validHeartRate.averageOrNull()?.roundToInt(),
            cadenceSource = source,
            targetAdherencePercent = adherence,
            rhythmStabilityPercent = stability,
            averageMovementMagnitude = validMovement.averageOrNull(),
            dataCoveragePercent = percent(measured.size, windows.size),
            stages = stages,
            incompleteDataMessages = buildList {
                if (validHeartRate.isEmpty()) add("Heart-rate data is unavailable.")
                if (validCadence.isEmpty()) add("Step cadence data is unavailable.")
                if (validMovement.isEmpty()) add("Movement data is unavailable.")
            },
        )
    }

    private fun deriveCadence(readings: List<RawSensorRecord>, spans: List<Span>): List<RawSensorRecord> =
        readings.filter { it.sensorType == StoredSensorType.StepCount && it.available && it.scalarValue != null }
            .inSpans(spans).sortedBy { it.timestampEpochMillis }.zipWithNext().mapNotNull { (before, after) ->
                val elapsed = after.timestampEpochMillis - before.timestampEpochMillis
                val steps = after.scalarValue!! - before.scalarValue!!
                if (elapsed !in 1L..10_000L || steps <= 0 || spans.none { before.timestampEpochMillis in it.start until it.end && after.timestampEpochMillis in it.start until it.end }) null
                else after.copy(sensorType = StoredSensorType.StepCadence, scalarValue = steps * 60_000 / elapsed)
            }

    private fun List<RawSensorRecord>.inSpans(spans: List<Span>) = filter { reading ->
        spans.any { reading.timestampEpochMillis in it.start until it.end }
    }

    private fun List<Double>.averageOrNull() = takeIf { it.isNotEmpty() }?.average()
    private fun percent(part: Int, total: Int) = if (total == 0) 0 else (part * 100.0 / total).roundToInt().coerceIn(0, 100)

    private data class Span(val stage: RunStage, val start: Long, val end: Long)
    private data class Window(val stage: RunStage, val cadence: Double?)
}
