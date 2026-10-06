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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.compx551.rhythmrun.ui.common.formatClockDuration
import com.compx551.rhythmrun.ui.common.formatOneDecimal
import com.compx551.rhythmrun.ui.common.label
import java.util.Locale

@Composable
fun LiveRunContent(
    state: LiveRunUiState,
    plannedStages: List<StagePlanUiState>,
    onPauseResumeClick: () -> Unit,
    onFinishRunClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "LIVE SESSION",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = syncStatusText(state.syncStatus),
                    color = syncStatusColor(state.syncStatus),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            LiveRunMapCard(
                routePoints = state.routePoints,
                currentLocation = state.currentLocation,
                modifier = Modifier.fillMaxWidth(),
            )
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
                        text = "FOUR-STAGE PLAN",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        plannedStages.sortedBy { it.stage.ordinal }.forEachIndexed { index, stage ->
                            val active = index + 1 == state.currentStageNumber
                            Column(modifier = Modifier.weight(1f)) {
                                LinearProgressIndicator(
                                    progress = {
                                        when {
                                            index + 1 < state.currentStageNumber -> 1f
                                            active -> state.stageProgress
                                            else -> 0f
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = stage.stage.label,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    text = "${stage.durationMinutesInput} min",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor =
                        MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "STAGE ${state.currentStageNumber} " +
                                "OF ${state.totalStageCount}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )

                    Text(
                        text = state.currentStage.label,
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    LinearProgressIndicator(
                        progress = { state.stageProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Elapsed: " +
                                    formatClockDuration(
                                        state.stageElapsedSeconds,
                                    ),
                        )
                        Text(
                            text = "Remaining: " +
                                    formatClockDuration(
                                        state.stageRemainingSeconds,
                                    ),
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = "HEART RATE",
                    value = state.heartRateBpm?.toString() ?: "--",
                    unit = "bpm",
                    modifier = Modifier.weight(1f),
                )

                MetricCard(
                    label = "CADENCE",
                    value = state.cadenceSpm?.toString() ?: "--",
                    unit = "spm",
                    supportingText = state.targetCadenceSpm?.let {
                        "Target $it spm"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            MetricCard(
                label = "TOTAL STEPS",
                value = state.totalSteps.toString(),
                unit = "steps",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = "SPEED",
                    value = state.speedKilometresPerHour
                        ?.let(::formatOneDecimal) ?: "--",
                    unit = "km/h",
                    supportingText = state.efficiency?.let {
                        "Efficiency ${String.format(Locale.US, "%.3f", it)}x"
                    } ?: "Efficiency unavailable",
                    modifier = Modifier.weight(1f),
                )

                MetricCard(
                    label = "DISTANCE",
                    value = formatOneDecimal(
                        state.distanceMetres / 1000.0,
                    ),
                    unit = "km",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            MetricCard(
                label = "ACCELERATION",
                value = state.accelerationMagnitude
                    ?.let(::formatOneDecimal) ?: "--",
                unit = "m/s²",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "Total elapsed: " +
                        formatClockDuration(state.totalElapsedSeconds),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onPauseResumeClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        text = if (state.isPaused) {
                            "Resume"
                        } else {
                            "Pause"
                        },
                    )
                }

                Button(
                    onClick = onFinishRunClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(text = "Finish Run")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
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
private fun syncStatusColor(
    status: LiveRunSyncStatus,
): Color {
    return when (status) {
        LiveRunSyncStatus.WaitingForWatch ->
            MaterialTheme.colorScheme.secondary

        LiveRunSyncStatus.ReceivingData ->
            MaterialTheme.colorScheme.primary

        LiveRunSyncStatus.ConnectionLost ->
            MaterialTheme.colorScheme.error
    }
}

private fun syncStatusText(
    status: LiveRunSyncStatus,
): String {
    return when (status) {
        LiveRunSyncStatus.WaitingForWatch ->
            "Waiting for Watch data"

        LiveRunSyncStatus.ReceivingData ->
            "Watch data synced"

        LiveRunSyncStatus.ConnectionLost ->
            "Watch data interrupted"
    }
}
