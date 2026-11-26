package com.jobtracker.timeline.tracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jobtracker.timeline.MainActivity
import com.jobtracker.timeline.R
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.LocationPoint
import com.jobtracker.timeline.processing.Geo
import com.jobtracker.timeline.processing.Processor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Records location in the background. To save battery it switches between a fast GPS mode while
 * moving and a slow, low-power mode once you've stayed in one spot for a few minutes.
 */
class TrackingService : LifecycleService() {
    private enum class Mode { MOVING, STILL }

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var mode: Mode? = null
    private var anchor: Location? = null
    private var anchorTime = 0L
    private var processJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onFix)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PAUSE) {
            TimelineApp.instance.settings.setTracking(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Permissions.location(this)) { stopSelf(); return START_NOT_STICKY }

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (mode == null) {
            setMode(Mode.MOVING)
            startActivityUpdates()
            processJob = lifecycleScope.launch {
                while (true) {
                    delay(10 * 60_000L)
                    runCatching { Processor.processRecent(this@TrackingService) }
                }
            }
            lifecycleScope.launch {
                ActivityState.latest.collect { a ->
                    if (a != null && mode == Mode.STILL && a.confidence >= 75 &&
                        a.type in setOf("IN_VEHICLE", "ON_BICYCLE", "WALKING", "RUNNING", "ON_FOOT")
                    ) setMode(Mode.MOVING)
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun setMode(m: Mode) {
        if (m == mode) return
        mode = m
        val req = when (m) {
            Mode.MOVING -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
                .setMinUpdateIntervalMillis(5_000)
                .setMinUpdateDistanceMeters(15f)
                .build()
            Mode.STILL -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3 * 60_000)
                .setMinUpdateIntervalMillis(60_000)
                .build()
        }
        fused.removeLocationUpdates(callback)
        runCatching { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) }
    }

    private fun onFix(loc: Location) {
        if (loc.accuracy > 500) return
        val time = loc.time
        lifecycleScope.launch {
            TimelineApp.instance.dao.insertPoint(
                LocationPoint(
                    time = time, lat = loc.latitude, lon = loc.longitude, accuracy = loc.accuracy,
                    speed = if (loc.hasSpeed()) loc.speed else null, activity = ActivityState.labelFor(time),
                ),
            )
        }
        if (loc.accuracy > 100) return
        val a = anchor
        val moved = a == null || Geo.distance(a.latitude, a.longitude, loc.latitude, loc.longitude) >
            maxOf(60f, loc.accuracy + a.accuracy)
        if (moved) {
            anchor = loc; anchorTime = time
            if (mode == Mode.STILL) setMode(Mode.MOVING)
        } else if (mode == Mode.MOVING && time - anchorTime > 3 * 60_000) {
            setMode(Mode.STILL)
            lifecycleScope.launch { runCatching { Processor.processRecent(this@TrackingService) } }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startActivityUpdates() {
        if (!Permissions.activity(this)) return
        runCatching {
            ActivityRecognition.getClient(this).requestActivityUpdates(30_000, activityIntent(this))
        }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        runCatching { ActivityRecognition.getClient(this).removeActivityUpdates(activityIntent(this)) }
        processJob?.cancel()
        super.onDestroy()
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID).run {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Timeline recording", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while your timeline is being recorded"
                    setShowBadge(false)
                },
            )
        }
        setSmallIcon(R.drawable.ic_stat_timeline)
        setContentTitle("Recording your timeline")
        setContentText("Tap to open · Pause any time")
        setOngoing(true)
        setSilent(true)
        setContentIntent(
            PendingIntent.getActivity(this@TrackingService, 0, Intent(this@TrackingService, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE),
        )
        addAction(
            0, "Pause",
            PendingIntent.getService(this@TrackingService, 1,
                Intent(this@TrackingService, TrackingService::class.java).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE),
        )
        build()
    }

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 10
        private const val ACTION_PAUSE = "pause"

        private fun activityIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
            c, 2, Intent(c, ActivityReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )

        /** Starts or stops the service to match the user's setting and granted permissions. */
        fun sync(context: Context) {
            val wanted = TimelineApp.instance.settings.tracking.value && Permissions.location(context)
            val intent = Intent(context, TrackingService::class.java)
            if (wanted) runCatching { ContextCompat.startForegroundService(context, intent) }
            else context.stopService(intent)
        }
    }
}
