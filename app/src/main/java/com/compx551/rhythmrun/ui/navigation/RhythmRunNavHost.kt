package com.compx551.rhythmrun.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.compx551.rhythmrun.ui.dashboard.DashboardScreen
import com.compx551.rhythmrun.ui.dashboard.DashboardUiState
import com.compx551.rhythmrun.ui.dashboard.WatchConnectionStatus
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.compx551.rhythmrun.ui.runsession.RunSessionScreen
import com.compx551.rhythmrun.ui.runsession.RunSessionUiState
import com.compx551.rhythmrun.ui.runsession.RunStageType
import com.compx551.rhythmrun.ui.runsession.createInitialRunSessionUiState

@Composable
fun RhythmRunNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    var runSessionState by remember {
        mutableStateOf(createInitialRunSessionUiState())
    }
    NavHost(
        navController = navController,
        startDestination = RhythmRunDestination.Dashboard.route,
        modifier = modifier,
    ) {
        composable(
            route = RhythmRunDestination.Dashboard.route,
        ) {
            // TODO(Member 2 integration):
            // Replace with the real Data Layer connection state.
            //
            // TODO(Room integration):
            // Replace null with the latest stored session.
            val temporaryDashboardState = DashboardUiState(
                watchConnectionStatus = WatchConnectionStatus.Disconnected,
                lastRun = null,
            )

            DashboardScreen(
                state = temporaryDashboardState,
                onNewRunClick = {
                    runSessionState = createInitialRunSessionUiState()

                    navController.navigate(
                        RhythmRunDestination.RunSession.route,
                    )
                },
                onHistoryClick = {
                    navController.navigate(
                        RhythmRunDestination.History.route,
                    )
                },
            )
        }

        composable(
            route = RhythmRunDestination.RunSession.route,
        ) {
            RunSessionScreen(
                state = runSessionState,
                onDurationChange = { stage, value ->
                    if (value.all { character -> character.isDigit() }) {
                        runSessionState = runSessionState.withDurationInput(
                            stage = stage,
                            value = value,
                        )
                    }
                },
                onCadenceChange = { stage, value ->
                    if (value.all { character -> character.isDigit() }) {
                        runSessionState = runSessionState.withCadenceInput(
                            stage = stage,
                            value = value,
                        )
                    }
                },
                onGuidanceEnabledChange = { enabled ->
                    runSessionState = runSessionState.copy(
                        guidanceEnabled = enabled,
                    )
                },
                onStartRunClick = {
                    // TODO(Member 1/2 integration):
                    // Send the validated plan and wait for the Watch
                    // to confirm that the session has started.
                },
                onBackClick = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = RhythmRunDestination.History.route,
        ) {
            PlaceholderScreen(
                title = "History",
                description = "Session history and trends will appear here",
                onBackClick = {
                    navController.popBackStack()
                },
            )
        }
    }
}

private fun RunSessionUiState.withDurationInput(
    stage: RunStageType,
    value: String,
): RunSessionUiState {
    return copy(
        stages = stages.map { stageState ->
            if (stageState.stage == stage) {
                stageState.copy(
                    durationMinutesInput = value,
                )
            } else {
                stageState
            }
        },
    )
}

private fun RunSessionUiState.withCadenceInput(
    stage: RunStageType,
    value: String,
): RunSessionUiState {
    return copy(
        stages = stages.map { stageState ->
            if (
                stageState.stage == stage &&
                stageState.targetCadenceSpmInput != null
            ) {
                stageState.copy(
                    targetCadenceSpmInput = value,
                )
            } else {
                stageState
            }
        },
    )
}
@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    onBackClick: () -> Unit,
) {
    Column(
        modifier = Modifier
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
            text = description,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )

        Button(
            onClick = onBackClick,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(text = "Back")
        }
    }
}
