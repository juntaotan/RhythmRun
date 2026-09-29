package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.processor.AnalyzingHandler
import com.compx551.rhythmrun.processing.processor.ProcessingRequest
import com.compx551.rhythmrun.processing.processor.SpeedHeartRateRatio
import com.compx551.rhythmrun.processing.repository.RunningRepository

/** Connects historical Room records to efficiency analysis and persists the resulting timeline. */
class RhythmProcessor(
    private val repository: RunningRepository,
    private val analyzingHandler: AnalyzingHandler = AnalyzingHandler(),
) {
    /** Expects normalization and smoothing to have populated [ProcessingRequest.smoothedReadings]. */
    suspend fun calculateAndStoreEfficiency(request: ProcessingRequest): List<SpeedHeartRateRatio> {
        val session = requireNotNull(repository.getSession(request.sessionId)) {
            "Session ${request.sessionId} must be saved before efficiency analysis"
        }
        request.efficiencyBaseline = analyzingHandler.calculateBaseline(
            repository.getHistoricalAverages(session.startTime),
        )
        analyzingHandler.handle(request)
        repository.saveRatios(request.sessionId, request.speedHeartRateRatios)
        return request.speedHeartRateRatios
    }
}
