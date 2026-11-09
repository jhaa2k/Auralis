# Auralis Native Android

This is the isolated native Android implementation. The existing React + Capacitor application remains untouched in the repository root.

## Stack
- Kotlin, Jetpack Compose, Material 3
- MVVM / Clean Architecture foundation
- Media3 ExoPlayer + MediaSessionService
- Room, DataStore, WorkManager
- Hilt, Coroutines and Flow
- OpenGL ES and Android MediaCodec/Muxer are planned rendering/export layers

## Build
Open this `native-android` directory in Android Studio, or run:

```bash
cd native-android
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

This is the first native foundation milestone, not feature parity with the full visualizer editor yet. Audio import/playback UI wiring, FFT analysis, visualizer layers, project persistence and video rendering are subsequent milestones.
