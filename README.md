# ADITYA MUSIC - Premium Android Audio Player 🎵

**Aditya Music** is a production-quality, lightweight, offline-first Android audio player built with Kotlin, Jetpack Compose, Material Design 3, Android Media3 (ExoPlayer), Room Database, and modern Clean Architecture.

Developed by **Aditya**.

---

## ✨ Key Features

- 🎧 **High-Fidelity Audio Engine**: Android Media3 ExoPlayer supporting MP3, WAV, FLAC, AAC, M4A, OGG.
- 🚀 **100% Offline & Private**: Zero internet permissions, no ads, no trackers, no accounts.
- ⚡ **Background Playback & MediaSession**: Lock screen controls, rich status notification with album art, Bluetooth controls, wired headset plug/unplug audio focus management.
- 🎨 **Material Design 3**: Dynamic themes (Light, Dark, AMOLED Black), responsive layouts for small, regular, and large screens.
- 🎛️ **5-Band Equalizer & Bass Boost**: Real audio session hardware equalizer with Rock, Pop, Classical, Jazz, Vocal presets and custom gain curves.
- ⏱️ **Sleep Timer**: Gentle fade-out countdown timer (5m, 10m, 15m, 30m, 45m, 60m, custom).
- 📁 **Smart MediaStore Scanner**: Rapid background discovery of device tracks without filesystem bloat.
- 📋 **Playlist & Favorites Engine**: Persistent local storage powered by Room Database with full drag-to-reorder, search, and sort.

---

## 🛠️ Build APK via GitHub Actions (Recommended)

1. Push this folder to a GitHub repository.
2. The workflow file `.github/workflows/build_apk.yml` will trigger automatically on `main` branch push.
3. Once completed, go to **Actions** > Click the latest workflow run > Download the **Aditya-Music-Debug-APK** artifact.

---

## 💻 Build Locally via Android Studio

1. Open Android Studio (Hedgehog or newer).
2. Select **Open Project** and navigate to this directory.
3. Let Gradle sync and JDK 17 configure.
4. Click **Run** or execute:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

© 2026 Aditya. All Rights Reserved.
