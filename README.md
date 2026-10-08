# Speed Volume Player — Final prototype

Android app architecture:
- Kotlin + Jetpack Compose
- Media3 ExoPlayer
- MediaSessionService for background playback and lock-screen/media notification controls
- Fused Location Provider for GPS speed
- Smooth speed-based volume control
- User-selectable base volume at 0 km/h
- 80 km/h and above = 100% player volume

Default curve:
0 km/h = 30%
20 = 45%
40 = 65%
60 = 85%
80+ = 100%

Build:
1. Open the folder in Android Studio.
2. Let Gradle sync.
3. Connect an Android phone with USB debugging enabled.
4. Run the app.
5. Grant location/audio permissions.

Safety note: GPS speed can be temporarily inaccurate in tunnels, dense urban areas, or weak-signal conditions. The app smooths volume changes and caps the target at 100%.
