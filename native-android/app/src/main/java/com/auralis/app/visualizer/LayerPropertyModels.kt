package com.auralis.app.visualizer

/**
 * Typed property schema for Avee-style visualizer layers.
 *
 * These models preserve the reference defaults and option names. A property is
 * not considered operational until a renderer or audio processor consumes it.
 */
enum class MeasureWhat(val label: String) {
    NOTHING("Nothing"),
    BEAT("Beat"),
    TOTAL_TIME("TotalTime"),
    TOTAL_TIME_BACKWARD("TotalTimeBackward"),
    TOTAL_TIME_WHEN_PLAYING("TotalTimeWhenPlaying"),
    TOTAL_TIME_AND_BEAT("TotalTimeAndBeat"),
    TRACK_POSITION("TrackPosition"),
    BEAT_RANDOM_SHAKE("BeatRandomShake"),
    BEAT_CAM_SHAKE_ROT_MORE("BeatCamShakeRotMore"),
    BEAT_CAM_SHAKE_ROT_LESS("BeatCamShakeRotLess"),
    CONSTANT_SHAKE_ROT_MORE("ConstantShakeRotMore"),
    CONSTANT_SHAKE_ROT_LESS("ConstantShakeRotLess"),
    BEAT_TRIGGER_ANIM("BeatTriggerAnim");

    companion object {
        val dropdownOptions: List<String> = entries.map { it.label }
    }
}

enum class LayerBlendMode(val label: String) {
    ALPHA("Alpha"), ADD("Add"), SCREEN("Screen"), PRE_MUL_ALPHA("PreMulAlpha")
}

data class MeasureAmount(
    val what: MeasureWhat = MeasureWhat.NOTHING,
    val amountA: Float = 0.5f,
    val amountB: Float = 0.5f
)

data class LayerTransformProperties(
    val tag: String = "",
    val visible: Boolean = true,
    val blendMode: LayerBlendMode = LayerBlendMode.ALPHA,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val anchorX: Float? = null,
    val anchorY: Float? = null,
    val measurePosition: MeasureAmount = MeasureAmount(),
    val alignmentX: Float = 0.5f,
    val alignmentY: Float = 0.5f,
    val scaleUniform: Boolean = false,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val measureScale: MeasureAmount = MeasureAmount(),
    val rotation: Float = 0f,
    val measureRotation: MeasureAmount = MeasureAmount()
)

data class TextLayerProperties(
    val text: String = "{ArtistOrTitle}",
    val colorArgb: Long = 0xFFFFFFFF,
    val fontSize: Float = 30f,
    val typefaceName: String = ""
)

data class BarsLayerProperties(
    val flipInput: Boolean = false,
    val heightScale: Float = 10f,
    val minHeightScale: Float = 0f,
    val maxHeightScale: Float = 1f,
    val shapePath: String = "HorizontalLine",
    val colorBlendOffset: Float = 0f,
    val segmentOneShape: String = "Line",
    val segmentOneColorFrom: Long = 0xFFFFFFFF,
    val segmentOneColorTo: Long = 0xFFFFFFFF,
    val segmentOneFixedHeight: Float = 0f,
    val segmentOneHeightMultiplier: Float = 1f,
    val segmentOneMirror: Boolean = false,
    val segmentOneFlipEveryOther: Boolean = false,
    val segmentTwoShape: String = "None",
    val softness: Int = 8,
    val reactionDelay: Int = 0,
    val reactionAccumulatedDelay: Int = 0,
    val audioProviderIndex: Int = 1
)

data class ParticleLayerProperties(
    val colorArgb: Long = 0xFFFFFFFF,
    val countLimit: Int = 1500,
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    val perspectiveDepth: Float = 900f,
    val perspectiveFov: Float = 150f,
    val spawnArea: String = "Rectangle",
    val vectorRelativeToCenter: Boolean = false,
    val useRandomVectorInstead: Boolean = false,
    val vectorAngle: Float = 270f,
    val vectorAngleZ: Float = 1f,
    val forceField: String = "None",
    val customImageUri: String? = null,
    val particleScale: Float = 2f,
    val trailLength: Float = 0f,
    val colorFrom: Long = 0xFFFFFFFF,
    val colorTo: Long = 0x7F808080,
    val fadeInOutTime: Float = 0.1f,
    val velocityAngle: Boolean = true,
    val sizeAspectRatio: Float = 1f,
    val startSize: Float = 0f,
    val fadeInSize: Float = 4f,
    val endSize: Float = 4f,
    val endSizeRandom: Float = 10f,
    val endSizeZFactor: Float = 0f,
    val nearCameraFadeOutDistance: Float = 40f,
    val spawnTime: Float = 0.020f,
    val measureOverallSpeed: MeasureAmount = MeasureAmount(MeasureWhat.NOTHING, 1.5f, 1.5f),
    val speed: Float = 100f,
    val speedRandom: Float = 60f,
    val lifetime: Float = 8f,
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
    val sideSineWaveFrequency: Float = 5f,
    val sideSineWaveFrequencyRandom: Float = 0f,
    val sideSineWaveMagnitude: Float = 0f,
    val sideSineWaveMagnitudeRandom: Float = 4f
)

data class ImageLayerProperties(
    val customImageUri: String? = null,
    val measureAnimationSpeed: MeasureAmount = MeasureAmount(),
    val keepAspectRatioAndCropToFit: Boolean = true,
    val keepAspectRatio: Boolean = true,
    val blurredBorder: Boolean = false,
    val shape: String = "None",
    val maskImageUri: String? = null,
    val maskMode: String = "Transparency",
    val colorArgb: Long = 0xFFFFFFFF,
    val colorToArgb: Long = 0xFFFFFFFF,
    val measureColorBlend: MeasureAmount = MeasureAmount(),
    val saturation: Float = 1f,
    val colorKeyEnabled: Boolean = false,
    val autoDetectColorKey: Boolean = true,
    val colorKeyArgb: Long = 0xFF00FF00,
    val transparencyStrength: Float = 1f,
    val opacityStrength: Float = 1f,
    val blurEnabled: Boolean = true,
    val blurDivider: Int = 10,
    val blurRadius: Int = 9,
    val albumArtHintShiftHue: Boolean = false,
    val albumArtHintNoText: Boolean = false,
    val albumArtHintForceGenerate: Boolean = false,
    val generatedAlbumArtColor: Long = 0xFFFFFFFF
)

data class BlurLayerProperties(
    val blendModeContent: LayerBlendMode = LayerBlendMode.PRE_MUL_ALPHA,
    val colorArgb: Long = 0xFFFF0017,
    val sourceCompositionIndex: Int = 2,
    val maskImageUri: String? = null,
    val showUnblurredContent: Boolean = true,
    val showUnblurredContentUnder: Boolean = true,
    val blurRadius: Float = 2f,
    val blurMultiplier: Float = 4.1f,
    val layer1ScaleX: Float = 1f,
    val layer1ScaleY: Float = 1f,
    val layer2ScaleX: Float = 0f,
    val layer2ScaleY: Float = 0f,
    val layer3ScaleX: Float = 0f,
    val layer3ScaleY: Float = 0f,
    val layer1Color: Long = 0xFFFFFFFF,
    val layer2Color: Long = 0xFFFFFFFF,
    val layer3Color: Long = 0xFFFFFFFF
)

data class RgbSplitLayerProperties(
    val blendModeContent: LayerBlendMode = LayerBlendMode.SCREEN,
    val colorArgb: Long = 0xFFDDDDDD,
    val targetImage: Int = 1,
    val showUnblurredContent: Boolean = false,
    val showUnblurredContentUnder: Boolean = false,
    val splitMultiplier: Float = 0f,
    val splitAmountX: Float = 0.5f,
    val splitAmountY: Float = 0.5f,
    val splitColor0: Long = 0xFFFFFFFF,
    val splitColor1: Long = 0xFFFFFFFF,
    val splitColor2: Long = 0xFFFFFFFF
)

data class MotionBlurLayerProperties(
    val blurAmountMultiplier: Float = 1f,
    val motionSource: String = "EffectTransform",
    val relativeMotionMode: Boolean = true,
    val blendModeContent: LayerBlendMode = LayerBlendMode.ADD,
    val colorArgb: Long = 0xFFFFFFFF,
    val targetImage: Int = 1,
    val showUnblurredContent: Boolean = false,
    val showUnblurredContentUnder: Boolean = false
)

data class AudioProviderProperties(
    val sampleProvider: String = "Spectrum",
    val sampleOutCount: Int = 64,
    val mirrorSamples: Boolean = false,
    val repeatSamples: Int = 1,
    val startAndEndGap: Int = 0,
    val smooth: Float = 0.8f,
    val preSmooth: Float = 1f,
    val filterRadius: Int = 1,
    val filterStrength: Float = 1f,
    val aWeight: Float = 0.2f,
    val outputMultiplier: Float = 1f,
    val lowerHz: Float = 20f,
    val higherHz: Float = 18000f,
    val hzLinearFactor: Float = 0.06f,
    val frequencyShift: Float = 0f,
    val beatSmooth: Float = 0.4f,
    val beatRangeBarFirst: Float = 0f,
    val beatRangeBarLast: Float = 0.2f,
    val beatRangeValueLower: Float = 0.7f,
    val beatRangeValueHigher: Float = 35f,
    val fftSize: Int = 11
)
