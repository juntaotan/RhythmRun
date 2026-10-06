package com.compx551.watchos.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStepCounterTest {
    @Test fun cumulativeCountContinuesAcrossPauseAndMissingUpdates() {
        val counter = SessionStepCounter()
        counter.updateTotal(100)
        // The restarted exercise reports its own total from zero.
        counter.reset(retainedSteps = counter.total)
        assertEquals(100L, counter.total)
        counter.updateTotal(20)
        assertEquals(120L, counter.total)
    }

    @Test fun intervalFallbackContinuesFromTheStoredSessionTotal() {
        val counter = SessionStepCounter()
        counter.reset(retainedSteps = 100)
        counter.addInterval(20, 1_000)
        assertEquals(120L, counter.total)
    }

    @Test fun assigningANewSessionDoesNotIncludeThePreviousSessionSteps() {
        val counter = SessionStepCounter()
        counter.updateTotal(100)
        counter.rebase()
        counter.updateTotal(120)
        assertEquals(20L, counter.total)
        counter.rebase(counter.total + 50)
        counter.updateTotal(130)
        assertEquals(80L, counter.total)
    }

    @Test fun duplicateAndLateTotalsDoNotChangeTheCount() {
        val counter = SessionStepCounter()
        listOf(100L, 100L, 80L, 120L).forEach(counter::updateTotal)
        assertEquals(120L, counter.total)
    }

    @Test fun intervalFallbackCountsEachIntervalOnlyOnce() {
        val counter = SessionStepCounter()
        counter.addInterval(100, 1_000)
        counter.addInterval(100, 1_000)
        counter.addInterval(50, 900)
        counter.addInterval(20, 2_000)
        assertEquals(120L, counter.total)
    }

    @Test fun aMissingTotalDoesNotCountOverlappingIntervalsAgain() {
        val counter = SessionStepCounter()
        counter.updateTotal(100)
        counter.addInterval(100, 1_000)
        assertEquals(100L, counter.total)
        counter.updateTotal(120)
        assertEquals(120L, counter.total)
    }

    @Test fun aNewExerciseResetsBothTotalAndIntervalWatermark() {
        val counter = SessionStepCounter()
        counter.addInterval(100, 2_000)
        counter.reset()
        assertEquals(0L, counter.total)
        counter.addInterval(20, 1_000)
        assertEquals(20L, counter.total)
    }
}
