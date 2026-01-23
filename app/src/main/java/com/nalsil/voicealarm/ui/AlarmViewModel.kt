package com.nalsil.voicealarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nalsil.voicealarm.data.Alarm
import com.nalsil.voicealarm.data.AlarmDatabase
import com.nalsil.voicealarm.util.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AlarmDatabase.getDatabase(application)
    private val dao = db.alarmDao()
    private val scheduler = AlarmScheduler(application)

    val alarms: StateFlow<List<Alarm>> = dao.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val id = dao.insertAlarm(alarm).toInt()
            if (alarm.isEnabled) {
                scheduler.schedule(alarm.copy(id = id))
            }
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            dao.updateAlarm(alarm)
            if (alarm.isEnabled) {
                scheduler.schedule(alarm)
            } else {
                scheduler.cancel(alarm)
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            dao.deleteAlarm(alarm)
            scheduler.cancel(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        updateAlarm(updated)
    }
}
