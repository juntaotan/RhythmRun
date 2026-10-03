package com.compx551.rhythmrun.data

import com.compx551.rhythmrun.data.repository.SampleRunData
import com.compx551.rhythmrun.domain.model.RunStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SampleRunDataTest {

    @Test
    fun sampleRecords_containThreeRealisticRunsWithFullStages() {
        val calendar = Calendar.getInstance()
        val records = SampleRunData.createSampleRecords(calendar)

        assertEquals(3, records.size)

        records.forEach { record ->
            assertTrue(record.sessionId.isNotBlank())
            assertTrue(record.title.isNotBlank())
            assertTrue(record.totalDurationSeconds > 0)
            assertTrue((record.distanceMetres ?: 0.0) > 0.0)
            assertTrue((record.averageCadenceSpm ?: 0) > 0)
            assertTrue((record.averageHeartRateBpm ?: 0) > 0)

            // Verify all 4 run stages are present
            assertEquals(4, record.stages.size)
            assertEquals(RunStage.WarmUp, record.stages[0].stage)
            assertEquals(RunStage.Running, record.stages[1].stage)
            assertEquals(RunStage.SlowDown, record.stages[2].stage)
            assertEquals(RunStage.Recovery, record.stages[3].stage)

            // Verify date formatting YYYY-MM-DD
            assertTrue(record.startLocalDate.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
            assertTrue(record.startLocalTime.matches(Regex("""\d{2}:\d{2}""")))
        }
    }
}
