package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.model.ProcessedReading
import com.compx551.rhythmrun.processing.model.ProcessingRequest
import com.compx551.rhythmrun.processing.processor.NormalizingHandler
import com.compx551.rhythmrun.processing.processor.ValidationHandler

/** Runs the processing workflow through validation and normalization only. */
class RhythmProcessor {
    private val recordedReadings = mutableListOf<ProcessedReading>()

    /** All validated and normalized sensor snapshots, in processing order. */
    val processedReadings: List<ProcessedReading>
        get() = recordedReadings.toList()

    private val validationHandler = ValidationHandler()
    private val normalizingHandler = NormalizingHandler(recordedReadings)

    init {
        validationHandler.setNext(normalizingHandler)
    }

    /** Validates and normalizes one request, then returns all readings recorded so far. */
    suspend fun rhythmProcessor(request: ProcessingRequest): List<ProcessedReading> {
        validationHandler.handle(request)
        return processedReadings
    }
}
