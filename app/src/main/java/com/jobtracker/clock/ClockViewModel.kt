package com.jobtracker.clock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jobtracker.clock.data.AppDatabase
import com.jobtracker.clock.data.BreakEntry
import com.jobtracker.clock.data.Shift
import com.jobtracker.clock.data.ShiftWithBreaks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClockViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).shiftDao()

    val activeShift: StateFlow<ShiftWithBreaks?> =
        dao.observeActiveShift().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allShifts: StateFlow<List<ShiftWithBreaks>> =
        dao.observeAllShifts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            activeShift.collect { ShiftNotification.update(getApplication(), it) }
        }
    }

    private fun now() = System.currentTimeMillis()

    fun clockIn() = viewModelScope.launch {
        if (dao.getActiveShift() == null) dao.insertShift(Shift(startTime = now()))
    }

    fun startBreak(note: String?) = viewModelScope.launch {
        val active = dao.getActiveShift() ?: return@launch
        if (active.activeBreak != null) return@launch
        dao.insertBreak(
            BreakEntry(shiftId = active.shift.id, startTime = now(), note = note?.trim()?.ifBlank { null }),
        )
    }

    fun endBreak() = viewModelScope.launch {
        val b = dao.getActiveShift()?.activeBreak ?: return@launch
        dao.updateBreak(b.copy(endTime = now()))
    }

    fun clockOut() = viewModelScope.launch {
        val active = dao.getActiveShift() ?: return@launch
        val t = now()
        active.activeBreak?.let { dao.updateBreak(it.copy(endTime = t)) }
        dao.updateShift(active.shift.copy(endTime = t))
    }

    fun updateShift(shift: Shift) = viewModelScope.launch { dao.updateShift(shift) }
    fun deleteShift(shift: Shift) = viewModelScope.launch { dao.deleteShift(shift) }
    fun updateBreak(entry: BreakEntry) = viewModelScope.launch { dao.updateBreak(entry) }
    fun deleteBreak(entry: BreakEntry) = viewModelScope.launch { dao.deleteBreak(entry) }
    fun addBreak(entry: BreakEntry) = viewModelScope.launch { dao.insertBreak(entry) }
}
