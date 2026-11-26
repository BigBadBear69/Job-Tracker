package com.jobtracker.timeline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.jobtracker.timeline.processing.Processor
import com.jobtracker.timeline.tracking.TrackingService
import com.jobtracker.timeline.ui.DayScreen
import com.jobtracker.timeline.ui.InsightsScreen
import com.jobtracker.timeline.ui.OnboardingScreen
import com.jobtracker.timeline.ui.PlacesScreen
import com.jobtracker.timeline.ui.SettingsScreen
import com.jobtracker.timeline.ui.TimelineTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = TimelineApp.instance
        setContent {
            TimelineTheme {
                var onboarded by rememberSaveable { mutableStateOf(app.settings.onboarded) }
                if (!onboarded) {
                    Scaffold { p ->
                        androidx.compose.foundation.layout.Box(Modifier.padding(p)) {
                            OnboardingScreen(onStart = {
                                app.settings.onboarded = true
                                app.settings.setTracking(true)
                                TrackingService.sync(this@MainActivity)
                                onboarded = true
                            })
                        }
                    }
                    return@TimelineTheme
                }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var date by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
                val openDay = { d: LocalDate -> date = d.toEpochDay(); tab = 0 }
                Scaffold(bottomBar = {
                    NavigationBar {
                        listOf(
                            Triple("Timeline", Icons.Filled.Timeline, 0), Triple("Places", Icons.Filled.Place, 1),
                            Triple("Insights", Icons.Filled.Insights, 2), Triple("Settings", Icons.Filled.Settings, 3),
                        ).forEach { (label, icon, i) ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) })
                        }
                    }
                }) { p ->
                    val mod = Modifier.padding(p)
                    when (tab) {
                        0 -> DayScreen(LocalDate.ofEpochDay(date), { date = it.toEpochDay() }, mod)
                        1 -> PlacesScreen(openDay, mod)
                        2 -> InsightsScreen(openDay, mod)
                        else -> SettingsScreen(mod)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (TimelineApp.instance.settings.onboarded) {
            TrackingService.sync(this)
            lifecycleScope.launch { runCatching { Processor.processRecent(this@MainActivity) } }
        }
    }
}
