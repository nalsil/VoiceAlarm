# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a specific unit test class
./gradlew testDebugUnitTest --tests "com.nalsil.voicealarm.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Install debug APK to connected device
./gradlew installDebug
```

## Architecture Overview

This is a native Android alarm app with voice announcement capabilities, built with **Kotlin + Jetpack Compose** following **MVVM architecture**.

### Data Flow

```
UI (Composables) ←→ AlarmViewModel ←→ AlarmDao ←→ Room Database
                          ↓
                   AlarmScheduler → AlarmManager
                          ↓
                   AlarmReceiver → AlarmService (TTS + Vibration)
```

### Key Components

| Layer | Component | Purpose |
|-------|-----------|---------|
| **Data** | `Alarm.kt` | Room entity with time, days, language, volume, vibration settings |
| **Data** | `AlarmDao.kt` | Flow-based queries returning reactive data |
| **Data** | `AlarmDatabase.kt` | Room database singleton |
| **UI** | `MainActivity.kt` | Single activity containing all Compose UI |
| **ViewModel** | `AlarmViewModel.kt` | CRUD operations, schedules alarms on changes |
| **Service** | `AlarmService.kt` | Foreground service for TTS announcement and vibration |
| **Util** | `AlarmScheduler.kt` | Calculates next alarm time, schedules via AlarmManager |
| **Receiver** | `AlarmReceiver.kt` | Receives alarm broadcast, starts service |
| **Receiver** | `BootReceiver.kt` | Reschedules all alarms after device boot |

### Day-of-Week Handling

The app uses different indexing systems that must be converted:
- **UI/Database**: 0-based (0=Sunday, 1=Monday, ..., 6=Saturday), stored as comma-separated string `"0,1,2,3,4"`
- **Calendar constants**: 1-based (1=Sunday, 2=Monday, ..., 7=Saturday)

`AlarmScheduler.convertDayIndex()` handles this conversion.

### Multilingual TTS

Alarm announcements support Korean (`"ko"`) and English (`"en"`):
- Language code stored per-alarm in database
- `AlarmService` creates locale-specific context for time formatting
- String resources in `values/` (English) and `values-ko/` (Korean)

## Technology Stack

- **Kotlin 2.0.0** with Java 21 toolchain
- **Jetpack Compose** (Material3) with Compose BOM 2024.10.01
- **Room 2.8.4** for SQLite persistence with KSP code generation
- **Coroutines** for async operations
- **Target SDK 35**, Min SDK 24

## Required Permissions

The app requires several system permissions for alarm functionality:
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` - Precise alarm scheduling
- `FOREGROUND_SERVICE_SPECIAL_USE` - Reliable alarm execution
- `RECEIVE_BOOT_COMPLETED` - Reschedule alarms after device restart
- `POST_NOTIFICATIONS` - Android 13+ notification permission (runtime request)
