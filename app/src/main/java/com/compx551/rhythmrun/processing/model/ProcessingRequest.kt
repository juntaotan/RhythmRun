package com.compx551.rhythmrun.processing.model

import com.compx551.rhythmrun.processing.processor.AnalysisResult
import com.compx551.rhythmrun.processing.processor.GpsProcessingResult
import com.compx551.rhythmrun.processing.processor.GpsProcessingState
import com.compx551.rhythmrun.processing.processor.NormalizedReading
import com.compx551.rhythmrun.processing.processor.RawReading
import com.compx551.rhythmrun.processing.processor.SpeedHeartRateRatio
import com.compx551.rhythmrun.processing.repository.RunningDetailsEntity

data class ProcessingRequest(
    val sessionId: String,
    val readings: List<RawReading>,
    /** Session metadata/summary supplied by the session owner for persistence. */
    val sessionDetails: RunningDetailsEntity? = null,
    var normalizedReadings: List<NormalizedReading> = emptyList(),
    /** Recent unsmoothed readings from the preceding batch of this session. */
    val smoothingHistory: List<NormalizedReading> = emptyList(),
    var smoothedReadings: List<NormalizedReading> = emptyList(),
    var nextSmoothingHistory: List<NormalizedReading> = emptyList(),
    /** Last smoothed HR from the preceding batch, for pairing with the next speed reading. */
    val previousSmoothedHeartRate: NormalizedReading.HeartRate? = null,
    var latestSmoothedHeartRate: NormalizedReading.HeartRate? = null,
    /** GPS context produced by the preceding batch of this session. */
    val gpsState: GpsProcessingState = GpsProcessingState(),
    /** Accepted and smoothed route fixes, plus distance added by this batch. */
    var gpsResult: GpsProcessingResult = GpsProcessingResult(),
    var nextGpsState: GpsProcessingState = GpsProcessingState(),
    /** Derived from completed sessions before this session; null when none exist. */
    var efficiencyBaseline: EfficiencyBaseline? = null,
    var speedHeartRateRatios: List<SpeedHeartRateRatio> = emptyList(),
    /** Produced by AnalyzingHandler and consumed by PersistenceHandler. */
    var analysisResult: AnalysisResult? = null,
)
