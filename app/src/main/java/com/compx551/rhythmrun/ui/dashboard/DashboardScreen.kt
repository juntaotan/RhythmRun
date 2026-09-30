package com.compx551.rhythmrun.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class WatchConnectionStatus {
    Connected,
    Disconnected,
}

data class LastRunUiModel(
    val title: String,
    val distanceKm: Double?,
    val averageCadenceSpm: Int?,
)

data class DashboardUiState(
    val watchConnectionStatus: WatchConnectionStatus,
    val lastRun: LastRunUiModel?,
    val hasResumableSession: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onNewRunClick: () -> Unit,
    onResumeRunClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = "RhythmRun")
                },
            )
        },
    ) { innerPadding ->
        DashboardContent(
            state = state,
            onNewRunClick = onNewRunClick,
            onResumeRunClick = onResumeRunClick,
            onHistoryClick = onHistoryClick,
            contentPadding = innerPadding,
        )
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onNewRunClick: () -> Unit,
    onResumeRunClick: () -> Unit,
    onHistoryClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Galaxy Watch",
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Connection status",
                    style = MaterialTheme.typography.labelLarge,
                )

                Text(
                    text = when (state.watchConnectionStatus) {
                        WatchConnectionStatus.Connected -> "Connected"
                        WatchConnectionStatus.Disconnected -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Text(
            text = "Last run",
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val lastRun = state.lastRun

                if (lastRun == null) {
                    Text(text = "No saved runs yet")
                } else {
                    Text(
                        text = lastRun.title,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = lastRun.distanceKm?.let {
                            "Distance: %.2f km".format(it)
                        } ?: "Distance unavailable",
                    )

                    Text(
                        text = lastRun.averageCadenceSpm?.let {
                            "Average cadence: $it spm"
                        } ?: "Cadence unavailable",
                    )
                }
            }
        }

        Button(
            onClick = if (state.hasResumableSession) onResumeRunClick else onNewRunClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (state.hasResumableSession) "Resume Run" else "New Run")
        }

        OutlinedButton(
            onClick = onHistoryClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "History")
        }
    }
}
