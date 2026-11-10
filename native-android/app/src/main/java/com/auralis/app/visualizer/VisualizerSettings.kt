package com.auralis.app.visualizer

data class VisualizerSettings(
    val barCount: Int = 64,
    val sensitivity: Float = 1f,
    val smoothing: Float = 0.35f,
    val glow: Float = 0.25f,
    val opacity: Float = 1f,
    val mirror: Boolean = false,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val particlesEnabled: Boolean = false,
    val particleCount: Int = 120,
    val particleSpeed: Float = 1f,
    val particleSize: Float = 3f,
    val particleSpread: Float = 0.8f
) {
    fun sanitized() = copy(
        barCount = barCount.coerceIn(16, 128),
        sensitivity = sensitivity.coerceIn(0.1f, 4f),
        smoothing = smoothing.coerceIn(0f, 0.95f),
        glow = glow.coerceIn(0f, 1f),
        opacity = opacity.coerceIn(0f, 1f),
        positionX = positionX.coerceIn(-1f, 2f),
        positionY = positionY.coerceIn(-1f, 2f),
        scale = scale.coerceIn(0.1f, 3f),
        rotation = rotation.coerceIn(-180f, 180f),
        particleCount = particleCount.coerceIn(0, 400),
        particleSpeed = particleSpeed.coerceIn(0.1f, 4f),
        particleSize = particleSize.coerceIn(1f, 12f),
        particleSpread = particleSpread.coerceIn(0.1f, 2f)
    )
}
