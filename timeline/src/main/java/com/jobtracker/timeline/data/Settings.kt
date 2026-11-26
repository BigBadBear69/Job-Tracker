package com.jobtracker.timeline.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User preferences, exposed as StateFlows so the UI updates instantly. */
class Settings private constructor(private val prefs: SharedPreferences) {
    private fun bool(key: String, def: Boolean) = MutableStateFlow(prefs.getBoolean(key, def))

    private val _tracking = bool(KEY_TRACKING, false)
    private val _lookups = bool(KEY_LOOKUPS, true)
    private val _photos = bool(KEY_PHOTOS, false)
    private val _retention = MutableStateFlow(prefs.getInt(KEY_RETENTION, 0))

    /** True when the user wants recording on (false = paused). */
    val tracking: StateFlow<Boolean> = _tracking
    /** Online place-name lookups (addresses + nearby businesses). Off = fully offline. */
    val onlineLookups: StateFlow<Boolean> = _lookups
    val photos: StateFlow<Boolean> = _photos
    /** Auto-delete history older than this many months; 0 = keep forever. */
    val retentionMonths: StateFlow<Int> = _retention

    fun setTracking(v: Boolean) { prefs.edit().putBoolean(KEY_TRACKING, v).apply(); _tracking.value = v }
    fun setOnlineLookups(v: Boolean) { prefs.edit().putBoolean(KEY_LOOKUPS, v).apply(); _lookups.value = v }
    fun setPhotos(v: Boolean) { prefs.edit().putBoolean(KEY_PHOTOS, v).apply(); _photos.value = v }
    fun setRetentionMonths(v: Int) { prefs.edit().putInt(KEY_RETENTION, v).apply(); _retention.value = v }

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    companion object {
        private const val KEY_TRACKING = "tracking"
        private const val KEY_LOOKUPS = "online_lookups"
        private const val KEY_PHOTOS = "photos"
        private const val KEY_RETENTION = "retention_months"
        private const val KEY_ONBOARDED = "onboarded"

        @Volatile private var instance: Settings? = null
        fun get(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE))
                .also { instance = it }
        }
    }
}
