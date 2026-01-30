package com.nalsil.voicealarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.nalsil.voicealarm.data.AlarmDatabase
import com.nalsil.voicealarm.service.AlarmService
import com.nalsil.voicealarm.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKELOCK_TIMEOUT = 60_000L // 60 seconds
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        Log.i(TAG, "!!! RECEIVED ALARM EVENT !!! ID: $alarmId")

        if (alarmId == -1) {
            Log.e(TAG, "Invalid Alarm ID - cannot start service.")
            return
        }

        // Acquire WakeLock to prevent device from sleeping during rescheduling
        // This is critical for Doze mode reliability
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VoiceAlarm:AlarmReceiverWakeLock"
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT)
        Log.i(TAG, "WakeLock acquired for alarm processing")

        // 1. Start alarm service (TTS playback)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Use goAsync() to keep receiver alive during async work
        // BroadcastReceiver process may be killed after onReceive() returns,
        // so we prevent termination before rescheduling completes
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AlarmDatabase.getDatabase(context)
                val dao = db.alarmDao()

                // Record last triggered time
                dao.updateLastTriggeredAt(alarmId, System.currentTimeMillis())
                Log.i(TAG, "Last triggered time recorded: ID=$alarmId")

                // Reschedule next alarm
                val alarm = dao.getAlarmById(alarmId)
                if (alarm != null && alarm.isEnabled) {
                    val scheduler = AlarmScheduler(context)
                    scheduler.schedule(alarm)
                    Log.i(TAG, "Next alarm rescheduled: ID=$alarmId")
                } else {
                    Log.i(TAG, "Alarm disabled or not found: ID=$alarmId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Alarm processing failed", e)
            } finally {
                // Release WakeLock after all work is done
                if (wakeLock.isHeld) {
                    wakeLock.release()
                    Log.i(TAG, "WakeLock released")
                }
                // Notify system that async work is complete
                pendingResult.finish()
                Log.i(TAG, "PendingResult.finish() called: ID=$alarmId")
            }
        }
    }
}
