package com.jobtracker.clock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Transaction
    @Query("SELECT * FROM shifts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActiveShift(): Flow<ShiftWithBreaks?>

    @Transaction
    @Query("SELECT * FROM shifts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveShift(): ShiftWithBreaks?

    @Transaction
    @Query("SELECT * FROM shifts ORDER BY startTime DESC")
    fun observeAllShifts(): Flow<List<ShiftWithBreaks>>

    @Transaction
    @Query("SELECT * FROM shifts ORDER BY startTime ASC")
    suspend fun getAllShifts(): List<ShiftWithBreaks>

    @Insert
    suspend fun insertShift(shift: Shift): Long

    @Update
    suspend fun updateShift(shift: Shift)

    @Delete
    suspend fun deleteShift(shift: Shift)

    @Insert
    suspend fun insertBreak(entry: BreakEntry): Long

    @Update
    suspend fun updateBreak(entry: BreakEntry)

    @Delete
    suspend fun deleteBreak(entry: BreakEntry)
}
