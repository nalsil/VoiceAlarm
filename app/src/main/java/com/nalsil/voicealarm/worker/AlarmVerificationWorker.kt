package com.nalsil.voicealarm.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nalsil.voicealarm.data.AlarmDatabase
import com.nalsil.voicealarm.receiver.AlarmReceiver
import com.nalsil.voicealarm.util.AlarmScheduler
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker that periodically verifies all alarms are properly scheduled.
 *
 * This is a critical safeguard against Android's Doze mode and App Standby:
 * - When the app is paused by "Pause app activity when unused", AlarmReceiver may not fire
 * - WorkManager has special exemptions and can run even in Doze mode
 * - This worker reschedules any alarms that may have been cancelled by the system
 */
class AlarmVerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AlarmVerificationWorker"
        private const val WORK_NAME = "alarm_verification_work"

        /**
         * Schedule periodic alarm verification work.
         * Runs every 15 minutes (minimum interval for WorkManager).
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<AlarmVerificationWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.i(TAG, "Alarm verification work scheduled")
        }

        /**
         * Cancel the periodic verification work.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Alarm verification work cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting alarm verification...")

        return try {
            val db = AlarmDatabase.getDatabase(applicationContext)
            val dao = db.alarmDao()
            val alarms = dao.getAllAlarmsOnce()

            val scheduler = AlarmScheduler(applicationContext)
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            var rescheduledCount = 0

            for (alarm in alarms) {
                if (alarm.isEnabled) {
                    // Check if the alarm is actually scheduled in AlarmManager
                    val isScheduled = isAlarmScheduled(alarm.id)

                    if (!isScheduled) {
                        Log.w(TAG, "Alarm ${alarm.id} was not scheduled! Rescheduling...")
                        scheduler.schedule(alarm)
                        rescheduledCount++
                    }
                }
            }

            if (rescheduledCount > 0) {
                Log.i(TAG, "Rescheduled $rescheduledCount alarms that were missing")
            } else {
                Log.i(TAG, "All ${alarms.count { it.isEnabled }} enabled alarms are properly scheduled")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Alarm verification failed", e)
            Result.retry()
        }
    }

    /**
     * Check if an alarm is currently scheduled in AlarmManager.
     */
    private fun isAlarmScheduled(alarmId: Int): Boolean {
        val intent = Intent(applicationContext, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            alarmId,
            intent,
            flags
        )

        return pendingIntent != null
    }
}
