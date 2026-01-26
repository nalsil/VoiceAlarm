package com.nalsil.voicealarm.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nalsil.voicealarm.data.Alarm
import com.nalsil.voicealarm.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.*

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        // --- START: 권한 및 유효성 검사 ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("AlarmScheduler", "PERMISSION DENIED: canScheduleExactAlarms() is false.")
                return
            }
        }

        val enabledDays = alarm.getEnabledDays()
        if (enabledDays.isEmpty()) {
            Log.w("AlarmScheduler", "Alarm ID ${alarm.id} has no enabled days. Cancelling any existing alarm.")
            cancel(alarm) // 활성화된 요일이 없으면 혹시 모를 기존 알람을 취소하고 종료
            return
        }
        // --- END: 권한 및 유효성 검사 ---

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- START: 핵심 로직 수정 ---
        // 다음 알람 시간을 정확히 계산하는 로직
        val nextAlarmTime = getNextAlarmTime(alarm.hour, alarm.minute, enabledDays)
        if (nextAlarmTime == -1L) {
             Log.w("AlarmScheduler", "Could not find a valid next alarm time for Alarm ID ${alarm.id}.")
             return
        }
        // --- END: 핵심 로직 수정 ---


        // --- ENHANCED DEBUG LOGGING ---
        val now = System.currentTimeMillis()
        val scheduledTime = nextAlarmTime
        val diffMinutes = (scheduledTime - now) / 60000.0
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z (E)", Locale.getDefault())

        Log.i("AlarmScheduler", "=================================================")
        Log.i("AlarmScheduler", "Scheduling Alarm ID: ${alarm.id} for H:${alarm.hour} M:${alarm.minute}")
        Log.i("AlarmScheduler", "  - Enabled Days: ${enabledDays.map { dayToString(it) }.joinToString()}")
        Log.i("AlarmScheduler", "  - Current Time:   ${sdf.format(Date(now))}")
        Log.i("AlarmScheduler", "  - Scheduled For:  ${sdf.format(Date(scheduledTime))}")
        Log.i("AlarmScheduler", "  - Alarm will fire in approximately ${"%.2f".format(diffMinutes)} minutes.")
        Log.i("AlarmScheduler", "=================================================")
        // --- END DEBUG LOGGING ---

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledTime,
                pendingIntent
            )
            Log.i("AlarmScheduler", "setExactAndAllowWhileIdle() called successfully.")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException: Failed to schedule exact alarm. Check permissions.", e)
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Exception while scheduling alarm.", e)
        }
    }

    private fun getNextAlarmTime(hour: Int, minute: Int, enabledDays: List<Int>): Long {
        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 오늘 알람 시간이 이미 지났다면, 내일부터 탐색 시작
        if (calendar.timeInMillis <= now.timeInMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 다음 7일간 루프를 돌면서 가장 가까운 '활성화된 요일'을 찾음
        for (i in 0..7) {
            // Calendar의 DAY_OF_WEEK는 일요일(1)부터 토요일(7)입니다.
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) 
            if (dayOfWeek in enabledDays) {
                // 활성화된 요일을 찾았으므로 이 시간으로 알람 설정
                return calendar.timeInMillis
            }
            // 활성화된 요일이 아니면 다음 날로 이동
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 이론적으로 enabledDays가 비어있지 않으면 이 코드는 실행되지 않음
        // (만약의 경우를 대비한 방어 코드)
        return -1L
    }

    fun cancel(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.i("AlarmScheduler", "Cancelled alarm ID: ${alarm.id}")
    }

    // MainActivity에서 사용하는 요일 인덱스(0=일, 1=월...)를 Calendar 클래스의 상수(SUNDAY=1, MONDAY=2...)로 변환합니다.
    private fun Alarm.getEnabledDays(): List<Int> {
        return this.daysOfWeek
            .split(",")
            .filter { it.isNotEmpty() }
            .map { it.toInt() + 1 } // 0-6 to 1-7
    }

    private fun dayToString(day: Int): String {
        return when (day) {
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "Unknown"
        }
    }
}
