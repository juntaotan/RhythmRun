package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.processor.AnalyzingHandler
import com.compx551.rhythmrun.processing.processor.PersistenceHandler
import com.compx551.rhythmrun.processing.processor.ProcessingRequest
import com.compx551.rhythmrun.processing.processor.SpeedHeartRateRatio
import com.compx551.rhythmrun.processing.repository.RunningStore

/** Connects historical records to efficiency analysis, followed by persistence in the chain. */
class RhythmProcessor(
    private val store: RunningStore,
    private val analyzingHandler: AnalyzingHandler = AnalyzingHandler(),
) {
    init {
        analyzingHandler.setNext(PersistenceHandler(store))
    }

    /** Expects normalization and smoothing to have populated [ProcessingRequest.smoothedReadings]. */
    suspend fun calculateAndStoreEfficiency(request: ProcessingRequest): List<SpeedHeartRateRatio> {
        val session = requireNotNull(request.sessionDetails) { "Session details are required for persistence" }
        require(session.sessionId == request.sessionId) { "Session IDs must match" }
        request.efficiencyBaseline = analyzingHandler.calculateBaseline(
            store.getHistoricalAverages(session.startTime),
        )
        analyzingHandler.handle(request)
        return request.speedHeartRateRatios
    }
}
