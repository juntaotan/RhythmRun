package com.compx551.rhythmrun.ui.runsession

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.compx551.rhythmrun.domain.model.CadenceSource
import com.compx551.rhythmrun.domain.model.RunCompletion
import com.compx551.rhythmrun.ui.common.accentColor
import com.compx551.rhythmrun.ui.common.formatClockDuration
import com.compx551.rhythmrun.ui.common.formatOneDecimal
import com.compx551.rhythmrun.ui.common.numberedLabel

@Composable
fun RunSummaryContent(
    state: RunSummaryUiState,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = completionLabel(state.completionStatus),
                    color = completionColor(state.completionStatus),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Run summary",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Session ${state.sessionId}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryMetricCard(
                    label = "DURATION",
                    value = formatClockDuration(state.totalDurationSeconds),
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                    label = "DISTANCE",
                    value = state.distanceMetres?.let {
                        "${formatOneDecimal(it / 1000.0)} km"
                    } ?: "Unavailable",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryMetricCard(
                    label = "AVG HEART RATE",
                    value = state.averageHeartRateBpm?.let { "$it bpm" }
                        ?: "Unavailable",
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                    label = "AVG CADENCE",
                    value = state.averageCadenceSpm?.let { "$it spm" }
                        ?: "Unavailable",
                    supportingText = cadenceSourceLabel(state.cadenceSource),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "PERFORMANCE",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    SummaryLine("Target adherence", percentOrUnavailable(state.targetAdherencePercent))
                    SummaryLine("Rhythm stability", percentOrUnavailable(state.rhythmStabilityPercent))
                    SummaryLine("Cue coverage", percentOrUnavailable(state.cueCoveragePercent))
                    SummaryLine("Data coverage", percentOrUnavailable(state.dataCoveragePercent))
                    SummaryLine(
                        "Movement magnitude",
                        state.averageMovementMagnitude?.let { "${formatOneDecimal(it)} m/s²" }
                            ?: "Unavailable",
                    )
                    SummaryLine(
                        "Heart-rate response",
                        state.heartRateResponseBpm?.let { signedBpm(it) }
                            ?: "Unavailable",
                    )
                }
            }
        }

        item {
            Text(
                text = "Four-stage timeline",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        items(
            items = state.stages,
            key = { it.stage },
        ) { stage ->
            StageSummaryCard(stage)
        }

        item {
            DataQualityCard(state)
        }

        item {
            Button(
                onClick = onDoneClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(text = "Done")
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
            supportingText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value)
    }
}

@Composable
private fun StageSummaryCard(stage: StageSummaryUiState) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, stage.stage.accentColor()),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stage.stage.numberedLabel,
                color = stage.stage.accentColor(),
                style = MaterialTheme.typography.titleMedium,
            )
            SummaryLine("Planned", formatClockDuration(stage.plannedDurationSeconds))
            SummaryLine("Actual", formatClockDuration(stage.actualDurationSeconds))
            SummaryLine(
                "Target cadence",
                stage.targetCadenceSpm?.let { "$it spm" } ?: "Not guided",
            )
            SummaryLine(
                "Average cadence",
                stage.averageCadenceSpm?.let { "$it spm" } ?: "Unavailable",
            )
            SummaryLine(
                "Average heart rate",
                stage.averageHeartRateBpm?.let { "$it bpm" } ?: "Unavailable",
            )
        }
    }
}

@Composable
private fun DataQualityCard(state: RunSummaryUiState) {
    val borderColor = if (state.hasIncompleteData) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (state.hasIncompleteData) "INCOMPLETE DATA" else "DATA COMPLETE",
                color = borderColor,
                style = MaterialTheme.typography.labelLarge,
            )
            if (state.incompleteDataMessages.isEmpty()) {
                Text(text = "All expected data is available for this summary.")
            } else {
                state.incompleteDataMessages.forEach { message ->
                    Text(text = "• $message")
                }
            }
        }
    }
}

@Composable
private fun completionColor(status: RunCompletion): Color = when (status) {
    RunCompletion.Completed -> MaterialTheme.colorScheme.primary
    RunCompletion.StoppedEarly -> MaterialTheme.colorScheme.error
}

private fun completionLabel(status: RunCompletion): String = when (status) {
    RunCompletion.Completed -> "SESSION COMPLETED"
    RunCompletion.StoppedEarly -> "SESSION STOPPED EARLY"
}

private fun cadenceSourceLabel(source: CadenceSource): String = when (source) {
    CadenceSource.DirectStepRate -> "Direct step rate"
    CadenceSource.DerivedFromStepCount -> "Derived from step count"
    CadenceSource.EstimatedWristRhythm -> "Estimated wrist rhythm"
    CadenceSource.Unavailable -> "Cadence source unavailable"
}

private fun percentOrUnavailable(value: Int?): String = value?.let { "$it%" } ?: "Unavailable"

private fun signedBpm(value: Int): String = if (value >= 0) "+$value bpm" else "$value bpm"
