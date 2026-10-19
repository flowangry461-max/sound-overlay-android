# sound-overlay-android

Prototype Android overlay that listens for sounds (ML classifier or playback capture) and draws directional indicators over other apps. This is a starter scaffold (Kotlin) using a foreground audio service and an overlay view.

Important
- Intended uses: accessibility, research, demo, or single‑player/local. Do NOT use this to cheat in online multiplayer games or for malicious activity.
- Accurate 3D localization is not possible using a single smartphone microphone alone. This scaffold uses a classifier to identify events and loudness to estimate proximity. Exact positioning requires cooperation from the source app.

New: Playback capture (Android 10+)
- On Android 10 (API 29) and newer, the app can request a MediaProjection token and attempt AudioPlaybackCapture to capture other apps' playback audio (when the source app has not opted out).
- If playback capture is available and allowed by the source app, the service will use it; if the source app blocks capture or the permission/request fails, the service falls back to the device microphone.

Quick start
1. Install Android Studio (Arctic Fox or later recommended).
2. Clone this repo and open it in Android Studio.
3. Add a TFLite sound‑event model (recommended: YAMNet) to: app/src/main/assets/yamnet.tflite
   - If you don't add a model, the service will still compute RMS loudness and fire heuristic events.
4. Build and install the APK on a device. The app requests RECORD_AUDIO and requires you to enable the overlay permission in Settings. On Android 10+ it will also prompt for screen capture permission (MediaProjection) to enable playback capture.

Behavior
- Attempt playback capture first (Android 10+). If the playback source app has opted out of capture, the playback capture attempt will fail and the service will fall back to microphone capture.
- The overlay draws lines representing detected events. Loudness affects line length/thickness (closer = shorter & thicker by default).

Files of interest
- app/src/main/java/com/example/soundoverlay/AudioForegroundService.kt — uses AudioPlaybackCapture when available with mic fallback; computes loudness and posts events.
- app/src/main/java/com/example/soundoverlay/MainActivity.kt — requests overlay, RECORD_AUDIO, and MediaProjection (Android 10+) permissions and starts services.
- app/src/main/java/com/example/soundoverlay/OverlayService.kt — creates an overlay and draws directional lines.
- app/src/main/java/com/example/soundoverlay/OverlayView.kt — custom View that renders events and throttles spam.

Limitations and ethics
- This prototype reads microphone or captured playback audio only — it does not access other apps' internal buffers except via the supported AudioPlaybackCapture API and only if the source app allows it.
- Using this against other players in online games is against terms of service for most games; I’ll add a warning in the README.

License: MIT
