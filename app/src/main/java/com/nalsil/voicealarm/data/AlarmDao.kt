package com.nalsil.voicealarm.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun getAllAlarmsOnce(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm): Int

    @Delete
    suspend fun deleteAlarm(alarm: Alarm): Int

    @Query("UPDATE alarms SET lastTriggeredAt = :timestamp WHERE id = :alarmId")
    suspend fun updateLastTriggeredAt(alarmId: Int, timestamp: Long)
}
