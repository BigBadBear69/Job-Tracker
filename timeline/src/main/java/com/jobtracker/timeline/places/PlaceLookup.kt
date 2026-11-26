package com.jobtracker.timeline.places

import android.content.Context
import android.location.Geocoder
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.GeocodeEntry
import com.jobtracker.timeline.processing.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class NearbyPlace(val name: String, val type: String, val lat: Double, val lon: Double, val distance: Double)

/**
 * Optional online lookups. Every call checks the "online lookups" setting first; when it's off,
 * only already-cached results are used and nothing leaves the phone.
 */
object PlaceLookup {
    fun key(lat: Double, lon: Double) = String.format(Locale.US, "%.4f,%.4f", lat, lon)

    private val enabled get() = TimelineApp.instance.settings.onlineLookups.value

    suspend fun cached(lat: Double, lon: Double): GeocodeEntry? = TimelineApp.instance.dao.geocode(key(lat, lon))

    /** Address for a coordinate, from cache or (if allowed) the phone's geocoder. */
    @Suppress("DEPRECATION")
    suspend fun address(context: Context, lat: Double, lon: Double): GeocodeEntry? = withContext(Dispatchers.IO) {
        val dao = TimelineApp.instance.dao
        dao.geocode(key(lat, lon))?.let { return@withContext it }
        if (!enabled || !Geocoder.isPresent()) return@withContext null
        val a = runCatching { Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull() }
            .getOrNull() ?: return@withContext null
        val line = a.getAddressLine(0)
            ?: listOfNotNull(a.thoroughfare?.let { t -> a.subThoroughfare?.let { "$it $t" } ?: t }, a.locality).joinToString(", ")
        val entry = GeocodeEntry(key(lat, lon), line.ifBlank { null }, a.locality ?: a.subAdminArea, a.countryName)
        dao.putGeocode(entry)
        entry
    }

    suspend fun geocodeRecentVisits(context: Context, limit: Int) {
        if (!enabled) return
        val dao = TimelineApp.instance.dao
        var n = 0
        for (v in dao.allVisits().asReversed()) {
            if (n >= limit) break
            if (dao.geocode(key(v.lat, v.lon)) == null) { address(context, v.lat, v.lon); n++ }
        }
    }

    /** Businesses and points of interest near a coordinate, from OpenStreetMap (Overpass API). */
    suspend fun nearby(lat: Double, lon: Double, radius: Int = 120): List<NearbyPlace> = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext emptyList()
        val q = """
            [out:json][timeout:15];
            nwr(around:$radius,$lat,$lon)[name][~"^(amenity|shop|tourism|leisure|office|craft|healthcare|sport|public_transport|railway|aeroway|building|historic)$"~"."];
            out center tags 40;
        """.trimIndent()
        runCatching {
            val conn = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000; conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "JobTrackerTimeline/1.0 (Android)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val els = JSONObject(body).getJSONArray("elements")
            (0 until els.length()).mapNotNull { i ->
                val e = els.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: return@mapNotNull null
                val name = tags.optString("name").ifBlank { return@mapNotNull null }
                val c = e.optJSONObject("center")
                val plat = c?.optDouble("lat") ?: e.optDouble("lat", Double.NaN)
                val plon = c?.optDouble("lon") ?: e.optDouble("lon", Double.NaN)
                if (plat.isNaN() || plon.isNaN()) return@mapNotNull null
                val type = listOf("amenity", "shop", "tourism", "leisure", "office", "craft", "healthcare", "sport",
                    "public_transport", "railway", "aeroway", "historic", "building")
                    .firstNotNullOfOrNull { k -> tags.optString(k).takeIf { it.isNotBlank() && it != "yes" } }
                    ?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: ""
                NearbyPlace(name, type, plat, plon, Geo.distance(lat, lon, plat, plon))
            }.distinctBy { it.name }.sortedBy { it.distance }
        }.getOrDefault(emptyList())
    }
}
