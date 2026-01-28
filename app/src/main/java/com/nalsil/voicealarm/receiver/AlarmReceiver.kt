package com.nalsil.voicealarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nalsil.voicealarm.data.AlarmDatabase
import com.nalsil.voicealarm.service.AlarmService
import com.nalsil.voicealarm.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        Log.i("AlarmReceiver", "!!! RECEIVED ALARM EVENT !!! ID: $alarmId")

        if (alarmId == -1) {
            Log.e("AlarmReceiver", "Invalid Alarm ID - cannot start service.")
            return
        }

        // 1. 알람 서비스 시작 (TTS 재생)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. 마지막 실행 시간 기록 및 다음 알람 재스케줄링
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AlarmDatabase.getDatabase(context)
                val dao = db.alarmDao()

                // 마지막 실행 시간 기록
                dao.updateLastTriggeredAt(alarmId, System.currentTimeMillis())
                Log.i("AlarmReceiver", "마지막 실행 시간 기록 완료: ID=$alarmId")

                // 다음 알람 재스케줄링
                val alarm = dao.getAlarmById(alarmId)
                if (alarm != null && alarm.isEnabled) {
                    val scheduler = AlarmScheduler(context)
                    scheduler.schedule(alarm)
                    Log.i("AlarmReceiver", "다음 알람 재스케줄링 완료: ID=$alarmId")
                } else {
                    Log.i("AlarmReceiver", "알람이 비활성화되었거나 존재하지 않음: ID=$alarmId")
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "알람 처리 실패", e)
            }
        }
    }
}
