package com.jobtracker.timeline.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object Permissions {
    private fun has(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun location(c: Context) = has(c, Manifest.permission.ACCESS_FINE_LOCATION)
    fun background(c: Context) = Build.VERSION.SDK_INT < 29 || has(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun notifications(c: Context) = Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS)
    fun activity(c: Context) = Build.VERSION.SDK_INT < 29 || has(c, Manifest.permission.ACTIVITY_RECOGNITION)
    fun battery(c: Context) = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    val photoPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun photos(c: Context) = has(c, photoPermission) ||
        (Build.VERSION.SDK_INT >= 34 && has(c, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))

    /** Everything needed for recording to work in the background. */
    fun canTrack(c: Context) = location(c) && background(c)
}
