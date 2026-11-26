package com.jobtracker.timeline

import android.app.Application
import com.jobtracker.timeline.data.Settings
import com.jobtracker.timeline.data.TimelineDao
import com.jobtracker.timeline.data.TimelineDatabase
import org.maplibre.android.MapLibre

class TimelineApp : Application() {
    val dao: TimelineDao by lazy { TimelineDatabase.get(this).dao() }
    val settings: Settings by lazy { Settings.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        MapLibre.getInstance(this)
    }

    companion object {
        lateinit var instance: TimelineApp
            private set
    }
}
