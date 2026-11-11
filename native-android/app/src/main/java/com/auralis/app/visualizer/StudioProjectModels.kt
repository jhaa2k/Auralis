package com.auralis.app.visualizer

import android.graphics.Color

/** Persistable project graph. Layer order in this list is the back-to-front Z order. */
enum class LayerType { COMPOSITION, BARS, TEXT, IMAGE, PARTICLES, BLUR, RGB_SPLIT, MOTION_BLUR, MIRROR, CUSTOM_SHADER, AUDIO_PROVIDER }
enum class StudioBlendMode { NORMAL, ADD, SCREEN, MULTIPLY, OVERLAY, DARKEN, LIGHTEN }
enum class ReactiveCurve { LINEAR, SMOOTH }

data class TransformSettings(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val blendMode: StudioBlendMode = StudioBlendMode.NORMAL
)

data class AudioReactiveSettings(
    val enabled: Boolean = true,
    val sensitivity: Float = 1f,
    val attackMs: Int = 45,
    val releaseMs: Int = 180,
    val curve: ReactiveCurve = ReactiveCurve.SMOOTH,
    val measure: String = "Beat"
)

data class LayerEffects(
    val glow: Float = 0f,
    val blur: Float = 0f,
    val repeatX: Int = 1,
    val repeatY: Int = 1,
    val motionAmount: Float = 0f
)

data class Layer(
    val id: String,
    val name: String,
    val type: LayerType,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val transform: TransformSettings = TransformSettings(),
    val audio: AudioReactiveSettings = AudioReactiveSettings(),
    val effects: LayerEffects = LayerEffects(),
    val colorHex: String = "#FFFFFFFF",
    val text: String = "",
    val fontFamily: String = "sans-serif",
    val fontSizeSp: Float = 32f,
    val barCount: Int = 64,
    val barThickness: Float = 0.7f,
    val mirror: Boolean = false,
    val particleSpawnRate: Float = 30f,
    val particleGravity: Float = 0f,
    val imageUri: String? = null,
    val customProperties: Map<String, String> = emptyMap()
)

data class AudioAnalysisSettings(
    val fftSize: Int = 2048,
    val lowHz: Int = 30,
    val highHz: Int = 16000,
    val bassWeight: Float = 1f,
    val midWeight: Float = 1f,
    val trebleWeight: Float = 1f
)

data class ExportSettings(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val bitrate: Int = 8_000_000,
    val includeAudio: Boolean = true
)

data class VisualizerProject(
    val id: String,
    val name: String = "Untitled Project",
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: Int = 30,
    val backgroundColor: Long = Color.BLACK.toLong(),
    val audioSettings: AudioAnalysisSettings = AudioAnalysisSettings(),
    val exportSettings: ExportSettings = ExportSettings(),
    val layers: List<Layer> = emptyList(),
    val selectedLayerId: String? = null
) {
    fun normalized(): VisualizerProject = copy(
        width = width.coerceIn(320, 3840),
        height = height.coerceIn(320, 3840),
        frameRate = frameRate.coerceIn(15, 60),
        layers = layers.map { layer ->
            layer.copy(
                transform = layer.transform.copy(
                    x = layer.transform.x.coerceIn(-2f, 2f),
                    y = layer.transform.y.coerceIn(-2f, 2f),
                    scaleX = layer.transform.scaleX.coerceIn(0.01f, 8f),
                    scaleY = layer.transform.scaleY.coerceIn(0.01f, 8f),
                    opacity = layer.transform.opacity.coerceIn(0f, 1f)
                ),
                audio = layer.audio.copy(
                    sensitivity = layer.audio.sensitivity.coerceIn(0f, 8f),
                    attackMs = layer.audio.attackMs.coerceIn(0, 2000),
                    releaseMs = layer.audio.releaseMs.coerceIn(0, 5000)
                ),
                effects = layer.effects.copy(
                    glow = layer.effects.glow.coerceIn(0f, 1f),
                    blur = layer.effects.blur.coerceIn(0f, 1f),
                    repeatX = layer.effects.repeatX.coerceIn(1, 16),
                    repeatY = layer.effects.repeatY.coerceIn(1, 16),
                    motionAmount = layer.effects.motionAmount.coerceIn(0f, 1f)
                ),
                barCount = layer.barCount.coerceIn(4, 256),
                particleSpawnRate = layer.particleSpawnRate.coerceIn(0f, 1000f)
            )
        }
    )
}
