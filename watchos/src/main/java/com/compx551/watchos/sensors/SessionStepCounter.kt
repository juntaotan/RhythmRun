package com.compx551.watchos.sensors

/** Counts one exercise, retaining the last total across missing or repeated updates. */
internal class SessionStepCounter {
    var total: Long = 0L
        private set
    private var lastIntervalEndNanos = -1L
    private var hasCumulativeTotal = false
    private var previousSegmentsTotal = 0L
    private var lastRawTotal = 0L

    fun reset(retainedSteps: Long = 0L) {
        previousSegmentsTotal = retainedSteps.coerceAtLeast(0L)
        total = previousSegmentsTotal
        lastRawTotal = 0L
        lastIntervalEndNanos = -1L
        hasCumulativeTotal = false
    }

    fun rebase(retainedSteps: Long = 0L) {
        total = retainedSteps.coerceAtLeast(0L)
        previousSegmentsTotal = total - lastRawTotal
    }

    fun updateTotal(value: Long) {
        hasCumulativeTotal = true
        lastRawTotal = maxOf(lastRawTotal, value)
        total = maxOf(total, previousSegmentsTotal + lastRawTotal)
    }

    fun addInterval(value: Long, endNanos: Long) {
        if (hasCumulativeTotal || endNanos <= lastIntervalEndNanos) return
        lastIntervalEndNanos = endNanos
        total += value.coerceAtLeast(0L)
    }
}
