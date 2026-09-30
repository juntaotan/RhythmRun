package com.compx551.rhythmrun.processing.repository

import com.compx551.rhythmrun.processing.processor.HistoricalRunAverage
import com.compx551.rhythmrun.processing.processor.SpeedHeartRateRatio

/** Operations needed by the processing chain; Room is the production implementation. */
interface RunningStore {
    suspend fun getHistoricalAverages(beforeStartTime: Long): List<HistoricalRunAverage>
    suspend fun persistBatch(session: RunningDetailsEntity, ratios: List<SpeedHeartRateRatio>)
}
