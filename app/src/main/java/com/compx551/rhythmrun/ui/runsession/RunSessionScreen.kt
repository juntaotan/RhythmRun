package com.compx551.rhythmrun.ui.runsession

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.compx551.rhythmrun.ui.theme.RhythmRunTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSessionScreen(
    state: RunSessionUiState,
    onDurationChange: (RunStageType, String) -> Unit,
    onCadenceChange: (RunStageType, String) -> Unit,
    onGuidanceEnabledChange: (Boolean) -> Unit,
    onStartRunClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Run Session")
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (state.mode) {
            RunSessionUiMode.Plan -> RunPlanContent(
                state = state,
                onDurationChange = onDurationChange,
                onCadenceChange = onCadenceChange,
                onGuidanceEnabledChange = onGuidanceEnabledChange,
                onStartRunClick = onStartRunClick,
                modifier = Modifier.padding(innerPadding),
            )

            RunSessionUiMode.Live -> FutureModeContent(
                title = "Live run",
                message = "Live Watch data will appear here after integration.",
                modifier = Modifier.padding(innerPadding),
            )

            RunSessionUiMode.Summary -> FutureModeContent(
                title = "Run summary",
                message = "The completed session summary will appear here.",
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun RunPlanContent(
    state: RunSessionUiState,
    onDurationChange: (RunStageType, String) -> Unit,
    onCadenceChange: (RunStageType, String) -> Unit,
    onGuidanceEnabledChange: (Boolean) -> Unit,
    onStartRunClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "STAGE PLAN",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Configure four running stages",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Set duration and target step cadence before starting.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = state.totalDurationMinutes?.let { totalMinutes ->
                        "Total planned duration: $totalMinutes min"
                    } ?: "Complete all stage durations",
                    color = if (state.totalDurationMinutes == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        items(
            items = state.stages,
            key = { stage -> stage.stage },
        ) { stage ->
            StagePlanCard(
                state = stage,
                onDurationChange = { value ->
                    onDurationChange(stage.stage, value)
                },
                onCadenceChange = { value ->
                    onCadenceChange(stage.stage, value)
                },
            )
        }

        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Vibration guidance",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "The Watch provides cadence cues during guided stages.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Switch(
                        checked = state.guidanceEnabled,
                        onCheckedChange = onGuidanceEnabledChange,
                    )
                }
            }
        }

        item {
            Button(
                onClick = onStartRunClick,
                enabled = state.isPlanValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(text = "Start Run")
            }
        }
    }
}

@Composable
private fun StagePlanCard(
    state: StagePlanUiState,
    onDurationChange: (String) -> Unit,
    onCadenceChange: (String) -> Unit,
) {
    val accentColor = stageAccentColor(state.stage)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = accentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stageTitle(state.stage),
                    color = accentColor,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stageDescription(state.stage),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val showDurationError =
                    state.durationMinutesInput.isNotEmpty() && !state.hasValidDuration

                OutlinedTextField(
                    value = state.durationMinutesInput,
                    onValueChange = onDurationChange,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(text = "Duration")
                    },
                    suffix = {
                        Text(text = "min")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    singleLine = true,
                    isError = showDurationError,
                    supportingText = if (showDurationError) {
                        {
                            Text(text = "Enter a value greater than 0")
                        }
                    } else {
                        null
                    },
                )

                state.targetCadenceSpmInput?.let { cadenceInput ->
                    val showCadenceError =
                        cadenceInput.isNotEmpty() && !state.hasValidCadence

                    OutlinedTextField(
                        value = cadenceInput,
                        onValueChange = onCadenceChange,
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(text = "Cadence")
                        },
                        suffix = {
                            Text(text = "spm")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true,
                        isError = showCadenceError,
                        supportingText = if (showCadenceError) {
                            {
                                Text(text = "Enter a value greater than 0")
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FutureModeContent(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun stageAccentColor(stage: RunStageType): Color {
    return when (stage) {
        RunStageType.WarmUp -> MaterialTheme.colorScheme.primary
        RunStageType.Running -> MaterialTheme.colorScheme.primaryContainer
        RunStageType.SlowDown -> MaterialTheme.colorScheme.secondary
        RunStageType.Recovery -> MaterialTheme.colorScheme.primary
    }
}

private fun stageTitle(stage: RunStageType): String {
    return when (stage) {
        RunStageType.WarmUp -> "1. Warm-up"
        RunStageType.Running -> "2. Main run"
        RunStageType.SlowDown -> "3. Slow-down"
        RunStageType.Recovery -> "4. Recovery"
    }
}

private fun stageDescription(stage: RunStageType): String {
    return when (stage) {
        RunStageType.WarmUp -> "Prepare gradually for the running stage"
        RunStageType.Running -> "Follow the main target step cadence"
        RunStageType.SlowDown -> "Reduce cadence before recovery"
        RunStageType.Recovery -> "Continue recording without cadence guidance"
    }
}

@Preview(showBackground = true)
@Composable
private fun RunSessionScreenPreview() {
    RhythmRunTheme {
        RunSessionScreen(
            // Preview-only sample values; these are not Watch or Room data.
            state = RunSessionUiState(
                mode = RunSessionUiMode.Plan,
                stages = listOf(
                    StagePlanUiState(RunStageType.WarmUp, "8", "145"),
                    StagePlanUiState(RunStageType.Running, "25", "175"),
                    StagePlanUiState(RunStageType.SlowDown, "7", "150"),
                    StagePlanUiState(RunStageType.Recovery, "5", null),
                ),
                guidanceEnabled = true,
            ),
            onDurationChange = { _, _ -> },
            onCadenceChange = { _, _ -> },
            onGuidanceEnabledChange = {},
            onStartRunClick = {},
            onBackClick = {},
        )
    }
}
