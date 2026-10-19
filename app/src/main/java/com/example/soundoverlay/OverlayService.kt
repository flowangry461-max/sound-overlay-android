package com.example.soundoverlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

class OverlayService: Service() {

    private val TAG = "OverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = OverlayView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager?.addView(overlayView, params)

        // Subscribe to events
        OverlayEventBus.register { evt ->
            overlayView?.post { overlayView?.addEvent(evt) }
        }

        Log.i(TAG, "Overlay added to window manager")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager?.removeView(overlayView)
        } catch (e: Exception) { }
        OverlayEventBus.unregisterAll()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
