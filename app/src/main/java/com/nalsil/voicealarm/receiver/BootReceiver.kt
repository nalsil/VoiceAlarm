package com.nalsil.voicealarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nalsil.voicealarm.data.AlarmDatabase
import com.nalsil.voicealarm.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device boot completed, rescheduling alarms...")
            val scheduler = AlarmScheduler(context)
            val db = AlarmDatabase.getDatabase(context)

            CoroutineScope(Dispatchers.IO).launch {
                val alarms = db.alarmDao().getAllAlarms().first()
                for (alarm in alarms) {
                    if (alarm.isEnabled) {
                        scheduler.schedule(alarm)
                    }
                }
                Log.i("BootReceiver", "Rescheduled ${alarms.size} alarms.")
            }
        }
    }
}
