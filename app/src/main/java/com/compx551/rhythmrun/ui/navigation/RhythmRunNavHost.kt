package com.compx551.rhythmrun.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.compx551.rhythmrun.ui.RhythmRunViewModel
import com.compx551.rhythmrun.ui.dashboard.DashboardScreen
import com.compx551.rhythmrun.ui.history.HistoryScreen
import com.compx551.rhythmrun.ui.runsession.RunSessionScreen
import kotlinx.coroutines.delay

@Composable
fun RhythmRunNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val factory = remember(context) { RhythmRunViewModel.Factory.production(context) }
    val rhythmRunViewModel: RhythmRunViewModel = viewModel(factory = factory)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> rhythmRunViewModel.startRun(usePhoneGps = granted) }
    val dashboardState by rhythmRunViewModel.dashboardState.collectAsStateWithLifecycle()
    val historyState by rhythmRunViewModel.historyState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = RhythmRunDestination.Dashboard.route,
        modifier = modifier,
    ) {
        composable(route = RhythmRunDestination.Dashboard.route) {
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        rhythmRunViewModel.refreshWatchConnection()
                        delay(5_000L)
                    }
                }
            }
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
                onStartRunClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        rhythmRunViewModel.startRun(usePhoneGps = true)
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
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
