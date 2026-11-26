package com.jobtracker.timeline.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.jobtracker.timeline.data.PlaceCategory
import com.jobtracker.timeline.data.TravelMode

@Composable
fun TimelineTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

const val VISIT_COLOR = "#1A73E8"

fun modeColorHex(m: TravelMode?): String = when (m) {
    TravelMode.WALKING -> "#34A853"
    TravelMode.RUNNING -> "#F57C00"
    TravelMode.CYCLING -> "#00897B"
    TravelMode.DRIVING -> "#4285F4"
    TravelMode.MOTORCYCLE -> "#3949AB"
    TravelMode.BUS -> "#C62828"
    TravelMode.TRAIN, TravelMode.SUBWAY, TravelMode.TRAM -> "#8E24AA"
    TravelMode.FERRY -> "#0277BD"
    TravelMode.FLYING -> "#6D4C41"
    TravelMode.UNKNOWN, null -> "#757575"
}

fun modeColor(m: TravelMode?) = Color(android.graphics.Color.parseColor(modeColorHex(m)))

fun modeIcon(m: TravelMode?): ImageVector = when (m) {
    TravelMode.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
    TravelMode.RUNNING -> Icons.AutoMirrored.Filled.DirectionsRun
    TravelMode.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    TravelMode.DRIVING -> Icons.Filled.DirectionsCar
    TravelMode.MOTORCYCLE -> Icons.Filled.TwoWheeler
    TravelMode.BUS -> Icons.Filled.DirectionsBus
    TravelMode.TRAIN -> Icons.Filled.Train
    TravelMode.SUBWAY -> Icons.Filled.DirectionsSubway
    TravelMode.TRAM -> Icons.Filled.Tram
    TravelMode.FERRY -> Icons.Filled.DirectionsBoat
    TravelMode.FLYING -> Icons.Filled.Flight
    TravelMode.UNKNOWN, null -> Icons.Filled.Timeline
}

fun categoryIcon(c: PlaceCategory?): ImageVector = when (c) {
    PlaceCategory.HOME -> Icons.Filled.Home
    PlaceCategory.WORK -> Icons.Filled.Work
    PlaceCategory.SCHOOL -> Icons.Filled.School
    else -> Icons.Filled.Place
}
