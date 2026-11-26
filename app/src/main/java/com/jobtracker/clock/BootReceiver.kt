package com.jobtracker.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jobtracker.clock.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores the "clocked in" notification after the phone restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ShiftNotification.update(context, AppDatabase.get(context).shiftDao().getActiveShift())
            } finally {
                pending.finish()
            }
        }
    }
}
