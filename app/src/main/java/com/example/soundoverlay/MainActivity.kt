package com.example.soundoverlay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView

    private val requestRecordPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            tvStatus.text = "RECORD_AUDIO permission denied"
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

            val start = Intent(this, AudioForegroundService::class.java)
            startService(start)
            val overlay = Intent(this, OverlayService::class.java)
            startService(overlay)
            tvStatus.text = "Status: running"
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, AudioForegroundService::class.java))
            stopService(Intent(this, OverlayService::class.java))
            tvStatus.text = "Status: stopped"
        }
    }
}
