package com.jobtracker.timeline.io

import android.util.JsonReader
import android.util.JsonToken

/** Reads the next JSON value into plain Kotlin maps/lists/strings/doubles. Used for small elements only. */
fun JsonReader.readTree(): Any? = when (peek()) {
    JsonToken.BEGIN_OBJECT -> {
        val m = HashMap<String, Any?>()
        beginObject(); while (hasNext()) m[nextName()] = readTree(); endObject(); m
    }
    JsonToken.BEGIN_ARRAY -> {
        val l = ArrayList<Any?>()
        beginArray(); while (hasNext()) l += readTree(); endArray(); l
    }
    JsonToken.STRING -> nextString()
    JsonToken.NUMBER -> nextString().toDoubleOrNull()
    JsonToken.BOOLEAN -> nextBoolean()
    JsonToken.NULL -> { nextNull(); null }
    else -> { skipValue(); null }
}

@Suppress("UNCHECKED_CAST")
fun Any?.obj(key: String): Map<String, Any?>? = (this as? Map<String, Any?>)?.get(key) as? Map<String, Any?>
fun Any?.str(key: String): String? = (this as? Map<*, *>)?.get(key)?.let { if (it is Double) it.toString() else it as? String }
fun Any?.num(key: String): Double? = (this as? Map<*, *>)?.get(key)?.let { (it as? Double) ?: (it as? String)?.toDoubleOrNull() }
fun Any?.list(key: String): List<Any?>? = (this as? Map<*, *>)?.get(key) as? List<Any?>
