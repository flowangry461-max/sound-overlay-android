package com.example.soundoverlay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView

    private var projectionData: Intent? = null
    private var projectionResultCode: Int = Activity.RESULT_CANCELED

    private val requestRecordPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            tvStatus.text = "RECORD_AUDIO permission denied"
        }
    }

    private val requestProjection = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            projectionResultCode = result.resultCode
            projectionData = result.data
            tvStatus.text = "Projection granted — starting services"

            // Start services with projection data
            val start = Intent(this, AudioForegroundService::class.java)
            start.putExtra("projection_result_code", projectionResultCode)
            start.putExtra("projection_result_data", projectionData)
            startService(start)

            val overlay = Intent(this, OverlayService::class.java)
            startService(overlay)

            tvStatus.text = "Status: running (using playback capture)"
        } else {
            tvStatus.text = "Projection denied — will use microphone fallback"
            // Start services without projection (microphone fallback)
            val start = Intent(this, AudioForegroundService::class.java)
            startService(start)
            val overlay = Intent(this, OverlayService::class.java)
            startService(overlay)
            tvStatus.text = "Status: running (mic fallback)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)

        btnStart.setOnClickListener {
            // Check overlay permission
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
                tvStatus.text = "Please grant overlay permission and then press Start again"
                return@setOnClickListener
            }

            // Request RECORD_AUDIO permission
            requestRecordPermission.launch(Manifest.permission.RECORD_AUDIO)

            // For Android 10+ we can request audio playback capture via MediaProjection
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val captureIntent = mgr.createScreenCaptureIntent()
                requestProjection.launch(captureIntent)
            } else {
                // Older devices: start services with mic only
                val start = Intent(this, AudioForegroundService::class.java)
                startService(start)
                val overlay = Intent(this, OverlayService::class.java)
                startService(overlay)
                tvStatus.text = "Status: running (mic only)"
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, AudioForegroundService::class.java))
            stopService(Intent(this, OverlayService::class.java))
            tvStatus.text = "Status: stopped"
        }
    }
}
