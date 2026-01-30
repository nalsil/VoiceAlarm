package com.nalsil.voicealarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.nalsil.voicealarm.data.Alarm
import com.nalsil.voicealarm.ui.AlarmViewModel
import com.nalsil.voicealarm.ui.theme.VoiceAlarmTheme
import com.nalsil.voicealarm.worker.AlarmVerificationWorker

private const val PREFS_NAME = "voice_alarm_prefs"
private const val KEY_HIBERNATION_DIALOG_SHOWN = "hibernation_dialog_shown"

// AdMob 배너 광고 ID (테스트용 - 배포 전 실제 ID로 교체 필요)
private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

class MainActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Handle the splash screen transition.
        installSplashScreen()

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Start WorkManager for periodic alarm verification
        // This ensures alarms are rescheduled even if the app is paused by system
        AlarmVerificationWorker.schedule(this)

        enableEdgeToEdge()
        setContent {
            VoiceAlarmTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: AlarmViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var showAppHibernationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.notification_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        checkExactAlarmPermission(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Check battery optimization status
        if (!isIgnoringBatteryOptimizations(context)) {
            showBatteryOptimizationDialog = true
        } else {
            // App hibernation dialog is shown only once (first time)
            val hibernationShown = prefs.getBoolean(KEY_HIBERNATION_DIALOG_SHOWN, false)
            if (!hibernationShown) {
                showAppHibernationDialog = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_alarm_description))
            }
        },
        bottomBar = {
            BannerAd(
                adUnitId = BANNER_AD_UNIT_ID,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { innerPadding ->
        AlarmListScreen(
            viewModel = viewModel,
            onEditAlarm = { alarmToEdit = it },
            modifier = Modifier.padding(innerPadding)
        )

        if (showAddDialog) {
            AlarmEditDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { alarm ->
                    viewModel.addAlarm(alarm)
                    showAddDialog = false
                }
            )
        }

        alarmToEdit?.let { alarm ->
            AlarmEditDialog(
                alarm = alarm,
                onDismiss = { alarmToEdit = null },
                onConfirm = { updatedAlarm ->
                    viewModel.updateAlarm(updatedAlarm)
                    alarmToEdit = null
                }
            )
        }

        // Battery Optimization Dialog
        if (showBatteryOptimizationDialog) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hibernationShown = prefs.getBoolean(KEY_HIBERNATION_DIALOG_SHOWN, false)
            BatteryOptimizationDialog(
                onDismiss = {
                    showBatteryOptimizationDialog = false
                    // After dismissing battery dialog, show app hibernation warning (only if not shown before)
                    if (!hibernationShown) {
                        showAppHibernationDialog = true
                    }
                },
                onOpenSettings = {
                    showBatteryOptimizationDialog = false
                    requestIgnoreBatteryOptimization(context)
                    // Show app hibernation dialog after returning (only if not shown before)
                    if (!hibernationShown) {
                        showAppHibernationDialog = true
                    }
                }
            )
        }

        // App Hibernation Warning Dialog
        if (showAppHibernationDialog) {
            AppHibernationDialog(
                onDismiss = {
                    showAppHibernationDialog = false
                    // Record that the dialog has been shown
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_HIBERNATION_DIALOG_SHOWN, true).apply()
                },
                onOpenSettings = {
                    showAppHibernationDialog = false
                    // Record that the dialog has been shown
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_HIBERNATION_DIALOG_SHOWN, true).apply()
                    openAppSettings(context)
                }
            )
        }
    }
}


private fun checkExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w("MainActivity", "Exact Alarm permission is required. Prompting user.")
            Toast.makeText(context, context.getString(R.string.request_exact_alarm_permission), Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } else {
            Log.i("MainActivity", "Exact Alarm permission is already granted.")
        }
    }
}

/**
 * Check if the app is ignoring battery optimizations (exempt from Doze mode).
 */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Request the user to disable battery optimization for this app.
 * This is allowed by Google Play policy for alarm apps.
 */
@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to open battery optimization settings", e)
        // Fallback to general battery settings
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            Log.e("MainActivity", "Failed to open general battery settings", e2)
        }
    }
}

/**
 * Open the app's settings page where user can disable "Pause app activity when unused".
 */
private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to open app settings", e)
    }
}

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_optimization_title)) },
        text = { Text(stringResource(R.string.battery_optimization_message)) },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.battery_optimization_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_optimization_dismiss))
            }
        }
    )
}

@Composable
fun AppHibernationDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_hibernation_title)) },
        text = { Text(stringResource(R.string.app_hibernation_message)) },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.app_hibernation_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_optimization_dismiss))
            }
        }
    )
}

@Composable
fun AlarmListScreen(
    viewModel: AlarmViewModel,
    onEditAlarm: (Alarm) -> Unit,
    modifier: Modifier = Modifier
) {
    val alarms by viewModel.alarms.collectAsState()

    if (alarms.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.no_alarms_set))
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(alarms) { alarm ->
                AlarmItem(
                    alarm = alarm,
                    onToggle = { viewModel.toggleAlarm(alarm) },
                    onDelete = { viewModel.deleteAlarm(alarm) },
                    onClick = { onEditAlarm(alarm) }
                )
            }
        }
    }
}

@Composable
fun AlarmItem(alarm: Alarm, onToggle: () -> Unit, onDelete: () -> Unit, onClick: () -> Unit) {
    val daysLabels = listOf(
        stringResource(R.string.sun), stringResource(R.string.mon), stringResource(R.string.tue),
        stringResource(R.string.wed), stringResource(R.string.thu), stringResource(R.string.fri),
        stringResource(R.string.sat)
    )
    val activeDays = alarm.daysOfWeek.split(",").filter { it.isNotEmpty() }.map { it.toInt() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stringResource(R.string.language)}: ${if (alarm.languageCode == "ko") stringResource(R.string.korean) else stringResource(R.string.english)}, " +
                            "${stringResource(R.string.volume)}: ${(alarm.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    daysLabels.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            color = if (activeDays.contains(index)) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontWeight = if (activeDays.contains(index)) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(end = 4.dp),
                            fontSize = 12.sp
                        )
                    }
                }
                // 마지막 실행 시간 표시
                alarm.lastTriggeredAt?.let { timestamp ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    Text(
                        text = "${stringResource(R.string.last_triggered)}: ${dateFormat.format(Date(timestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = alarm.isEnabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_description))
                }
            }
        }
    }
}

@Composable
fun AlarmEditDialog(
    alarm: Alarm? = null,
    onDismiss: () -> Unit,
    onConfirm: (Alarm) -> Unit
) {
    val currentTime = remember { Calendar.getInstance() }
    var hour by remember { mutableIntStateOf(alarm?.hour ?: currentTime.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: currentTime.get(Calendar.MINUTE)) }
    var languageCode by remember { mutableStateOf(alarm?.languageCode ?: "ko") }
    var volume by remember { mutableFloatStateOf(alarm?.volume ?: 1.0f) }
    var vibrate by remember { mutableStateOf(alarm?.vibrate ?: true) }
    var selectedDays by remember {
        mutableStateOf(
            alarm?.daysOfWeek?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toSet()
                ?: setOf(1, 2, 3, 4, 5)
        )
    }

    val dayNames = listOf(
        stringResource(R.string.sun), stringResource(R.string.mon), stringResource(R.string.tue),
        stringResource(R.string.wed), stringResource(R.string.thu), stringResource(R.string.fri),
        stringResource(R.string.sat)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (alarm == null) stringResource(R.string.add_alarm) else stringResource(R.string.edit_alarm)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    NumberPicker(value = hour, range = 0..23, onValueChange = { hour = it })
                    Text(" : ", fontSize = 24.sp)
                    NumberPicker(value = minute, range = 0..59, onValueChange = { minute = it })
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.days), fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dayNames.forEachIndexed { index, name ->
                        DayChip(
                            name = name,
                            isSelected = selectedDays.contains(index),
                            onClick = {
                                selectedDays = if (selectedDays.contains(index)) {
                                    selectedDays - index
                                } else {
                                    selectedDays + index
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = languageCode == "ko", onClick = { languageCode = "ko" })
                    Text(stringResource(R.string.korean))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = languageCode == "en", onClick = { languageCode = "en" })
                    Text(stringResource(R.string.english))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("${stringResource(R.string.volume)}: ${(volume * 100).toInt()}%", fontWeight = FontWeight.Bold)
                Slider(value = volume, onValueChange = { volume = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vibrate, onCheckedChange = { vibrate = it })
                    Text(stringResource(R.string.vibrate))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    alarm?.copy(
                        hour = hour,
                        minute = minute,
                        daysOfWeek = selectedDays.sorted().joinToString(","),
                        languageCode = languageCode,
                        volume = volume,
                        vibrate = vibrate
                    ) ?: Alarm(
                        hour = hour,
                        minute = minute,
                        daysOfWeek = selectedDays.sorted().joinToString(","),
                        languageCode = languageCode,
                        volume = volume,
                        vibrate = vibrate
                    )
                )
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DayChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = if (isSelected) Color.White else Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NumberPicker(value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { if (value < range.last) onValueChange(value + 1) else onValueChange(range.first) }) {
            Text("▲")
        }
        Text(text = String.format("%02d", value), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = { if (value > range.first) onValueChange(value - 1) else onValueChange(range.last) }) {
            Text("▼")
        }
    }
}

/**
 * AdMob Banner Ad Composable
 */
@Composable
fun BannerAd(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
