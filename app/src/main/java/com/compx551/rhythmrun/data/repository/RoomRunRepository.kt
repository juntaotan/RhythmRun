package com.compx551.rhythmrun.data.repository

import com.compx551.rhythmrun.data.local.AnalysisWindowEntity
import com.compx551.rhythmrun.data.local.CueEventEntity
import com.compx551.rhythmrun.data.local.IncompleteDataMessageEntity
import com.compx551.rhythmrun.data.local.LocationFixEntity
import com.compx551.rhythmrun.data.local.PhoneRoomDatabase
import com.compx551.rhythmrun.data.local.RawSensorReadingEntity
import com.compx551.rhythmrun.data.local.RunSessionEntity
import com.compx551.rhythmrun.data.local.RunStageEntity
import com.compx551.rhythmrun.data.local.RunSummaryEntity
import com.compx551.rhythmrun.data.local.StageChangeEntity
import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.AnalysisWindowRecord
import com.compx551.rhythmrun.domain.model.CueEventRecord
import com.compx551.rhythmrun.domain.model.LocationFixRecord
import com.compx551.rhythmrun.domain.model.RawSensorRecord
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.domain.model.RunDataBatch
import com.compx551.rhythmrun.domain.model.RunPlan
import com.compx551.rhythmrun.domain.model.RunRecord
import com.compx551.rhythmrun.domain.model.RunSessionState
import com.compx551.rhythmrun.domain.model.RunStage
import com.compx551.rhythmrun.domain.model.RunStageResult
import com.compx551.rhythmrun.domain.model.StageChangeRecord
import com.compx551.rhythmrun.domain.model.StoredSensorType
import com.compx551.rhythmrun.domain.repository.RunRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomRunRepository(
    database: PhoneRoomDatabase,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : RunRepository {
    private val dao = database.phoneRunDao()

    init {
        scope.launch {
            val sessions = dao.observeSessions().first()
            if (sessions.isEmpty()) {
                SampleRunData.createSampleRecords().forEach { session ->
                    upsert(session)
                    val sampleFixes = SampleRunData.createSampleLocationFixes(session.sessionId, session.startEpochMillis)
                    persistBatch(RunDataBatch(locationFixes = sampleFixes))
                }
            } else {
                sessions.filter { it.sessionId.startsWith("sample-session-") }.forEach { session ->
                    if (dao.findLocationFixes(session.sessionId).isEmpty()) {
                        val sampleFixes = SampleRunData.createSampleLocationFixes(session.sessionId, session.startEpochMillis)
                        persistBatch(RunDataBatch(locationFixes = sampleFixes))
                    }
                }
            }
        }
    }

    override val records: StateFlow<List<RunRecord>> = dao.observeSessions()
        .map { sessions ->
            sessions.mapNotNull { session -> loadRecord(session) }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val activePlan: StateFlow<RunPlan?> = dao.observeSessions()
        .map { sessions ->
            val active = sessions.firstOrNull { session ->
                session.state in setOf(
                    RunSessionState.Planned.name,
                    RunSessionState.Active.name,
                    RunSessionState.Paused.name,
                )
            } ?: return@map null
            val stages = dao.findStages(active.sessionId)
            RunPlan(
                sessionId = active.sessionId,
                startEpochMillis = active.startEpochMillis,
                startLocalDate = active.startLocalDate,
                startLocalTime = active.startLocalTime,
                timeZoneId = active.timeZoneId,
                guidanceEnabled = active.guidanceEnabled,
                outdoorRouteEnabled = active.outdoorRouteEnabled,
                state = enumValueOrDefault(active.state, RunSessionState.Planned),
                stages = stages.map { stage ->
                    com.compx551.rhythmrun.domain.model.RunPlanStage(
                        stage = enumValueOrDefault(stage.stage, RunStage.WarmUp),
                        order = stage.stageOrder,
                        durationSeconds = stage.plannedDurationSeconds,
                        targetCadenceSpm = stage.targetCadenceSpm,
                    )
                },
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun createSession(plan: RunPlan) {
        dao.replacePlan(
            session = plan.toEntity(),
            stages = plan.stages.map { stage ->
                RunStageEntity(
                    sessionId = plan.sessionId,
                    stageOrder = stage.order,
                    stage = stage.stage.name,
                    plannedDurationSeconds = stage.durationSeconds,
                    targetCadenceSpm = stage.targetCadenceSpm,
                )
            },
        )
    }

    override suspend fun updateSessionState(
        sessionId: String,
        state: RunSessionState,
        endEpochMillis: Long?,
    ) {
        val current = dao.findSession(sessionId) ?: return
        dao.upsertSession(
            current.copy(
                state = state.name,
                endEpochMillis = endEpochMillis ?: current.endEpochMillis,
            ),
        )
    }

    override suspend fun persistBatch(batch: RunDataBatch) {
        dao.persistIncomingBatch(
            rawReadings = batch.rawReadings.map { reading ->
                RawSensorReadingEntity(
                    sessionId = reading.sessionId,
                    sensorType = reading.sensorType.name,
                    sequence = reading.sequence,
                    timestampEpochMillis = reading.timestampEpochMillis,
                    x = reading.x,
                    y = reading.y,
                    z = reading.z,
                    scalarValue = reading.scalarValue,
                    unit = reading.unit,
                    source = reading.source,
                    available = reading.available,
                )
            },
            locationFixes = batch.locationFixes.map { fix ->
                LocationFixEntity(
                    sessionId = fix.sessionId,
                    sequence = fix.sequence,
                    timestampEpochMillis = fix.timestampEpochMillis,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyMetres = fix.accuracyMetres,
                    available = fix.available,
                    gapBefore = fix.gapBefore,
                )
            },
            stageChanges = batch.stageChanges.map { change ->
                StageChangeEntity(
                    sessionId = change.sessionId,
                    sequence = change.sequence,
                    timestampEpochMillis = change.timestampEpochMillis,
                    stage = change.stage.name,
                    reason = change.reason,
                )
            },
            cueEvents = batch.cueEvents.map { event ->
                CueEventEntity(
                    sessionId = event.sessionId,
                    sequence = event.sequence,
                    intendedTimestampEpochMillis = event.intendedTimestampEpochMillis,
                    actualTimestampEpochMillis = event.actualTimestampEpochMillis,
                    stage = event.stage.name,
                    delivered = event.delivered,
                )
            },
            analysisWindows = batch.analysisWindows.map { window ->
                AnalysisWindowEntity(
                    sessionId = window.sessionId,
                    windowStartEpochMillis = window.windowStartEpochMillis,
                    windowEndEpochMillis = window.windowEndEpochMillis,
                    analysisVersion = window.analysisVersion,
                    cadenceSpm = window.cadenceSpm,
                    cadenceSource = window.cadenceSource.name,
                    rhythmStabilityPercent = window.rhythmStabilityPercent,
                    movementMagnitude = window.movementMagnitude,
                    averageHeartRateBpm = window.averageHeartRateBpm,
                    confidencePercent = window.confidencePercent,
                    coveragePercent = window.coveragePercent,
                )
            },
        )
    }

    override suspend fun upsert(record: RunRecord) {
        val existing = dao.findSession(record.sessionId)
        dao.replaceCompletedRun(
            session = RunSessionEntity(
                sessionId = record.sessionId,
                startEpochMillis = record.startEpochMillis,
                startLocalDate = record.startLocalDate,
                startLocalTime = record.startLocalTime,
                timeZoneId = record.timeZoneId,
                title = record.title,
                state = when (record.completion) {
                    RunCompletion.Completed -> RunSessionState.Completed
                    RunCompletion.StoppedEarly -> RunSessionState.StoppedEarly
                }.name,
                guidanceEnabled = existing?.guidanceEnabled ?: false,
                outdoorRouteEnabled = existing?.outdoorRouteEnabled ?: false,
                endEpochMillis = record.startEpochMillis + record.totalDurationSeconds * 1_000L,
            ),
            stages = record.stages.mapIndexed { index, stage -> stage.toEntity(record.sessionId, index) },
            summary = record.toSummaryEntity(),
            messages = record.incompleteDataMessages.mapIndexed { index, message ->
                IncompleteDataMessageEntity(record.sessionId, index, message)
            },
        )
    }

    override suspend fun findById(sessionId: String): RunRecord? {
        val session = dao.findSession(sessionId) ?: return null
        return loadRecord(session)
    }

    override suspend fun rawReadings(sessionId: String): List<RawSensorRecord> =
        dao.findRawReadings(sessionId).map { reading ->
            RawSensorRecord(
                sessionId = reading.sessionId,
                sensorType = enumValueOrDefault(reading.sensorType, StoredSensorType.Accelerometer),
                sequence = reading.sequence,
                timestampEpochMillis = reading.timestampEpochMillis,
                x = reading.x,
                y = reading.y,
                z = reading.z,
                scalarValue = reading.scalarValue,
                unit = reading.unit,
                source = reading.source,
                available = reading.available,
            )
        }

    override suspend fun locationFixes(sessionId: String): List<LocationFixRecord> =
        dao.findLocationFixes(sessionId).map { fix ->
            LocationFixRecord(
                sessionId = fix.sessionId,
                sequence = fix.sequence,
                timestampEpochMillis = fix.timestampEpochMillis,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMetres = fix.accuracyMetres,
                available = fix.available,
                gapBefore = fix.gapBefore,
            )
        }

    override suspend fun stageChanges(sessionId: String): List<StageChangeRecord> =
        dao.findStageChanges(sessionId).map { change ->
            StageChangeRecord(
                sessionId = change.sessionId,
                sequence = change.sequence,
                timestampEpochMillis = change.timestampEpochMillis,
                stage = enumValueOrDefault(change.stage, RunStage.WarmUp),
                reason = change.reason,
            )
        }

    override suspend fun cueEvents(sessionId: String): List<CueEventRecord> =
        dao.findCueEvents(sessionId).map { event ->
            CueEventRecord(
                sessionId = event.sessionId,
                sequence = event.sequence,
                intendedTimestampEpochMillis = event.intendedTimestampEpochMillis,
                actualTimestampEpochMillis = event.actualTimestampEpochMillis,
                stage = enumValueOrDefault(event.stage, RunStage.WarmUp),
                delivered = event.delivered,
            )
        }

    override suspend fun analysisWindows(sessionId: String): List<AnalysisWindowRecord> =
        dao.findAnalysisWindows(sessionId).map { window ->
            AnalysisWindowRecord(
                sessionId = window.sessionId,
                windowStartEpochMillis = window.windowStartEpochMillis,
                windowEndEpochMillis = window.windowEndEpochMillis,
                analysisVersion = window.analysisVersion,
                cadenceSpm = window.cadenceSpm,
                cadenceSource = enumValueOrDefault(window.cadenceSource, CadenceSource.Unavailable),
                rhythmStabilityPercent = window.rhythmStabilityPercent,
                movementMagnitude = window.movementMagnitude,
                averageHeartRateBpm = window.averageHeartRateBpm,
                confidencePercent = window.confidencePercent,
                coveragePercent = window.coveragePercent,
            )
        }

    private suspend fun loadRecord(session: RunSessionEntity): RunRecord? {
        val summary = dao.findSummary(session.sessionId) ?: return null
        val stages = dao.findStages(session.sessionId)
        return RunRecord(
            sessionId = session.sessionId,
            startEpochMillis = session.startEpochMillis,
            startLocalDate = session.startLocalDate,
            startLocalTime = session.startLocalTime,
            timeZoneId = session.timeZoneId,
            title = session.title,
            completion = if (session.state == RunSessionState.Completed.name) {
                RunCompletion.Completed
            } else {
                RunCompletion.StoppedEarly
            },
            totalDurationSeconds = summary.totalDurationSeconds,
            distanceMetres = summary.distanceMetres,
            averageHeartRateBpm = summary.averageHeartRateBpm,
            averageCadenceSpm = summary.averageCadenceSpm,
            cadenceSource = enumValueOrDefault(summary.cadenceSource, CadenceSource.Unavailable),
            targetAdherencePercent = summary.targetAdherencePercent,
            rhythmStabilityPercent = summary.rhythmStabilityPercent,
            averageMovementMagnitude = summary.averageMovementMagnitude,
            heartRateResponseBpm = summary.heartRateResponseBpm,
            cueCoveragePercent = summary.cueCoveragePercent,
            dataCoveragePercent = summary.dataCoveragePercent,
            stages = stages.map { it.toDomain() },
            incompleteDataMessages = dao.findIncompleteMessages(session.sessionId).map { it.message },
        )
    }
}

private fun RunPlan.toEntity(): RunSessionEntity = RunSessionEntity(
    sessionId = sessionId,
    startEpochMillis = startEpochMillis,
    startLocalDate = startLocalDate,
    startLocalTime = startLocalTime,
    timeZoneId = timeZoneId,
    title = "Four-stage rhythm run",
    state = state.name,
    guidanceEnabled = guidanceEnabled,
    outdoorRouteEnabled = outdoorRouteEnabled,
)

private fun RunStageResult.toEntity(sessionId: String, order: Int): RunStageEntity =
    RunStageEntity(
        sessionId = sessionId,
        stageOrder = order,
        stage = stage.name,
        plannedDurationSeconds = plannedDurationSeconds,
        targetCadenceSpm = targetCadenceSpm,
        actualDurationSeconds = actualDurationSeconds,
        averageCadenceSpm = averageCadenceSpm,
        averageHeartRateBpm = averageHeartRateBpm,
        movementMagnitude = movementMagnitude,
    )

private fun RunStageEntity.toDomain(): RunStageResult = RunStageResult(
    stage = enumValueOrDefault(stage, RunStage.WarmUp),
    plannedDurationSeconds = plannedDurationSeconds,
    actualDurationSeconds = actualDurationSeconds ?: 0L,
    targetCadenceSpm = targetCadenceSpm,
    averageCadenceSpm = averageCadenceSpm,
    averageHeartRateBpm = averageHeartRateBpm,
    movementMagnitude = movementMagnitude,
)

private fun RunRecord.toSummaryEntity(): RunSummaryEntity = RunSummaryEntity(
    sessionId = sessionId,
    totalDurationSeconds = totalDurationSeconds,
    distanceMetres = distanceMetres,
    averageHeartRateBpm = averageHeartRateBpm,
    averageCadenceSpm = averageCadenceSpm,
    cadenceSource = cadenceSource.name,
    targetAdherencePercent = targetAdherencePercent,
    rhythmStabilityPercent = rhythmStabilityPercent,
    averageMovementMagnitude = averageMovementMagnitude,
    heartRateResponseBpm = heartRateResponseBpm,
    cueCoveragePercent = cueCoveragePercent,
    dataCoveragePercent = dataCoveragePercent,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
