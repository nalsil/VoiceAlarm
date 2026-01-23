package com.nalsil.voicealarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nalsil.voicealarm.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        Log.i("AlarmReceiver", "!!! RECEIVED ALARM EVENT !!! ID: $alarmId")

        if (alarmId == -1) {
            Log.e("AlarmReceiver", "Invalid Alarm ID - cannot start service.")
            return
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
