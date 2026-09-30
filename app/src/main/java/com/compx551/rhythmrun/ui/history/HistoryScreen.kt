package com.compx551.rhythmrun.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBackClick: () -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onDayClick: (Int) -> Unit,
    onSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "History") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TRAINING ARCHIVE",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "Running history and trends",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            item {
                CalendarCard(
                    state = state,
                    onPreviousMonthClick = onPreviousMonthClick,
                    onNextMonthClick = onNextMonthClick,
                    onDayClick = onDayClick,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HistoryMetricCard(
                        label = "WEEKLY TIME",
                        value = "${state.weeklyDurationMinutes} min",
                        modifier = Modifier.weight(1f),
                    )
                    HistoryMetricCard(
                        label = "WEEKLY DISTANCE",
                        value = String.format(
                            Locale.US,
                            "%.1f km",
                            state.weeklyDistanceKilometres,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                CadenceTrendCard(state.cadenceTrend)
            }

            item {
                Text(
                    text = "Recent activity",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (state.sessions.isEmpty()) {
                item { EmptyHistoryCard() }
            } else {
                items(
                    items = state.sessions,
                    key = { it.sessionId },
                ) { session ->
                    HistorySessionCard(
                        session = session,
                        onClick = { onSessionClick(session.sessionId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(
    state: HistoryUiState,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onDayClick: (Int) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreviousMonthClick) { Text(text = "‹") }
                Text(text = state.monthLabel, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onNextMonthClick) { Text(text = "›") }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            state.calendarDays.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            onClick = { day.dayOfMonth?.let(onDayClick) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(7 - week.size) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDayUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayModifier = if (day.isSelected) {
        modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
    } else if (day.sessionCount > 0) {
        modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        modifier
    }

    Box(
        modifier = dayModifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(enabled = day.dayOfMonth != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        day.dayOfMonth?.let { dayNumber ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dayNumber.toString(),
                    color = if (day.isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (day.sessionCount > 0) {
                    Text(
                        text = "•",
                        color = if (day.isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
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
        }
    }
}

@Composable
private fun CadenceTrendCard(points: List<CadenceTrendPointUiModel>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "COMPARABLE RHYTHM TRENDS",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge,
            )
            if (points.isEmpty()) {
                Text(text = "Not enough comparable sessions yet")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    points.forEach { point ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = point.cadenceSpm?.let { "$it spm" } ?: "--",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = point.rhythmStabilityPercent?.let { "$it% stable" }
                                    ?: "Stability --",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = point.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySessionCard(session: HistorySessionUiModel, onClick: () -> Unit) {
    val accentColor = if (session.completed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, accentColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${session.dateLabel} • ${session.timeLabel}",
                color = accentColor,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(text = session.title, style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = formatDuration(session.durationSeconds))
                Text(
                    text = session.distanceMetres?.let {
                        String.format(Locale.US, "%.2f km", it / 1000.0)
                    } ?: "Distance unavailable",
                )
            }
            Text(
                text = buildString {
                    append(session.averageCadenceSpm?.let { "$it spm" } ?: "Cadence unavailable")
                    append("  •  ")
                    append(session.averageHeartRateBpm?.let { "$it bpm" } ?: "HR unavailable")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = session.dataCoveragePercent?.let { "Data coverage $it%" }
                    ?: "Data coverage unavailable",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "No saved runs", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Completed sessions will appear here after they are saved.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0L) / 60L
    val remainingSeconds = seconds.coerceAtLeast(0L) % 60L
    return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
}
