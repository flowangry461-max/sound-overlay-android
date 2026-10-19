package com.example.soundoverlay

/**
 * Simple in-app event bus for sending SoundEvent objects from the audio service to the overlay.
 * This is intentionally tiny and app-internal only.
 */
object OverlayEventBus {
    private val listeners = mutableListOf<(SoundEvent) -> Unit>()

    fun register(fn: (SoundEvent) -> Unit) {
        listeners.add(fn)
    }

    fun unregisterAll() {
        listeners.clear()
    }

    fun post(evt: SoundEvent) {
        for (l in listeners) {
            try { l(evt) } catch (_: Exception) { }
        }
    }
}
