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
import com.compx551.rhythmrun.domain.model.StageChangeRecord
import com.compx551.rhythmrun.domain.repository.RunRepository
import com.compx551.rhythmrun.location.PhoneLocationService
import com.compx551.rhythmrun.processing.model.ProcessedLocation
import com.compx551.rhythmrun.ui.dashboard.DashboardUiState
import com.compx551.rhythmrun.ui.dashboard.WatchConnectionStatus
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
import com.compx551.rhythmrun.processing.RunSummaryCalculator
import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
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
    private val appContext: Context? = null,
) : ViewModel() {
    private var liveRunProcessor: LiveRunProcessor? = null
    private var timerJob: Job? = null
    private var processedReadingsJob: Job? = null
    private var activeRunStartedAtMillis: Long? = null
    private var timelineJob: Job? = null
    private var lastTimelineSequence = 0L
    private var lastLiveReadingAtMillis: Long? = null
    private var guidanceStarted = false
    private var restoringRun = false
    private var phoneGpsSession = false
    private val initialCalendar = Calendar.getInstance(timeZone)
    private val selectedMonth = MutableStateFlow(
        MonthSelection(
            year = initialCalendar.get(Calendar.YEAR),
            month = initialCalendar.get(Calendar.MONTH) + 1,
        ),
    )
    private val selectedDay = MutableStateFlow<Int?>(null)
    private val watchConnectionStatus = MutableStateFlow(WatchConnectionStatus.Disconnected)

    var runSessionState by mutableStateOf(createInitialRunSessionUiState())
        private set

    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.records,
        repository.activePlan,
        watchConnectionStatus,
    ) { records, activePlan, connectionStatus ->
        buildDashboardUiState(records, activePlan != null, connectionStatus)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = buildDashboardUiState(
                repository.records.value,
                repository.activePlan.value != null,
                watchConnectionStatus.value,
            ),
        )

    fun refreshWatchConnection() {
        sessionCommandClient?.connectedNodes()?.addOnCompleteListener { task ->
            watchConnectionStatus.value = if (task.isSuccessful && task.result.isNotEmpty()) {
                WatchConnectionStatus.Connected
            } else {
                WatchConnectionStatus.Disconnected
            }
        }
    }

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
            if (restoringRun || live.sessionId != reading.sessionId) return@launch
            if (phoneGpsSession && reading.dataType == "location") return@launch
            if (live.syncStatus != LiveRunSyncStatus.ReceivingData) {
                runSessionState = runSessionState.copy(
                    liveRun = live.copy(
                        syncStatus = LiveRunSyncStatus.ReceivingData,
                    ),
                )
            }
            if (liveRunProcessor == null) {
                startLiveProcessing()
            }
            lastLiveReadingAtMillis = nowMillis()
            liveRunProcessor?.onReading(reading)
        }
    }

    init {
        RhythmDataListenerService.readingListener = watchReadingListener
        PhoneLocationService.locationListener = ::onPhoneLocation
        viewModelScope.launch {
            repository.records.collect { records ->
                val summary = runSessionState.summary ?: return@collect
                records.firstOrNull { it.sessionId == summary.sessionId }?.let { updated ->
                    runSessionState = runSessionState.copy(
                        summary = updated.toSummaryUiState(runSessionState.summary?.routePoints.orEmpty()),
                    )
                }
            }
        }
    }

    fun startNewRun() {
        activeRunStartedAtMillis = null
        timelineJob = null
        guidanceStarted = false
        restoringRun = false
        phoneGpsSession = false
        runSessionState = createInitialRunSessionUiState()
    }

    fun resumeActiveRun() {
        val plan = repository.activePlan.value ?: return
        restoringRun = true
        guidanceStarted = false
        phoneGpsSession = plan.outdoorRouteEnabled
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
        viewModelScope.launch {
            val changes = repository.stageChanges(plan.sessionId).sortedBy { it.timestampEpochMillis }
            if (runSessionState.liveRun.sessionId != plan.sessionId) return@launch
            if (phoneGpsSession) {
                val fixes = repository.locationFixes(plan.sessionId)
                val route = fixes.mapNotNull { fix ->
                    if (fix.available && fix.latitude != null && fix.longitude != null) {
                        ProcessedLocation(fix.latitude, fix.longitude, fix.accuracyMetres ?: 0.0)
                    } else null
                }
                val distance = route.zipWithNext().sumOf { (a, b) ->
                    computeDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                }
                runSessionState = runSessionState.copy(liveRun = runSessionState.liveRun.copy(
                    routePoints = route,
                    currentLocation = route.lastOrNull(),
                    distanceMetres = distance,
                ))
            }
            guidanceStarted = changes.any { it.reason == "START" }
            changes.lastOrNull()?.let { last ->
                val now = nowMillis()
                val activeIntervals = changes.zipWithNext().mapNotNull { (start, end) ->
                    if (start.reason == "PAUSE" || start.reason == "STOP") null
                    else start.stage to (end.timestampEpochMillis - start.timestampEpochMillis).coerceAtLeast(0L)
                } + if (last.reason == "PAUSE" || last.reason == "STOP") emptyList()
                    else listOf(last.stage to (now - last.timestampEpochMillis).coerceAtLeast(0L))
                val stageIndex = stages.indexOfFirst { it.stage == last.stage }
                if (stageIndex >= 0 && runSessionState.liveRun.sessionId == plan.sessionId) {
                    val stage = stages[stageIndex]
                    runSessionState = runSessionState.copy(liveRun = runSessionState.liveRun.copy(
                        currentStage = stage.stage,
                        currentStageNumber = stageIndex + 1,
                        stageDurationSeconds = durationMinutesToSeconds(stage.durationMinutesInput),
                        targetCadenceSpm = stage.targetCadenceSpmInput?.toIntOrNull(),
                        totalElapsedSeconds = activeIntervals.sumOf { it.second } / 1_000,
                        stageElapsedSeconds = activeIntervals.filter { it.first == stage.stage }.sumOf { it.second } / 1_000,
                    ))
                }
            }
            restoringRun = false
            when (plan.state) {
                RunSessionState.Active -> sessionCommandClient?.sendResume(plan.sessionId)
                RunSessionState.Paused -> if (runSessionState.liveRun.isPaused) togglePause()
                RunSessionState.Planned -> {
                    repository.updateSessionState(plan.sessionId, RunSessionState.Active)
                    sessionCommandClient?.sendStart(plan.sessionId)
                }
                else -> Unit
            }
        }
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

    fun startRun(usePhoneGps: Boolean = false) {
        if (!runSessionState.isPlanValid) return
        guidanceStarted = false
        restoringRun = false
        phoneGpsSession = usePhoneGps && appContext != null
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
        if (phoneGpsSession) appContext?.let { PhoneLocationService.start(it, sessionId) }
        sessionCommandClient?.sendStart(sessionId)
        startLiveProcessing()
    }

    fun togglePause() {
        val paused = !runSessionState.liveRun.isPaused
        if (!paused) lastLiveReadingAtMillis = null
        runSessionState = runSessionState.copy(
            liveRun = runSessionState.liveRun.copy(
                isPaused = paused,
                syncStatus = if (paused) runSessionState.liveRun.syncStatus else LiveRunSyncStatus.WaitingForWatch,
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
                if (phoneGpsSession) appContext?.let { PhoneLocationService.pause(it, sessionId) }
                sessionCommandClient?.sendPause(sessionId)
            } else {
                if (phoneGpsSession) appContext?.let { PhoneLocationService.resume(it, sessionId) }
                sessionCommandClient?.sendResume(sessionId)
            }
            val wasStarted = guidanceStarted
            if (!paused) startGuidanceIfReady()
            if (wasStarted) recordTimeline(if (paused) "PAUSE" else "RESUME")
        }
    }

    fun finishRun() {
        recordTimeline("STOP")
        stopLiveProcessing()
        runSessionState.liveRun.sessionId?.let { sessionId ->
            if (phoneGpsSession) appContext?.let { PhoneLocationService.stop(it, sessionId) }
            sessionCommandClient?.sendStop(sessionId)
        }
        val record = createRunRecord(runSessionState)
        val route = runSessionState.liveRun.routePoints
        runSessionState = runSessionState.copy(
            mode = RunSessionUiMode.Summary,
            summary = record.toSummaryUiState(routePoints = route),
        )
        viewModelScope.launch {
            timelineJob?.join()
            val calculated = RunSummaryCalculator.update(
                record,
                repository.rawReadings(record.sessionId),
                repository.stageChanges(record.sessionId),
            )
            repository.upsert(calculated)
            runSessionState = runSessionState.copy(
                summary = calculated.toSummaryUiState(routePoints = route),
            )
            if (route.isNotEmpty() && !phoneGpsSession) {
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
        lastLiveReadingAtMillis = null
        val processor = LiveRunProcessor()
        liveRunProcessor = processor
        val beforeStart = activeRunStartedAtMillis ?: nowMillis()
        viewModelScope.launch {
            val baseline = EfficiencyBaseline.fromPreviousRuns(repository.previousRuns(beforeStart))
            if (liveRunProcessor === processor) processor.setBaseline(baseline)
        }

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
        PhoneLocationService.locationListener = ::onPhoneLocation
    }

    private fun onPhoneLocation(fix: LocationFixRecord) {
        val live = runSessionState.liveRun
        if (!phoneGpsSession || live.sessionId != fix.sessionId || live.isPaused ||
            runSessionState.mode != RunSessionUiMode.Live
        ) return
        val latitude = fix.latitude ?: return
        val longitude = fix.longitude ?: return
        val point = ProcessedLocation(latitude, longitude, fix.accuracyMetres ?: 0.0)
        val previous = live.routePoints.lastOrNull()
        if (previous?.latitude == latitude && previous.longitude == longitude) return
        val distance = previous?.let {
            computeDistanceMeters(it.latitude, it.longitude, latitude, longitude)
        } ?: 0.0
        runSessionState = runSessionState.copy(liveRun = live.copy(
            currentLocation = point,
            routePoints = live.routePoints + point,
            distanceMetres = live.distanceMetres + distance,
        ))
    }

    private fun recordTimeline(reason: String) {
        val live = runSessionState.liveRun
        val sessionId = live.sessionId ?: return
        val timestamp = nowMillis()
        val sequence = maxOf(timestamp, lastTimelineSequence + 1)
        lastTimelineSequence = sequence
        val previous = timelineJob
        timelineJob = viewModelScope.launch {
            previous?.join()
            repository.persistBatch(RunDataBatch(stageChanges = listOf(
                StageChangeRecord(sessionId, sequence, timestamp, live.currentStage, reason),
            )))
        }
    }

    private fun startGuidanceIfReady() {
        if (!guidanceStarted && !runSessionState.liveRun.isPaused && runSessionState.liveRun.heartRateBpm != null) {
            guidanceStarted = true
            recordTimeline("START")
        }
    }

    private fun advanceTimer() {
        var live = runSessionState.liveRun
        if (live.isPaused) return
        if (live.syncStatus == LiveRunSyncStatus.ReceivingData &&
            lastLiveReadingAtMillis?.let { nowMillis() - it > 15_000L } == true
        ) {
            live = live.copy(
                syncStatus = LiveRunSyncStatus.ConnectionLost,
                cadenceSpm = live.cadenceSpm?.let { 0 },
                speedKilometresPerHour = live.speedKilometresPerHour?.let { 0.0 },
            )
        }
        if (!guidanceStarted) {
            if (live != runSessionState.liveRun) runSessionState = runSessionState.copy(liveRun = live)
            return
        }

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
                recordTimeline("STAGE")
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
                cadenceSpm = cadence.takeIf { it > 0 || currentLive.cadenceSpm != null },
                speedKilometresPerHour = speedKmh.takeIf { it > 0 || currentLive.speedKilometresPerHour != null },
                distanceMetres = currentLive.distanceMetres + addedDistance,
                accelerationMagnitude = reading.accelerationPerSecond.takeIf { it > 0.0 } ?: currentLive.accelerationMagnitude,
                efficiency = reading.efficiency,
                currentLocation = newLocation ?: currentLive.currentLocation,
                routePoints = newRoute,
                syncStatus = LiveRunSyncStatus.ReceivingData,
            ),
        )
        startGuidanceIfReady()
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
        PhoneLocationService.locationListener = null
        liveRunProcessor = null
        lastLiveReadingAtMillis = null
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
            outdoorRouteEnabled = phoneGpsSession,
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
        private val appContext: Context? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RhythmRunViewModel::class.java))
            return RhythmRunViewModel(repository, sessionCommandClient, appContext = appContext) as T
        }

        companion object {
            fun production(context: Context): Factory = Factory(
                repository = RoomRunRepository(PhoneRoomDatabase.getInstance(context)),
                sessionCommandClient = SessionCommandClient(context),
                appContext = context.applicationContext,
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
