package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Stores a fully normalized and analyzed reading in the exercise event list. */
class PersistenceHandler(
    private val processedReadings: MutableStateFlow<List<ProcessedReading>>,
) : ProcessingHandler() {

    override fun process(request: ProcessingRequest): Boolean {
        val reading = requireNotNull(request.processedReading) {
            "Analysis must complete before persistence"
        }
        processedReadings.update { currentReadings -> currentReadings + reading }
        return true
    }
}
