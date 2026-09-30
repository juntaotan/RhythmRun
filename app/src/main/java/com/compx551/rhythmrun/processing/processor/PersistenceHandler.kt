package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.ProcessedReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Publishes one fully processed reading to the exercise event list. */
class PersistenceHandler(
    private val processedReadings: MutableStateFlow<List<ProcessedReading>>,
) {
    fun persist(reading: ProcessedReading) {
        processedReadings.update { currentReadings -> currentReadings + reading }
    }
}
