# Music — Offline Android Music Player

A clean, offline-first Android music player built with Kotlin, Jetpack Compose, and AndroidX Media3.
The app scans audio through Android MediaStore and plays it locally. It does not request Internet access.

## Included in this first clean build

- Local audio library scanning and refresh
- Search across song, artist, and album names
- Songs, albums, artists, and favourites views
- Persistent favourites stored on the device
- Media3 playback service with system media session and playback notification
- Previous, next, play/pause, seek, shuffle, and repeat controls
- Mini-player and full Now Playing screen
- Runtime audio permission handling for Android 10 and newer
- GitHub Actions workflow that builds an installable debug APK

## Build APK with GitHub Actions

1. Upload the contents of this folder to a new GitHub repository. Keep the `.github/workflows/build-apk.yml` file and the `gradlew` wrapper.
2. Open **Actions → Build Music APK → Run workflow** (or push to `main`/`master`).
3. Open the successful run and download the **Music-debug-apk** artifact.
4. Extract `app-debug.apk` from the artifact ZIP and install it on the phone.

The workflow installs Android SDK Platform 37 and Build Tools 36.0.0, then runs the unit-test task and assembles the debug APK.

## Compatibility

- Minimum Android version: Android 10 (API 29)
- Compile SDK: Android API 37
- Target SDK: Android 16 (API 36)
- Media playback: AndroidX Media3 1.11.1
- No Internet permission; no account, cloud, or streaming dependency

## Important testing note

A successful build confirms compilation, not that every hardware decoder and vendor audio driver works on every phone. After installing, test several ordinary MP3/M4A files, headset disconnect/reconnect, screen-off playback, next/previous, seeking, and reopening the app. If any device-specific playback issue occurs, collect its runtime log before changing the player implementation.
