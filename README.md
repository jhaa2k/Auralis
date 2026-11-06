# Auralis
Real-time audio visualization app for Android "See Your Sound."

## Build an Android test APK

The Android project is packaged with Capacitor. To build a debug APK locally,
install Android Studio and its Android SDK, then run:

```bash
npm ci
npm run android:apk
```

The APK is created at
`android/app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions also builds this APK on pushes to `main`. To start a build
manually, open the repository's **Actions** tab and run **Build Android APK**.
Download the `audiovis-debug-apk` artifact from the completed workflow.

Debug APKs are for testing and are not signed for Play Store release.
