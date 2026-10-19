package com.example.soundoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList

class OverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val events = CopyOnWriteArrayList<SoundEvent>()

    fun addEvent(evt: SoundEvent) {
        // Basic spam control and prioritization can be handled here
        events.add(evt)
        // Remove after a short time
        postDelayed({ events.remove(evt); invalidate() }, 800)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Draw each event as a radial line from center
        val cx = w / 2
        val cy = h / 2

        for (evt in events) {
            // Map loudness to length: louder = shorter (closer)
            val loudNorm = (evt.loudnessDb + 100) / 100f // roughly 0..1
            val length = (0.2f + (1 - loudNorm) * 0.6f) * Math.min(w, h)

            // Azimuth not available in mono mic; use a simple random or fixed pattern for demo
            val angle = (evt.azimuth + 180) * Math.PI / 180.0
            val endX = (cx + Math.cos(angle) * length).toFloat()
            val endY = (cy + Math.sin(angle) * length).toFloat()

            // Thickness map
            paint.strokeWidth = 4f + (1 - loudNorm) * 12f

            canvas.drawLine(cx, cy, endX, endY, paint)
        }
    }
}
