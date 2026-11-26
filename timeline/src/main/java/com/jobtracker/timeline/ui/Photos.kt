package com.jobtracker.timeline.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Photo(val uri: Uri, val takenAt: Long)

/** Photos taken between [from] and [to] (matched by time, so no photo location access is needed). */
suspend fun queryPhotos(context: Context, from: Long, to: Long): List<Photo> = withContext(Dispatchers.IO) {
    val out = ArrayList<Photo>()
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN),
            "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?",
            arrayOf(from.toString(), to.toString()),
            "${MediaStore.Images.Media.DATE_TAKEN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), c.getLong(1))
            }
        }
    }
    out
}

@Composable
fun PhotoStrip(photos: List<Photo>, max: Int = 5) {
    if (photos.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.take(max).forEachIndexed { i, p ->
            val extra = photos.size - max
            Box(contentAlignment = Alignment.Center) {
                Thumb(p)
                if (i == max - 1 && extra > 0) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) { Text("+$extra", color = MaterialTheme.colorScheme.inverseOnSurface) }
                }
            }
        }
    }
}

@Composable
private fun Thumb(p: Photo) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, p.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(p.uri, Size(160, 160), null)
                else context.contentResolver.openInputStream(p.uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })
                }
            }.getOrNull()
        }
    }
    val mod = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clickable {
        context.startActivity(Intent(Intent.ACTION_VIEW, p.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    bmp?.let { Image(it.asImageBitmap(), null, mod, contentScale = ContentScale.Crop) }
        ?: Box(mod.background(MaterialTheme.colorScheme.surfaceVariant))
}
