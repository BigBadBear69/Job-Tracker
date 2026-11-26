package com.jobtracker.timeline.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jobtracker.timeline.MainActivity
import com.jobtracker.timeline.R
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.SegmentType
import com.jobtracker.timeline.places.PlaceLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen widget: "Today · 4 places · 12.3 km". */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context) } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val today = LocalDate.now()
            val segs = TimelineApp.instance.dao.segmentsBetween(Fmt.startOf(today), Fmt.startOf(today.plusDays(1)))
            val places = segs.filter { it.type == SegmentType.VISIT }.distinctBy { it.placeId ?: PlaceLookup.key(it.lat, it.lon) }.size
            val dist = segs.filter { it.type == SegmentType.TRIP }.sumOf { it.distanceMeters }
            val recording = TimelineApp.instance.settings.tracking.value
            val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_title, if (recording) "Today" else "Today · paused")
                setTextViewText(R.id.widget_body, "$places place${if (places == 1) "" else "s"} · ${Fmt.distance(dist)}")
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0,
                    Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
