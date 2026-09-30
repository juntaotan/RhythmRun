package com.compx551.rhythmrun.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.compx551.rhythmrun.domain.model.RunStage
import java.util.Locale

val RunStage.label: String
    get() = when (this) {
        RunStage.WarmUp -> "Warm-up"
        RunStage.Running -> "Main run"
        RunStage.SlowDown -> "Slow-down"
        RunStage.Recovery -> "Recovery"
    }

val RunStage.numberedLabel: String
    get() = "${ordinal + 1}. $label"

val RunStage.description: String
    get() = when (this) {
        RunStage.WarmUp -> "Prepare gradually for the running stage"
        RunStage.Running -> "Follow the main target step cadence"
        RunStage.SlowDown -> "Reduce cadence before recovery"
        RunStage.Recovery -> "Continue recording without cadence guidance"
    }

@Composable
fun RunStage.accentColor(): Color = when (this) {
    RunStage.WarmUp -> MaterialTheme.colorScheme.primary
    RunStage.Running -> MaterialTheme.colorScheme.primaryContainer
    RunStage.SlowDown -> MaterialTheme.colorScheme.secondary
    RunStage.Recovery -> MaterialTheme.colorScheme.tertiary
}

fun formatClockDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val remainder = safe % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainder)
    }
}

fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
