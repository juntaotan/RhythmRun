package com.compx551.rhythmrun.processing

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading
import com.compx551.rhythmrun.processing.processor.AnalyzingHandler
import com.compx551.rhythmrun.processing.processor.NormalizingHandler
import com.compx551.rhythmrun.processing.processor.PersistenceHandler
import com.compx551.rhythmrun.processing.processor.RawReading
import com.compx551.rhythmrun.processing.processor.SmoothingHandler
import com.compx551.rhythmrun.processing.processor.ValidationHandler
import kotlinx.coroutines.flow.MutableStateFlow

/** Processes one exercise event from raw sensor values to a persisted reading. */
class RhythmProcessor {
    val processedReadings = MutableStateFlow<List<ProcessedReading>>(emptyList())

    private val validationHandler = ValidationHandler()
    private val normalizingHandler = NormalizingHandler()
    private val smoothingHandler = SmoothingHandler()
    private val analyzingHandler = AnalyzingHandler()
    private val persistenceHandler = PersistenceHandler(processedReadings)

    fun process(
        readings: List<RawReading>,
        baseline: EfficiencyBaseline,
    ) {
        val validatedReadings = validationHandler.validate(readings)
        val normalizedReading = normalizingHandler.normalize(validatedReadings)
        val smoothedReading = smoothingHandler.smooth(normalizedReading)
        val analyzedReading = analyzingHandler.analyze(smoothedReading, baseline)
        persistenceHandler.persist(analyzedReading)
    }
}
