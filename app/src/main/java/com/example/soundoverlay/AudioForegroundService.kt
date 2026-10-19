package com.example.soundoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
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

    // MediaProjection info (if available)
    private var projectionResultCode: Int = -1
    private var projectionData: Intent? = null
    private var mediaProjection: MediaProjection? = null

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Read projection info if provided
        try {
            if (intent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (intent.hasExtra("projection_result_code") && intent.hasExtra("projection_result_data")) {
                    projectionResultCode = intent.getIntExtra("projection_result_code", -1)
                    projectionData = intent.getParcelableExtra("projection_result_data")
                    if (projectionData != null) {
                        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjection = mgr.getMediaProjection(projectionResultCode, projectionData!!)
                        Log.i(TAG, "Obtained MediaProjection — attempting playback capture")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain MediaProjection: ${e.message}")
            mediaProjection = null
        }

        startRecordingLoop()
        return START_STICKY
    }

    private fun startRecordingLoop() {
        running = true
        executor.execute {
            val sampleRate = 16000
            val usePlaybackCapture = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null)

            // Determine channel configuration
            val channelConfig = if (usePlaybackCapture) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO

            val minBuf = AudioRecord.getMinBufferSize(sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = if (minBuf > 0) minBuf else sampleRate * 2

            var audioRecord: AudioRecord? = null

            try {
                if (usePlaybackCapture) {
                    try {
                        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build()

                        val format = AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()

                        audioRecord = AudioRecord.Builder()
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(bufferSize)
                            .setAudioPlaybackCaptureConfig(config)
                            .build()

                        audioRecord.startRecording()
                        Log.i(TAG, "Started playback capture audio record")
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Playback capture blocked by source app or permission: ${e.message}")
                        audioRecord = null
                    } catch (e: Exception) {
                        Log.w(TAG, "Playback capture failed: ${e.message}")
                        audioRecord = null
                    }
                }

                // If playback capture not available or failed, fall back to MIC
                if (audioRecord == null) {
                    val micBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT)
                    val micBuffer = if (micBufferSize > 0) micBufferSize else sampleRate * 2
                    audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        micBuffer)
                    audioRecord.startRecording()
                    Log.i(TAG, "Started microphone audio record (fallback)")
                }

                val channelCount = if (usePlaybackCapture) 2 else 1
                val audioBuffer = ShortArray(bufferSize / 2)

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
                                azimuth = 0f // Directionality not implemented; could use stereo difference if available
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
                try {
                    audioRecord?.stop()
                } catch (e: Exception) { }
                try {
                    audioRecord?.release()
                } catch (e: Exception) { }
            }
        }
    }

    override fun onDestroy() {
        running = false
        mediaProjection?.stop()
        tflite?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
