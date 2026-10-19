package com.example.soundoverlay

data class SoundEvent(
    var timestampMs: Long,
    var label: String,
    var confidence: Float,
    var loudnessDb: Float,
    var azimuth: Float // -180..180 degrees (best-effort)
)
