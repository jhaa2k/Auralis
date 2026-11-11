package com.auralis.app.visualizer

/**
 * Typed settings for the module canvas. Values are sanitized at the UI/renderer boundary.
 * Rendering backends can consume these models without depending on Compose.
 */
enum class CompositionBlendMode { NORMAL, ADD, SCREEN, MULTIPLY, OVERLAY, LIGHTEN, DARKEN }
enum class ImageFitMode { KEEP_ASPECT_RATIO, CROP_TO_FIT }
enum class ImageSourceMode { GENERATED_ALBUM_ART, CUSTOM_IMAGE }
enum class ParticleImageMode { DEFAULT_DOT, CUSTOM_PNG }

data class CompositionSettings(
    val targetLayerId: Int? = null,
    /** Stable index assigned at creation; never recomputed from current z-order. */
    val creationIndex: Int = 0,
    val blendMode: CompositionBlendMode = CompositionBlendMode.NORMAL,
    val opacity: Float = 1f,
    val useCustomImage: Boolean = false,
    val targetImageUri: String? = null
) {
    fun safe() = copy(creationIndex = creationIndex.coerceAtLeast(0), opacity = opacity.coerceIn(0f, 1f))
}

data class ImageModuleSettings(
    val source: ImageSourceMode = ImageSourceMode.GENERATED_ALBUM_ART,
    val imageUri: String? = null,
    /** KEEP_ASPECT_RATIO letterboxes; CROP_TO_FIT fills bounds without stretching. */
    val fitMode: ImageFitMode = ImageFitMode.KEEP_ASPECT_RATIO,
    val opacity: Float = 1f
) {
    fun safe() = copy(opacity = opacity.coerceIn(0f, 1f))
}

data class BlurModuleSettings(
    val intensity: Float = 0.15f,
    val maxSafeIntensity: Float = 0.65f,
    val enabled: Boolean = true
) {
    fun safe() = copy(intensity = intensity.coerceIn(0f, maxSafeIntensity.coerceIn(0f, 0.65f)))
    val performanceWarning: Boolean get() = intensity > 0.45f
}

data class ParticleModuleSettings(
    val colorArgb: Long = 0xFFFFFFFF,
    val transparency: Float = 1f,
    val vectorAngleDegrees: Float = 0f,
    val imageMode: ParticleImageMode = ParticleImageMode.DEFAULT_DOT,
    val customPngUri: String? = null
) {
    fun safe() = copy(transparency = transparency.coerceIn(0f, 1f), vectorAngleDegrees = vectorAngleDegrees.coerceIn(-360f, 360f))
}

data class BarsModuleSettings(
    val barsCount: Int = 64,
    val segmentsCount: Int = 1,
    val measureWhat: MeasureWhat = MeasureWhat.BEAT,
    val speed: Float = 1f,
    val beatAmount: Float = 0.5f
) {
    fun safe() = copy(barsCount = barsCount.coerceIn(8, 128), segmentsCount = segmentsCount.coerceIn(1, 32), speed = speed.coerceIn(0.1f, 4f), beatAmount = beatAmount.coerceIn(0f, 1f))
    val performanceWarning: Boolean get() = barsCount > 96 || segmentsCount > 24
}

data class TextModuleSettings(
    val songTitle: String = "%title%",
    val artist: String = "%artist%",
    val dynamicText: String = "%title%  •  %artist%",
    val showProgressTime: Boolean = false
) {
    /** Supported metadata tokens are expanded by the metadata binding layer. */
    fun resolve(title: String, artistName: String, elapsed: String, duration: String): String =
        dynamicText.replace("%title%", title).replace("%artist%", artistName)
            .replace("%time%", elapsed).replace("%duration%", duration)
}

data class RgbSplitModuleSettings(
    val amount: Float = 0.15f,
    val beatTriggered: Boolean = true,
    val beatThreshold: Float = 0.7f
) {
    fun safe() = copy(amount = amount.coerceIn(0f, 1f), beatThreshold = beatThreshold.coerceIn(0f, 1f))
}

data class MotionBlurModuleSettings(
    val trailing: Float = 0.2f,
    val smoothing: Float = 0.35f,
    val maxSafeTrailing: Float = 0.65f
) {
    fun safe() = copy(trailing = trailing.coerceIn(0f, maxSafeTrailing.coerceIn(0f, 0.65f)), smoothing = smoothing.coerceIn(0f, 0.95f))
    val performanceWarning: Boolean get() = trailing > 0.45f
}

data class MirrorModuleSettings(
    val enabled: Boolean = true,
    val targetLayerIds: Set<Int> = emptySet(),
    val horizontal: Boolean = true,
    val vertical: Boolean = false,
    val kaleidoscopeSegments: Int = 2
) {
    fun safe() = copy(kaleidoscopeSegments = kaleidoscopeSegments.coerceIn(2, 12))
}

data class CustomShaderModuleSettings(
    val fragmentSource: String = "// Auralis GLSL ES 2.0 fragment shader\\n// Keep the shader within mobile GPU limits.\\nvoid main() {\\n    gl_FragColor = vec4(1.0);\\n}",
    val floatParameters: Map<String, Float> = emptyMap(),
    val enabled: Boolean = false
) {
    fun safe() = copy(floatParameters = floatParameters.mapValues { it.value.coerceIn(-100f, 100f) })
}

/** Creation-order registry for sub-compositions. Removing/reordering layers must not renumber entries. */
class CompositionIndexRegistry {
    private val indices = linkedMapOf<Int, Int>()
    private var nextIndex = 0
    fun register(layerId: Int): Int = indices.getOrPut(layerId) { nextIndex++ }
    fun indexOf(layerId: Int): Int? = indices[layerId]
    fun snapshot(): Map<Int, Int> = indices.toMap()
}
