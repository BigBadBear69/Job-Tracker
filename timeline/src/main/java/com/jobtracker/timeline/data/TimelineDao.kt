package com.jobtracker.timeline.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    // ---- points ----
    @Insert suspend fun insertPoint(p: LocationPoint): Long
    @Insert suspend fun insertPoints(p: List<LocationPoint>)

    @Query("SELECT * FROM points WHERE time >= :from AND time < :to ORDER BY time")
    suspend fun pointsBetween(from: Long, to: Long): List<LocationPoint>

    @Query("SELECT * FROM points ORDER BY time DESC LIMIT 1")
    suspend fun lastPoint(): LocationPoint?

    @Query("SELECT MIN(time) FROM points") suspend fun firstPointTime(): Long?
    @Query("SELECT MAX(time) FROM points") suspend fun lastPointTime(): Long?
    @Query("SELECT COUNT(*) FROM points") suspend fun pointCount(): Long

    @Query("DELETE FROM points WHERE time >= :from AND time <= :to")
    suspend fun deletePointsBetween(from: Long, to: Long)

    @Query("DELETE FROM points WHERE time < :before") suspend fun deletePointsBefore(before: Long)

    // ---- segments ----
    @Insert suspend fun insertSegments(s: List<Segment>)
    @Insert suspend fun insertSegment(s: Segment): Long
    @Update suspend fun updateSegment(s: Segment)
    @Delete suspend fun deleteSegment(s: Segment)

    @Query("SELECT * FROM segments WHERE endTime > :from AND startTime < :to ORDER BY startTime")
    fun observeSegmentsBetween(from: Long, to: Long): Flow<List<Segment>>

    @Query("SELECT * FROM segments WHERE endTime > :from AND startTime < :to ORDER BY startTime")
    suspend fun segmentsBetween(from: Long, to: Long): List<Segment>

    @Query("SELECT * FROM segments WHERE type = 'VISIT' ORDER BY startTime")
    suspend fun allVisits(): List<Segment>

    @Query("SELECT * FROM segments WHERE type = 'VISIT' ORDER BY startTime DESC")
    fun observeAllVisits(): Flow<List<Segment>>

    @Query("SELECT * FROM segments WHERE placeId = :placeId ORDER BY startTime DESC")
    fun observeVisitsAt(placeId: Long): Flow<List<Segment>>

    @Query("SELECT * FROM segments WHERE edited = 0 AND type = 'VISIT' ORDER BY startTime DESC LIMIT 1")
    suspend fun lastAutoVisit(): Segment?

    @Query("SELECT * FROM segments ORDER BY endTime DESC LIMIT 1")
    suspend fun lastSegment(): Segment?

    @Query("SELECT * FROM segments WHERE edited = 1 AND endTime > :from AND startTime < :to ORDER BY startTime")
    suspend fun editedSegmentsBetween(from: Long, to: Long): List<Segment>

    @Query("DELETE FROM segments WHERE edited = 0 AND startTime >= :from")
    suspend fun deleteAutoSegmentsFrom(from: Long)

    @Query("DELETE FROM segments WHERE edited = 0") suspend fun deleteAllAutoSegments()

    @Query("DELETE FROM segments WHERE startTime >= :from AND startTime <= :to")
    suspend fun deleteSegmentsBetween(from: Long, to: Long)

    @Query("DELETE FROM segments WHERE endTime < :before") suspend fun deleteSegmentsBefore(before: Long)

    @Query("SELECT startTime FROM segments WHERE startTime >= :from AND startTime < :to")
    fun observeSegmentStarts(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT * FROM segments ORDER BY startTime")
    suspend fun allSegments(): List<Segment>

    @Query("UPDATE segments SET placeId = NULL WHERE placeId = :placeId")
    suspend fun clearPlace(placeId: Long)

    // ---- places ----
    @Insert suspend fun insertPlace(p: Place): Long
    @Update suspend fun updatePlace(p: Place)
    @Delete suspend fun deletePlace(p: Place)
    @Query("SELECT * FROM places ORDER BY name") fun observePlaces(): Flow<List<Place>>
    @Query("SELECT * FROM places ORDER BY name") suspend fun allPlaces(): List<Place>
    @Query("SELECT * FROM places WHERE id = :id") suspend fun place(id: Long): Place?

    // ---- geocode cache ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGeocode(e: GeocodeEntry)
    @Query("SELECT * FROM geocode_cache WHERE `key` = :key") suspend fun geocode(key: String): GeocodeEntry?
    @Query("SELECT * FROM geocode_cache") fun observeGeocodes(): Flow<List<GeocodeEntry>>

    // ---- wipe ----
    @Query("DELETE FROM points") suspend fun deleteAllPoints()
    @Query("DELETE FROM segments") suspend fun deleteAllSegments()
}
