# sound-overlay-android

Prototype Android overlay that listens for sounds (ML classifier) and draws directional indicators over other apps. This is a starter scaffold (Kotlin) using a foreground audio service and an overlay view. The repository intentionally does NOT include any pre-trained large TFLite models — see instructions below to add one (YAMNet recommended).

Important
- Intended uses: accessibility, research, demo, or single-player/local. Do NOT use this to cheat in online multiplayer games or for malicious activity.
- Accurate 3D localization is not possible using a single smartphone microphone alone. This scaffold uses a classifier to identify events and loudness to estimate proximity. Exact positioning requires cooperation from the source app or specialized hardware.

Quick start
1. Install Android Studio (Arctic Fox or later recommended).
2. Clone this repo and open it in Android Studio.
3. Add a TFLite sound-event model (recommended: YAMNet) at: app/src/main/assets/yamnet.tflite
   - If you don't want to use a model, the service will still compute RMS loudness and fire heuristic events.
4. Build and install the APK on a device. The app requests RECORD_AUDIO and requires you to enable the overlay permission in Settings.

Files of interest
- app/src/main/java/com/example/soundoverlay/AudioForegroundService.kt — records audio, computes features, and (optionally) runs a TFLite model.
- app/src/main/java/com/example/soundoverlay/OverlayService.kt — creates an overlay and draws directional lines.
- app/src/main/java/com/example/soundoverlay/OverlayView.kt — custom View that renders events and throttles spam.
- app/src/main/res/layout/activity_main.xml — simple UI to start/stop the service and adjust settings.

How to provide a model
- Download YAMNet converted to TFLite or another small sound-event model. Place the .tflite in app/src/main/assets/yamnet.tflite.
- The AudioForegroundService contains a simple TFLite interpreter loader — you may need to adapt input preprocessing depending on your model.

Limitations and ethics
- This prototype reads microphone audio only — it does not access other apps' internal audio buffers.
- Use responsibly. The author is not responsible for misuse.

License: MIT
