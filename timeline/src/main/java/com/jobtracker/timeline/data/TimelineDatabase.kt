package com.jobtracker.timeline.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun segType(v: SegmentType): String = v.name
    @TypeConverter fun toSegType(v: String): SegmentType = SegmentType.valueOf(v)
    @TypeConverter fun mode(v: TravelMode?): String? = v?.name
    @TypeConverter fun toMode(v: String?): TravelMode? = v?.let { runCatching { TravelMode.valueOf(it) }.getOrNull() }
    @TypeConverter fun cat(v: PlaceCategory): String = v.name
    @TypeConverter fun toCat(v: String): PlaceCategory = PlaceCategory.valueOf(v)
}

@Database(
    entities = [LocationPoint::class, Segment::class, Place::class, GeocodeEntry::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TimelineDatabase : RoomDatabase() {
    abstract fun dao(): TimelineDao

    companion object {
        @Volatile private var instance: TimelineDatabase? = null
        fun get(context: Context): TimelineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TimelineDatabase::class.java, "timeline.db")
                .build().also { instance = it }
        }
    }
}
