package com.compx551.rhythmrun.processing.processor

import com.compx551.rhythmrun.processing.model.EfficiencyBaseline
import com.compx551.rhythmrun.processing.model.ProcessedReading
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyzingHandlerTest {
    @Test
    fun calculatesEfficiencyAgainstBaseline() {
        val reading = ProcessedReading(1_000, 100.0, 1.0, 4.0, 3.0)

        val analyzed = AnalyzingHandler().analyze(
            reading,
            EfficiencyBaseline(2.0, 100.0, 1),
        )

        assertEquals(2.0, analyzed.efficiency!!, 1e-9)
    }
}
