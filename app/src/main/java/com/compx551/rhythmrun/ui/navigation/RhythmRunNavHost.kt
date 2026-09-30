package com.compx551.rhythmrun.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.compx551.rhythmrun.ui.RhythmRunViewModel
import com.compx551.rhythmrun.ui.dashboard.DashboardScreen
import com.compx551.rhythmrun.ui.history.HistoryScreen
import com.compx551.rhythmrun.ui.runsession.RunSessionScreen

@Composable
fun RhythmRunNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val factory = remember(context) { RhythmRunViewModel.Factory.production(context) }
    val rhythmRunViewModel: RhythmRunViewModel = viewModel(factory = factory)
    val dashboardState by rhythmRunViewModel.dashboardState.collectAsStateWithLifecycle()
    val historyState by rhythmRunViewModel.historyState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = RhythmRunDestination.Dashboard.route,
        modifier = modifier,
    ) {
        composable(route = RhythmRunDestination.Dashboard.route) {
            DashboardScreen(
                state = dashboardState,
                onNewRunClick = {
                    rhythmRunViewModel.startNewRun()
                    navController.navigate(RhythmRunDestination.RunSession.route)
                },
                onResumeRunClick = {
                    rhythmRunViewModel.resumeActiveRun()
                    navController.navigate(RhythmRunDestination.RunSession.route)
                },
                onHistoryClick = {
                    navController.navigate(RhythmRunDestination.History.route)
                },
            )
        }

        composable(route = RhythmRunDestination.RunSession.route) {
            RunSessionScreen(
                state = rhythmRunViewModel.runSessionState,
                onDurationChange = rhythmRunViewModel::updateDuration,
                onCadenceChange = rhythmRunViewModel::updateCadence,
                onGuidanceEnabledChange = rhythmRunViewModel::updateGuidanceEnabled,
                onStartRunClick = rhythmRunViewModel::startRun,
                onPauseResumeClick = rhythmRunViewModel::togglePause,
                onFinishRunClick = rhythmRunViewModel::finishRun,
                onBackClick = navController::popBackStack,
            )
        }

        composable(route = RhythmRunDestination.History.route) {
            HistoryScreen(
                state = historyState,
                onBackClick = navController::popBackStack,
                onPreviousMonthClick = rhythmRunViewModel::showPreviousMonth,
                onNextMonthClick = rhythmRunViewModel::showNextMonth,
                onDayClick = rhythmRunViewModel::selectHistoryDay,
                onSessionClick = { sessionId ->
                    rhythmRunViewModel.openSummary(sessionId) {
                        navController.navigate(RhythmRunDestination.RunSession.route)
                    }
                },
            )
        }
    }
}
