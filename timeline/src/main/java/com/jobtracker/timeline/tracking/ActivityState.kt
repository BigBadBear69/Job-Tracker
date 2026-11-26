package com.jobtracker.timeline.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow

data class CurrentActivity(val type: String, val confidence: Int, val time: Long)

/** Latest result from Google's on-device activity recognition. */
object ActivityState {
    val latest = MutableStateFlow<CurrentActivity?>(null)

    /** Activity label to store with a fix, if the detection is recent enough. */
    fun labelFor(time: Long): String? = latest.value?.takeIf { time - it.time < 3 * 60_000 }?.type

    fun name(type: Int) = when (type) {
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.TILTING -> "TILTING"
        else -> "UNKNOWN"
    }
}

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        // Prefer the specific WALKING/RUNNING over the generic ON_FOOT when both are likely.
        val best = result.probableActivities
            .filter { it.type != DetectedActivity.TILTING && it.type != DetectedActivity.UNKNOWN }
            .sortedWith(compareByDescending<DetectedActivity> { it.confidence }.thenBy { if (it.type == DetectedActivity.ON_FOOT) 1 else 0 })
            .firstOrNull() ?: return
        var type = best.type
        if (type == DetectedActivity.ON_FOOT) {
            result.probableActivities.filter { it.type == DetectedActivity.WALKING || it.type == DetectedActivity.RUNNING }
                .maxByOrNull { it.confidence }?.let { type = it.type }
        }
        ActivityState.latest.value = CurrentActivity(ActivityState.name(type), best.confidence, result.time)
    }
}
