package com.compx551.rhythmrun.ui

import android.content.Context
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.compx551.rhythmrun.data.local.PhoneRoomDatabase
import com.compx551.rhythmrun.data.repository.RoomRunRepository
import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.domain.model.RunRecord
import com.compx551.rhythmrun.domain.model.LocationFixRecord
import com.compx551.rhythmrun.domain.model.RunDataBatch
import com.compx551.rhythmrun.domain.model.RunPlan
import com.compx551.rhythmrun.domain.model.RunPlanStage
import com.compx551.rhythmrun.domain.model.RunSessionState
import com.compx551.rhythmrun.domain.model.RunStage
import com.compx551.rhythmrun.domain.model.RunStageResult
import com.compx551.rhythmrun.domain.repository.RunRepository
import com.compx551.rhythmrun.processing.model.ProcessedLocation
import com.compx551.rhythmrun.ui.dashboard.DashboardUiState
import com.compx551.rhythmrun.ui.history.HistoryUiState
import com.compx551.rhythmrun.ui.mapping.buildDashboardUiState
import com.compx551.rhythmrun.ui.mapping.buildHistoryUiState
import com.compx551.rhythmrun.ui.mapping.toSummaryUiState
import com.compx551.rhythmrun.ui.runsession.LiveRunSyncStatus
import com.compx551.rhythmrun.ui.runsession.LiveRunUiState
import com.compx551.rhythmrun.ui.runsession.RunSessionUiMode
import com.compx551.rhythmrun.ui.runsession.RunSessionUiState
import com.compx551.rhythmrun.ui.runsession.createInitialRunSessionUiState
import com.compx551.rhythmrun.communication.RhythmDataListenerService
import com.compx551.rhythmrun.communication.RhythmReading
import com.compx551.rhythmrun.communication.SessionCommandClient
import com.compx551.rhythmrun.processing.LiveRunProcessor
import com.compx551.rhythmrun.processing.model.ProcessedReading
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RhythmRunViewModel(
    private val repository: RunRepository,
    private val sessionCommandClient: SessionCommandClient? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) : ViewModel() {
    private var liveRunProcessor: LiveRunProcessor? = null
    private var timerJob: Job? = null
    private var processedReadingsJob: Job? = null
    private var activeRunStartedAtMillis: Long? = null
    private val initialCalendar = Calendar.getInstance(timeZone)
    private val selectedMonth = MutableStateFlow(
        MonthSelection(
            year = initialCalendar.get(Calendar.YEAR),
            month = initialCalendar.get(Calendar.MONTH) + 1,
        ),
    )
    private val selectedDay = MutableStateFlow<Int?>(null)

    var runSessionState by mutableStateOf(createInitialRunSessionUiState())
        private set

    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.records,
        repository.activePlan,
    ) { records, activePlan ->
        buildDashboardUiState(records, activePlan != null)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = buildDashboardUiState(
                repository.records.value,
                repository.activePlan.value != null,
            ),
        )

    val historyState: StateFlow<HistoryUiState> = combine(
        repository.records,
        selectedMonth,
        selectedDay,
    ) { records, month, day ->
        buildHistoryUiState(
            records = records,
            year = month.year,
            month = month.month,
            selectedDay = day,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = buildHistoryUiState(
            records = repository.records.value,
            year = selectedMonth.value.year,
            month = selectedMonth.value.month,
            selectedDay = null,
        ),
    )

    private val watchReadingListener: (RhythmReading) -> Unit = { reading ->
        viewModelScope.launch {
            val live = runSessionState.liveRun
            if (live.sessionId != reading.sessionId || live.syncStatus != LiveRunSyncStatus.ReceivingData) {
                runSessionState = runSessionState.copy(
                    liveRun = live.copy(
                        sessionId = reading.sessionId,
                        syncStatus = LiveRunSyncStatus.ReceivingData,
                    ),
                )
            }
            if (liveRunProcessor == null) {
                startLiveProcessing()
            }
            liveRunProcessor?.onReading(reading)
        }
    }

    init {
        RhythmDataListenerService.readingListener = watchReadingListener
    }

    fun startNewRun() {
        activeRunStartedAtMillis = null
        runSessionState = createInitialRunSessionUiState()
    }

    fun resumeActiveRun() {
        val plan = repository.activePlan.value ?: return
        activeRunStartedAtMillis = plan.startEpochMillis
        val stages = plan.stages.sortedBy { it.order }.map { stage ->
            com.compx551.rhythmrun.ui.runsession.StagePlanUiState(
                stage = stage.stage,
                durationMinutesInput = formatMinutesInput(stage.durationSeconds),
                targetCadenceSpmInput = stage.targetCadenceSpm?.toString(),
            )
        }
        val current = stages.firstOrNull() ?: return
        runSessionState = RunSessionUiState(
            mode = RunSessionUiMode.Live,
            stages = stages,
            guidanceEnabled = plan.guidanceEnabled,
            liveRun = LiveRunUiState(
                sessionId = plan.sessionId,
                currentStage = current.stage,
                currentStageNumber = 1,
                stageDurationSeconds = durationMinutesToSeconds(current.durationMinutesInput),
                isPaused = plan.state == RunSessionState.Paused,
                syncStatus = LiveRunSyncStatus.WaitingForWatch,
            ),
        )
        startLiveProcessing()
    }

    fun updateDuration(stage: RunStage, value: String) {
        val normalized = value.replace(',', '.')
        if (normalized.count { it == '.' } > 1) return
        if (normalized.any { !it.isDigit() && it != '.' }) return
        runSessionState = runSessionState.copy(
            stages = runSessionState.stages.map { stageState ->
                if (stageState.stage == stage) {
                    stageState.copy(durationMinutesInput = normalized)
                } else {
                    stageState
                }
            },
        )
    }

    fun updateCadence(stage: RunStage, value: String) {
        if (!value.all(Char::isDigit)) return
        runSessionState = runSessionState.copy(
            stages = runSessionState.stages.map { stageState ->
                if (stageState.stage == stage && stageState.targetCadenceSpmInput != null) {
                    stageState.copy(targetCadenceSpmInput = value)
                } else {
                    stageState
                }
            },
        )
    }

    fun updateGuidanceEnabled(enabled: Boolean) {
        runSessionState = runSessionState.copy(guidanceEnabled = enabled)
    }

    fun startRun() {
        if (!runSessionState.isPlanValid) return
        val warmUp = runSessionState.stages.first { it.stage == RunStage.WarmUp }
        val startedAt = nowMillis()
        val sessionId = UUID.randomUUID().toString()
        activeRunStartedAtMillis = startedAt

        runSessionState = runSessionState.copy(
            mode = RunSessionUiMode.Live,
            liveRun = LiveRunUiState(
                sessionId = sessionId,
                currentStage = RunStage.WarmUp,
                currentStageNumber = 1,
                stageElapsedSeconds = 0L,
                stageDurationSeconds = durationMinutesToSeconds(warmUp.durationMinutesInput),
                totalElapsedSeconds = 0L,
                targetCadenceSpm = warmUp.targetCadenceSpmInput?.toIntOrNull(),
                syncStatus = LiveRunSyncStatus.WaitingForWatch,
            ),
        )
        viewModelScope.launch {
            repository.createSession(createPlan(runSessionState, sessionId, startedAt))
            repository.updateSessionState(sessionId, RunSessionState.Active)
        }
        sessionCommandClient?.sendStart(sessionId)
        startLiveProcessing()
    }

    fun togglePause() {
        val paused = !runSessionState.liveRun.isPaused
        runSessionState = runSessionState.copy(
            liveRun = runSessionState.liveRun.copy(
                isPaused = paused,
            ),
        )
        runSessionState.liveRun.sessionId?.let { sessionId ->
            viewModelScope.launch {
                repository.updateSessionState(
                    sessionId = sessionId,
                    state = if (paused) RunSessionState.Paused else RunSessionState.Active,
                )
            }
            if (paused) {
                sessionCommandClient?.sendPause(sessionId)
            } else {
                sessionCommandClient?.sendResume(sessionId)
            }
        }
    }

    fun finishRun() {
        stopLiveProcessing()
        runSessionState.liveRun.sessionId?.let { sessionId ->
            sessionCommandClient?.sendStop(sessionId)
        }
        val record = createRunRecord(runSessionState)
        val route = runSessionState.liveRun.routePoints
        runSessionState = runSessionState.copy(
            mode = RunSessionUiMode.Summary,
            summary = record.toSummaryUiState(routePoints = route),
        )
        viewModelScope.launch {
            repository.upsert(record)
            if (route.isNotEmpty()) {
                val fixes = route.mapIndexed { index, loc ->
                    LocationFixRecord(
                        sessionId = record.sessionId,
                        sequence = index.toLong(),
                        timestampEpochMillis = record.startEpochMillis + index * 1000L,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMetres = loc.accuracyMeters,
                        available = true,
                    )
                }
                repository.persistBatch(RunDataBatch(locationFixes = fixes))
            }
        }
    }

    private fun startLiveProcessing() {
        timerJob?.cancel()
        processedReadingsJob?.cancel()
        val processor = LiveRunProcessor()
        liveRunProcessor = processor

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                advanceTimer()
            }
        }

        processedReadingsJob = viewModelScope.launch {
            processor.processedReadings.collect { readings ->
                readings.lastOrNull()?.let(::updateLiveMetrics)
            }
        }
        RhythmDataListenerService.readingListener = watchReadingListener
    }

    private fun advanceTimer() {
        val live = runSessionState.liveRun
        if (live.isPaused) return

        val newStageElapsed = live.stageElapsedSeconds + 1L
        val newTotalElapsed = live.totalElapsedSeconds + 1L

        if (newStageElapsed >= live.stageDurationSeconds && live.stageDurationSeconds > 0L) {
            val nextStageNum = live.currentStageNumber + 1
            if (nextStageNum <= runSessionState.stages.size) {
                val nextStagePlan = runSessionState.stages[nextStageNum - 1]
                val nextDuration = durationMinutesToSeconds(nextStagePlan.durationMinutesInput)
                runSessionState = runSessionState.copy(
                    liveRun = live.copy(
                        currentStage = nextStagePlan.stage,
                        currentStageNumber = nextStageNum,
                        stageElapsedSeconds = 0L,
                        stageDurationSeconds = nextDuration,
                        totalElapsedSeconds = newTotalElapsed,
                        targetCadenceSpm = nextStagePlan.targetCadenceSpmInput?.toIntOrNull(),
                    ),
                )
            } else {
                runSessionState = runSessionState.copy(
                    liveRun = live.copy(
                        stageElapsedSeconds = newStageElapsed,
                        totalElapsedSeconds = newTotalElapsed,
                    ),
                )
            }
        } else {
            runSessionState = runSessionState.copy(
                liveRun = live.copy(
                    stageElapsedSeconds = newStageElapsed,
                    totalElapsedSeconds = newTotalElapsed,
                ),
            )
        }
    }

    private fun updateLiveMetrics(reading: ProcessedReading) {
        val currentLive = runSessionState.liveRun
        val cadence = (reading.stepCounterPerSecond * 60.0).roundToInt()
        val speedKmh = reading.velocityMetersPerSecond * 3.6
        val hr = reading.heartRateBpm.roundToInt().takeIf { it > 0 }

        val newLocation = reading.location?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
        val lastPoint = currentLive.routePoints.lastOrNull()
        val isNewPoint = newLocation != null && (lastPoint == null || lastPoint.latitude != newLocation.latitude || lastPoint.longitude != newLocation.longitude)

        val newRoute = if (newLocation != null && isNewPoint) {
            currentLive.routePoints + newLocation
        } else {
            currentLive.routePoints
        }

        val addedDistance = if (newLocation != null && lastPoint != null && isNewPoint) {
            computeDistanceMeters(
                lastPoint.latitude, lastPoint.longitude,
                newLocation.latitude, newLocation.longitude,
            )
        } else {
            0.0
        }

        runSessionState = runSessionState.copy(
            liveRun = currentLive.copy(
                heartRateBpm = hr ?: currentLive.heartRateBpm,
                cadenceSpm = if (cadence > 0) cadence else currentLive.cadenceSpm,
                speedKilometresPerHour = if (speedKmh > 0) speedKmh else currentLive.speedKilometresPerHour,
                distanceMetres = currentLive.distanceMetres + addedDistance,
                accelerationMagnitude = reading.accelerationPerSecond.takeIf { it > 0.0 } ?: currentLive.accelerationMagnitude,
                efficiency = reading.efficiency,
                currentLocation = newLocation ?: currentLive.currentLocation,
                routePoints = newRoute,
                syncStatus = LiveRunSyncStatus.ReceivingData,
            ),
        )
    }

    private fun computeDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val latDelta = Math.toRadians(lat2 - lat1)
        val lonDelta = Math.toRadians(lon2 - lon1)
        val a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2)
        val c = 2 * Math.atan2(Math.sqrt(a.coerceIn(0.0, 1.0)), Math.sqrt((1.0 - a).coerceIn(0.0, 1.0)))
        return 6_371_000.0 * c
    }

    private fun stopLiveProcessing() {
        timerJob?.cancel()
        timerJob = null
        processedReadingsJob?.cancel()
        processedReadingsJob = null
        RhythmDataListenerService.readingListener = null
        liveRunProcessor = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveProcessing()
    }

    fun openSummary(sessionId: String, onOpened: () -> Unit) {
        viewModelScope.launch {
            val record = repository.findById(sessionId) ?: return@launch
            val fixes = repository.locationFixes(sessionId)
            val route = fixes.mapNotNull { fix ->
                if (fix.latitude != null && fix.longitude != null) {
                    ProcessedLocation(fix.latitude, fix.longitude, fix.accuracyMetres ?: 0.0)
                } else null
            }
            runSessionState = runSessionState.copy(
                mode = RunSessionUiMode.Summary,
                summary = record.toSummaryUiState(routePoints = route),
            )
            onOpened()
        }
    }

    fun selectHistoryDay(day: Int) {
        selectedDay.value = if (selectedDay.value == day) null else day
    }

    fun showPreviousMonth() {
        selectedMonth.value = selectedMonth.value.previous()
        selectedDay.value = null
    }

    fun showNextMonth() {
        selectedMonth.value = selectedMonth.value.next()
        selectedDay.value = null
    }

    private fun createPlan(
        state: RunSessionUiState,
        sessionId: String,
        timestamp: Long,
    ): RunPlan {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
        return RunPlan(
            sessionId = sessionId,
            startEpochMillis = timestamp,
            startLocalDate = localDate(calendar),
            startLocalTime = localTime(calendar),
            timeZoneId = timeZone.id,
            guidanceEnabled = state.guidanceEnabled,
            state = RunSessionState.Active,
            stages = state.stages.mapIndexed { index, stage ->
                RunPlanStage(
                    stage = stage.stage,
                    order = index,
                    durationSeconds = durationMinutesToSeconds(stage.durationMinutesInput),
                    targetCadenceSpm = stage.targetCadenceSpmInput?.toIntOrNull(),
                )
            },
        )
    }

    private fun createRunRecord(state: RunSessionUiState): RunRecord {
        val timestamp = activeRunStartedAtMillis ?: nowMillis()
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
        val plannedDurations = state.stages.map { stage ->
            durationMinutesToSeconds(stage.durationMinutesInput)
        }
        val currentStageIndex = state.liveRun.currentStageNumber.minus(1).coerceIn(0, 3)
        val stageResults = state.stages.mapIndexed { index, stage ->
            val targetCadence = stage.targetCadenceSpmInput?.toIntOrNull()
            RunStageResult(
                stage = stage.stage,
                plannedDurationSeconds = plannedDurations[index],
                actualDurationSeconds = when {
                    index < currentStageIndex -> plannedDurations[index]
                    index == currentStageIndex -> state.liveRun.stageElapsedSeconds
                    else -> 0L
                },
                targetCadenceSpm = targetCadence,
                averageCadenceSpm = state.liveRun.cadenceSpm.takeIf { index == currentStageIndex },
                averageHeartRateBpm = state.liveRun.heartRateBpm.takeIf { index == currentStageIndex },
                movementMagnitude = state.liveRun.accelerationMagnitude.takeIf { index == currentStageIndex },
            )
        }
        val completed = state.liveRun.totalElapsedSeconds >= plannedDurations.sum()
        val missing = buildList {
            if (state.liveRun.heartRateBpm == null) add("Heart-rate data is unavailable.")
            if (state.liveRun.cadenceSpm == null) add("Step cadence or wrist-rhythm data is unavailable.")
            if (state.liveRun.accelerationMagnitude == null) add("Movement data is unavailable.")
            if (state.liveRun.syncStatus != LiveRunSyncStatus.ReceivingData) {
                add("The Watch data stream was not connected when this summary was created.")
            }
        }

        return RunRecord(
            sessionId = state.liveRun.sessionId ?: UUID.randomUUID().toString(),
            startEpochMillis = timestamp,
            startLocalDate = localDate(calendar),
            startLocalTime = localTime(calendar),
            timeZoneId = timeZone.id,
            title = "Four-stage rhythm run",
            completion = if (completed) RunCompletion.Completed else RunCompletion.StoppedEarly,
            totalDurationSeconds = state.liveRun.totalElapsedSeconds,
            distanceMetres = state.liveRun.distanceMetres.takeIf { it > 0.0 },
            averageHeartRateBpm = state.liveRun.heartRateBpm,
            averageCadenceSpm = state.liveRun.cadenceSpm,
            cadenceSource = if (state.liveRun.cadenceSpm == null) {
                CadenceSource.Unavailable
            } else {
                CadenceSource.DirectStepRate
            },
            targetAdherencePercent = null,
            rhythmStabilityPercent = null,
            averageMovementMagnitude = state.liveRun.accelerationMagnitude,
            heartRateResponseBpm = null,
            cueCoveragePercent = null,
            dataCoveragePercent = null,
            stages = stageResults,
            incompleteDataMessages = missing,
        )
    }

    class Factory(
        private val repository: RunRepository,
        private val sessionCommandClient: SessionCommandClient? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RhythmRunViewModel::class.java))
            return RhythmRunViewModel(repository, sessionCommandClient) as T
        }

        companion object {
            fun production(context: Context): Factory = Factory(
                repository = RoomRunRepository(PhoneRoomDatabase.getInstance(context)),
                sessionCommandClient = SessionCommandClient(context),
            )
        }
    }
}

private fun localDate(calendar: Calendar): String = String.format(
    Locale.US,
    "%04d-%02d-%02d",
    calendar.get(Calendar.YEAR),
    calendar.get(Calendar.MONTH) + 1,
    calendar.get(Calendar.DAY_OF_MONTH),
)

private fun localTime(calendar: Calendar): String = String.format(
    Locale.US,
    "%02d:%02d",
    calendar.get(Calendar.HOUR_OF_DAY),
    calendar.get(Calendar.MINUTE),
)

private fun durationMinutesToSeconds(input: String): Long {
    val minutes = input.replace(',', '.').toDoubleOrNull() ?: 0.0
    return (minutes * 60.0).roundToLong().coerceAtLeast(0L)
}

private fun formatMinutesInput(seconds: Long): String {
    val minutes = seconds / 60.0
    return if (seconds % 60L == 0L) {
        (seconds / 60L).toString()
    } else {
        String.format(Locale.US, "%.1f", minutes)
    }
}

private data class MonthSelection(val year: Int, val month: Int) {
    fun previous(): MonthSelection = if (month == 1) {
        MonthSelection(year - 1, 12)
    } else {
        copy(month = month - 1)
    }

    fun next(): MonthSelection = if (month == 12) {
        MonthSelection(year + 1, 1)
    } else {
        copy(month = month + 1)
    }
}
