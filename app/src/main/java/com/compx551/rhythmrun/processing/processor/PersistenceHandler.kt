package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.repository.RunningStore

/** Final chain step: save the session row and calculated score rows in one transaction. */
class PersistenceHandler(private val store: RunningStore) : ProcessingHandler() {
    override suspend fun processSuspending(request: ProcessingRequest): Boolean {
        val session = requireNotNull(request.sessionDetails) { "Session details are required for persistence" }
        require(session.sessionId == request.sessionId) { "Session IDs must match" }
        val result = requireNotNull(request.analysisResult) {
            "Analysis must complete before persistence"
        }
        store.persistBatch(session, result.ratios)
        return true
    }
}
