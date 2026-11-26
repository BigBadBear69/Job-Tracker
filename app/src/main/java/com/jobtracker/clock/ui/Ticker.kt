package com.jobtracker.clock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/** Current time in millis, refreshed every second while [running]. */
@Composable
fun rememberNow(running: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        now.longValue = System.currentTimeMillis()
        while (running) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}
