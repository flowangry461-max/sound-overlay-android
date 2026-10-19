package com.example.soundoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class AudioForegroundService : Service() {

    private val TAG = "AudioForegroundService"
    private var running = false
    private val executor = Executors.newSingleThreadExecutor()

    // TFLite interpreter (loaded lazily if model exists in assets)
    private var tflite: Interpreter? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel("audio_fg", "Audio Service", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
            val notification: Notification = NotificationCompat.Builder(this, "audio_fg")
                .setContentTitle("Sound Overlay")
                .setContentText("Listening for sounds…")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
            startForeground(1, notification)
        }

        // Try to load tflite model if present
        try {
            val assetManager = assets
            val modelStream = assetManager.open("yamnet.tflite")
            val modelBytes = modelStream.readBytes()
            val bb = ByteBuffer.allocateDirect(modelBytes.size)
            bb.order(ByteOrder.nativeOrder())
            bb.put(modelBytes)
            bb.rewind()
            tflite = Interpreter(bb)
            Log.i(TAG, "Loaded TFLite model (yamnet.tflite) from assets")
        } catch (e: Exception) {
            Log.i(TAG, "No model found in assets; proceeding with heuristic detection")
        }

        startRecordingLoop()
    }

    private fun startRecordingLoop() {
        running = true
        executor.execute {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)

            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize)

            val audioBuffer = ShortArray(bufferSize)

            try {
                audioRecord.startRecording()
                while (running) {
                    val read = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                    if (read > 0) {
                        // Compute RMS loudness
                        var sum = 0L
                        for (i in 0 until read) {
                            sum += (audioBuffer[i].toInt() * audioBuffer[i].toInt())
                        }
                        val rms = Math.sqrt(sum.toDouble() / read)
                        val db = 20 * Math.log10(rms + 1e-6)

                        // Simple heuristic threshold
                        if (db > -50) {
                            // Create SoundEvent
                            val evt = SoundEvent(
                                timestampMs = System.currentTimeMillis(),
                                label = if (tflite != null) "ml_candidate" else "loud",
                                confidence = 1.0f,
                                loudnessDb = db.toFloat(),
                                azimuth = 0f // No directionality from mono mic
                            )

                            // If model exists, run model on the frame (placeholder)
                            if (tflite != null) {
                                // TODO: preprocess audioBuffer to model input shape (e.g., waveform->log-mel)
                                // Placeholder: mark as candidate
                                evt.label = "candidate"
                            }

                            // Send to overlay via a simple local broadcast (app-internal)
                            OverlayEventBus.post(evt)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }

    override fun onDestroy() {
        running = false
        tflite?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
