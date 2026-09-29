package com.compx551.rhythmrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.compx551.rhythmrun.ui.dashboard.DashboardScreen
import com.compx551.rhythmrun.ui.dashboard.DashboardUiState
import com.compx551.rhythmrun.ui.dashboard.WatchConnectionStatus
import com.compx551.rhythmrun.ui.theme.RhythmRunTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RhythmRunTheme {
                RhythmRunApp()
            }
        }
    }
}

@Composable
fun RhythmRunApp() {
    // TODO(Member 2 integration):
    // Replace this temporary connection state with the real Data Layer status.
    //
    // TODO(Room integration):
    // Replace null with the latest saved session from the phone repository.
    val temporaryDashboardState = DashboardUiState(
        watchConnectionStatus = WatchConnectionStatus.Disconnected,
        lastRun = null,
    )

    DashboardScreen(
        state = temporaryDashboardState,
        onNewRunClick = {
            // TODO: Navigate to Run Plan.
        },
        onHistoryClick = {
            // TODO: Navigate to History.
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun RhythmRunAppPreview() {
    RhythmRunTheme {
        RhythmRunApp()
    }
}