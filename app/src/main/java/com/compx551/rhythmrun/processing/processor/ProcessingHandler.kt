package com.compx551.rhythmrun.processing.processor

/**
 * Data Processing
 *
 * The processor receives heart rate, acceleration, velocity and cumulative step counts from the
 * watch. Cadence is derived from consecutive step-counter readings.
 *
 * The collected data is processed as follows:
 * - 1st. validate the data received from the watch in {@code ValidationHandler}
 * - 2nd. normalise the validated data in {@code NormalizingHandler}.
 * - 3rd. Smooth the normalised data using a moving average in {@code SmoothingHandler}.
 * - 4th. Calculate running efficiency using the following formula:
 *        {@code (Speed_t / HR_t) / (Speed_baseline / HR_baseline)}.
 * - 5th. Persist the processed data to storage.
 *
 */
abstract class ProcessingHandler {

    private var next: ProcessingHandler? = null

    fun setNext(handler: ProcessingHandler): ProcessingHandler {
        next = handler
        return handler
    }

    fun handle(request: ProcessingRequest) {
        if (process(request)) {
            next?.handle(request)
        }
    }

    /** Return true to continue the chain; false to stop it. */
    protected abstract fun process(request: ProcessingRequest): Boolean
}
