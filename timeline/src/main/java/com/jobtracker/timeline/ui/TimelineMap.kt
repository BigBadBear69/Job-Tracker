package com.jobtracker.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Free OpenStreetMap-based vector map style; no API key needed. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

data class MapLine(val points: List<Pair<Double, Double>>, val colorHex: String)
data class MapPin(val id: String, val lat: Double, val lon: Double, val label: String, val colorHex: String = VISIT_COLOR)

/**
 * A MapLibre map showing route lines and labelled pins. The camera fits everything whenever
 * [fitKey] changes, or zooms to [focus] when set.
 */
@Composable
fun TimelineMap(
    lines: List<MapLine>,
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    fitKey: Any? = null,
    focus: Pair<Double, Double>? = null,
    selectedPin: String? = null,
    onPinClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val clickHandler by rememberUpdatedState(onPinClick)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                s.addSource(GeoJsonSource("lines"))
                s.addSource(GeoJsonSource("pins"))
                s.addLayer(
                    LineLayer("lines-casing", "lines").withProperties(
                        PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(8f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    LineLayer("lines-layer", "lines").withProperties(
                        PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    CircleLayer("pins-layer", "pins").withProperties(
                        PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.circleRadius(
                            Expression.switchCase(Expression.get("selected"), Expression.literal(11f), Expression.literal(7f)),
                        ),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2.5f),
                    ),
                )
                s.addLayer(
                    SymbolLayer("pins-labels", "pins").withProperties(
                        PropertyFactory.textField(Expression.get("label")),
                        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                        PropertyFactory.textSize(12f),
                        PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                        PropertyFactory.textHaloColor("#FFFFFF"),
                        PropertyFactory.textHaloWidth(1.5f),
                        PropertyFactory.textOptional(true),
                    ),
                )
                m.addOnMapClickListener { latLng ->
                    val screen = m.projection.toScreenLocation(latLng)
                    val f = m.queryRenderedFeatures(screen, "pins-layer").firstOrNull()
                    f?.getStringProperty("id")?.let { clickHandler(it); true } ?: false
                }
                style = s
            }
            map = m
        }
    }

    // Push data into the map whenever it changes.
    LaunchedEffect(style, lines, pins, selectedPin) {
        val s = style ?: return@LaunchedEffect
        (s.getSource("lines") as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                lines.filter { it.points.size >= 2 }.map { l ->
                    Feature.fromGeometry(LineString.fromLngLats(l.points.map { Point.fromLngLat(it.second, it.first) }))
                        .apply { addStringProperty("color", l.colorHex) }
                },
            ),
        )
        (s.getSource("pins") as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                pins.map { p ->
                    Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                        addStringProperty("id", p.id)
                        addStringProperty("label", p.label)
                        addStringProperty("color", p.colorHex)
                        addBooleanProperty("selected", p.id == selectedPin)
                    }
                },
            ),
        )
    }

    LaunchedEffect(map, fitKey, focus) {
        val m = map ?: return@LaunchedEffect
        if (focus != null) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus.first, focus.second), 15.5), 600)
            return@LaunchedEffect
        }
        val all = lines.flatMap { it.points } + pins.map { it.lat to it.lon }
        if (all.isEmpty()) return@LaunchedEffect
        val distinct = all.distinct()
        if (distinct.size == 1) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(distinct[0].first, distinct[0].second), 15.0))
        } else {
            val b = LatLngBounds.Builder().apply { distinct.forEach { include(LatLng(it.first, it.second)) } }.build()
            runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(b, 80)) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
