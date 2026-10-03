package com.compx551.rhythmrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.compx551.rhythmrun.ui.navigation.RhythmRunNavHost
import com.compx551.rhythmrun.ui.theme.RhythmRunTheme

import android.content.Context
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

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
