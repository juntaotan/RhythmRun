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

/**
 * Data Processing
 *
 * The processor receives heart rate, acceleration, velocity, steps per second, and optional
 * location data for one exercise event.
 *
 * Sensor readings are processed as follows:
 * - 1st. Validate every raw value in [ValidationHandler]. An invalid value is replaced with zero
 *        without discarding the other valid values from the same exercise event.
 * - 2nd. Normalize the validated values and combine them into one [ProcessedReading] in
 *        [NormalizingHandler]. Acceleration is represented in m/s² and velocity in m/s.
 * - 3rd. Smooth every metric in [SmoothingHandler] using a weighted moving average over the five
 *        latest samples, with weights 1, 2, 3, 4, and 5 from oldest to newest.
 * - 4th. Calculate the efficiency index in [AnalyzingHandler]:
 *        (current speed / current heart rate) / (baseline speed / baseline heart rate).
 * - 5th. Publish the completed [ProcessedReading] in [PersistenceHandler]. This is the only stage
 *        that updates [processedReadings], so observers receive one complete row per event.
 *
 * Data flows directly between the processing methods; no mutable processing-request object or
 * generic handler chain is used.
 */
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
