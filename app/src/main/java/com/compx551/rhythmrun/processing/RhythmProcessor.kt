package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.model.ProcessedReading
import com.compx551.rhythmrun.processing.model.ProcessingRequest
import com.compx551.rhythmrun.processing.processor.AnalyzingHandler
import com.compx551.rhythmrun.processing.processor.NormalizingHandler
import com.compx551.rhythmrun.processing.processor.PersistenceHandler
import com.compx551.rhythmrun.processing.processor.ValidationHandler
import kotlinx.coroutines.flow.MutableStateFlow

/** Runs the processing workflow through validation and normalization only. */
class RhythmProcessor {
    /** All validated and normalized sensor snapshots for this exercise event. */
    val processedReadings = MutableStateFlow<List<ProcessedReading>>(emptyList())

    private val validationHandler = ValidationHandler()
    private val normalizingHandler = NormalizingHandler()
    private val analyzingHandler = AnalyzingHandler()
    private val persistenceHandler = PersistenceHandler(processedReadings)

    init {
        validationHandler
            .setNext(normalizingHandler)
            .setNext(analyzingHandler)
            .setNext(persistenceHandler)
    }

    /** Validates and normalizes one request. */
    suspend fun rhythmProcessor(request: ProcessingRequest) {
        validationHandler.handle(request)
    }
}
