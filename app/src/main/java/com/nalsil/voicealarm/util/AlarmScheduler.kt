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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("AlarmScheduler", "PERMISSION CHECK: canScheduleExactAlarms() is DENIED. Alarm will likely not fire.")
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Force the use of the default system timezone to fix calculation errors
        val timeZone = TimeZone.getDefault()
        val calendar = Calendar.getInstance(timeZone).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // --- ENHANCED DEBUG LOGGING ---
        val now = System.currentTimeMillis()
        val scheduledTime = calendar.timeInMillis
        val diffMinutes = (scheduledTime - now) / 60000.0
        // Use a formatter that shows the timezone
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
        
        Log.i("AlarmScheduler", "=================================================")
        Log.i("AlarmScheduler", "Scheduling Alarm ID: ${alarm.id} for H:${alarm.hour} M:${alarm.minute}")
        Log.i("AlarmScheduler", "  - System Default TimeZone: ${timeZone.id} (Offset: ${timeZone.getOffset(now) / 3600000} hrs)")
        Log.i("AlarmScheduler", "  - Calendar's TimeZone: ${calendar.timeZone.id}")
        Log.i("AlarmScheduler", "  - Current Time: ${sdf.format(Date(now))}")
        Log.i("AlarmScheduler", "  - Scheduled For: ${sdf.format(Date(scheduledTime))}")
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
}
