package com.auralis.app.visualizer

data class VisualizerSettings(
    val barCount: Int = 64,
    val sensitivity: Float = 1f,
    val smoothing: Float = 0.35f,
    val glow: Float = 0.25f,
    val opacity: Float = 1f,
    val mirror: Boolean = false
) {
    fun sanitized() = copy(
        barCount = barCount.coerceIn(16, 128),
        sensitivity = sensitivity.coerceIn(0.1f, 4f),
        smoothing = smoothing.coerceIn(0f, 0.95f),
        glow = glow.coerceIn(0f, 1f),
        opacity = opacity.coerceIn(0f, 1f)
    )
}
