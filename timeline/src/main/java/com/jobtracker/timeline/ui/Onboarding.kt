package com.jobtracker.timeline.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jobtracker.timeline.tracking.Permissions

/** Re-reads permission state whenever the screen resumes (e.g. returning from system settings). */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    return tick
}

/** Permission checklist. Also used from Settings to fix missing permissions. */
@SuppressLint("BatteryLife")
@Composable
fun PermissionSteps(showStart: Boolean, onStart: () -> Unit = {}) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val fine = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    // Reading these makes the checklist recompose after a permission result or returning to the app.
    check(tick >= 0 && refresh >= 0)

    val loc = Permissions.location(context)
    val bg = Permissions.background(context)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Step("Location", "Lets the app see where you are.", loc) {
            fine.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (Build.VERSION.SDK_INT >= 29) Step(
            "Allow all the time",
            "Needed to record while the app is closed. On the next screen choose \"Allow all the time\".",
            bg, enabled = loc,
        ) { single.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        if (Build.VERSION.SDK_INT >= 33) Step("Notifications", "Shows the \"Recording\" notification Android requires.",
            Permissions.notifications(context)) { single.launch(Manifest.permission.POST_NOTIFICATIONS) }
        if (Build.VERSION.SDK_INT >= 29) Step("Physical activity (optional)", "Detects walking, cycling and driving.",
            Permissions.activity(context)) { single.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
        Step("Unrestricted battery (recommended)", "Stops Android from killing recording in the background.",
            Permissions.battery(context)) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }
        }
        if (showStart) {
            Button(onClick = onStart, enabled = loc && bg, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Start recording my timeline")
            }
            if (!(loc && bg)) Text("Location \"all the time\" is required to start.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Step(title: String, desc: String, done: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                tint = if (done) Color(0xFF34A853) else MaterialTheme.colorScheme.outline)
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!done) OutlinedButton(onClick = onClick, enabled = enabled) { Text("Allow") }
        }
    }
}

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your Timeline", style = MaterialTheme.typography.headlineMedium)
        Text(
            "See where you've been: places you visited, routes you took, and how you got there. " +
                "Everything is stored only on this phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionSteps(showStart = true, onStart = onStart)
    }
}
