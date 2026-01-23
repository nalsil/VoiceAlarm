package com.nalsil.voicealarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nalsil.voicealarm.R
import com.nalsil.voicealarm.data.Alarm
import com.nalsil.voicealarm.data.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class AlarmService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private lateinit var alarm: Alarm
    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        if (alarmId == -1) {
            Log.e("AlarmService", "Invalid Alarm ID, stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AlarmDatabase.getDatabase(applicationContext)
            val fetchedAlarm = db.alarmDao().getAlarmById(alarmId)

            if (fetchedAlarm == null) {
                Log.e("AlarmService", "Alarm not found in DB, stopping service.")
                stopSelf()
                return@launch
            }
            alarm = fetchedAlarm
            playAlarm()
        }

        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("AlarmService", "TTS Initialized")
        } else {
            Log.e("AlarmService", "TTS Initialization failed")
            stopSelf()
        }
    }

    private fun playAlarm() {
        // 1. Vibration
        if (alarm.vibrate) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) {
                Log.e("AlarmService", "Vibration failed", e)
            }
        }

        // 2. Ringtone
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("AlarmService", "Ringtone failed", e)
        }

        // 3. TTS
        CoroutineScope(Dispatchers.Main).launch {
            delay(1500) // Delay for ringtone to start
            speakTime()
        }
    }

    private fun speakTime() {
        ringtone?.stop()
        val locale = Locale(alarm.languageCode)
        if (tts?.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("AlarmService", "Language ${alarm.languageCode} not supported.")
            stopSelf()
            return
        }
        tts?.language = locale

        tts?.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeString = if (alarm.languageCode == "ko") {
            "현재 시간은 ${if (hour >= 12) "오후" else "오전"} ${if (hour % 12 == 0) 12 else hour % 12}시 ${if (minute == 0) "정각" else "${minute}분"}입니다."
        } else {
            "The current time is ${if (hour % 12 == 0) 12 else hour % 12} ${if (minute == 0) "o'clock" else minute} ${if (hour >= 12) "PM" else "AM"}."
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, alarm.volume)
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                Log.d("AlarmService", "TTS Done, stopping service.")
                stopSelf()
            }
            override fun onError(utteranceId: String?) {
                Log.e("AlarmService", "TTS Error, stopping service.")
                stopSelf()
            }
        })

        tts?.speak(timeString, TextToSpeech.QUEUE_FLUSH, params, "ALARM_TTS")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ALARM_SERVICE_CHANNEL",
                "Alarm Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "ALARM_SERVICE_CHANNEL")
            .setContentTitle("Voice Alarm")
            .setContentText("알람이 울리고 있습니다...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this drawable exists
            .build()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ringtone?.stop()
        ringtone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
