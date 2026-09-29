package com.compx551.rhythmrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.compx551.rhythmrun.ui.navigation.RhythmRunNavHost
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
    RhythmRunNavHost()
}

@Preview(showBackground = true)
@Composable
private fun RhythmRunAppPreview() {
    RhythmRunTheme {
        RhythmRunApp()
    }
}
