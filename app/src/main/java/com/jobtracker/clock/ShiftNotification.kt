package com.jobtracker.clock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jobtracker.clock.data.ShiftWithBreaks

/** Ongoing notification with a running chronometer shown while clocked in. */
object ShiftNotification {
    private const val CHANNEL_ID = "shift_status"
    private const val NOTIFICATION_ID = 1

    fun update(context: Context, active: ShiftWithBreaks?) {
        val manager = NotificationManagerCompat.from(context)
        if (active == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return

        ensureChannel(context)
        val now = System.currentTimeMillis()
        val onBreak = active.activeBreak
        val title: String
        val base: Long
        if (onBreak != null) {
            title = "On break" + (onBreak.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            base = onBreak.startTime
        } else {
            title = "Clocked in since ${Format.time(active.shift.startTime)}"
            // Chronometer counts worked time: shift the base forward by completed break time.
            base = now - active.workedMillis(now)
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(if (onBreak != null) "Break time" else "Time worked")
            .setWhen(base)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Shift status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows a running timer while you are clocked in"
                },
            )
        }
    }
}
