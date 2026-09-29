package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.processor.AnalyzingHandler
import com.compx551.rhythmrun.processing.processor.GpsProcessingHandler
import com.compx551.rhythmrun.processing.processor.PersistenceHandler
import com.compx551.rhythmrun.processing.processor.ProcessingRequest
import com.compx551.rhythmrun.processing.processor.SpeedHeartRateRatio
import com.compx551.rhythmrun.processing.repository.RunningStore

/** Connects GPS processing and historical efficiency analysis to persistence in the chain. */
class RhythmProcessor(
    private val store: RunningStore,
    private val analyzingHandler: AnalyzingHandler = AnalyzingHandler(),
    private val gpsProcessingHandler: GpsProcessingHandler = GpsProcessingHandler(),
) {
    init {
        gpsProcessingHandler
            .setNext(analyzingHandler)
            .setNext(PersistenceHandler(store))
    }

    /**
     * Processes optional raw GPS readings, then analyzes and stores the already-smoothed sensor
     * readings. GPS output is returned through [ProcessingRequest.gpsResult].
     */
    suspend fun calculateAndStoreEfficiency(request: ProcessingRequest): List<SpeedHeartRateRatio> {
        val session = requireNotNull(request.sessionDetails) { "Session details are required for persistence" }
        require(session.sessionId == request.sessionId) { "Session IDs must match" }
        request.efficiencyBaseline = analyzingHandler.calculateBaseline(
            store.getHistoricalAverages(session.startTime),
        )
        gpsProcessingHandler.handle(request)
        return request.speedHeartRateRatios
    }
}
