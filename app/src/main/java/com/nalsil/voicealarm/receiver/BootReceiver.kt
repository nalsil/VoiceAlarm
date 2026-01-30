package com.nalsil.voicealarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.nalsil.voicealarm.data.AlarmDatabase
import com.nalsil.voicealarm.util.AlarmScheduler
import com.nalsil.voicealarm.worker.AlarmVerificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val WAKELOCK_TIMEOUT = 60_000L // 60 seconds
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device boot completed, rescheduling alarms...")

            // Acquire WakeLock to ensure rescheduling completes
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoiceAlarm:BootReceiverWakeLock"
            )
            wakeLock.acquire(WAKELOCK_TIMEOUT)

            // Start WorkManager for periodic alarm verification
            AlarmVerificationWorker.schedule(context)

            val scheduler = AlarmScheduler(context)
            val db = AlarmDatabase.getDatabase(context)
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarms = db.alarmDao().getAllAlarms().first()
                    var scheduledCount = 0
                    for (alarm in alarms) {
                        if (alarm.isEnabled) {
                            scheduler.schedule(alarm)
                            scheduledCount++
                        }
                    }
                    Log.i(TAG, "Rescheduled $scheduledCount enabled alarms out of ${alarms.size} total.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarms after boot", e)
                } finally {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                    pendingResult.finish()
                }
            }
        }
    }
}
