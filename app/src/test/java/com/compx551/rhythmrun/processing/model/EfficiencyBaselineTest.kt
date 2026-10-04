package com.compx551.rhythmrun.processing.model

import com.compx551.rhythmrun.data.repository.SampleRunData
import com.compx551.rhythmrun.domain.model.RunCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EfficiencyBaselineTest {
    @Test
    fun averagesComparableCompletedRuns() {
        val history = SampleRunData.createSampleRecords()
        val baseline = EfficiencyBaseline.fromPreviousRuns(history)

        assertEquals(3, baseline?.historyCount)
        assertEquals(2.1, baseline!!.averageSpeedMetersPerSecond, 1e-9)
        assertEquals(152.0, baseline.averageHeartRateBpm, 1e-9)
    }

    @Test
    fun hasNoBaselineWithoutComparableHistory() {
        val run = SampleRunData.createSampleRecords().first()
        assertNull(EfficiencyBaseline.fromPreviousRuns(emptyList()))
        assertNull(EfficiencyBaseline.fromPreviousRuns(listOf(run.copy(completion = RunCompletion.StoppedEarly))))
        assertNull(EfficiencyBaseline.fromPreviousRuns(listOf(run.copy(dataCoveragePercent = 50))))
    }
}
