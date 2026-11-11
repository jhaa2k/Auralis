package com.auralis.app.visualizer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.sqrt

class VisualizerSurface(context: Context) : GLSurfaceView(context) {
    private val visualizerRenderer = SpectrumGlRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(visualizerRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun submitSpectrum(values: FloatArray) = visualizerRenderer.submit(values)
    fun updateSettings(settings: VisualizerSettings) = visualizerRenderer.update(settings)
    fun updateLayers(settings: List<VisualizerSettings>) = visualizerRenderer.updateLayers(settings)
    fun applyCustomShader(vertexSource: String, fragmentSource: String, onResult: (Boolean, String?) -> Unit) {
        queueEvent {
            val error = visualizerRenderer.applyCustomShader(vertexSource, fragmentSource)
            post { onResult(error == null, error) }
        }
    }
}

private class SpectrumGlRenderer : GLSurfaceView.Renderer {
    @Volatile private var spectrum = FloatArray(128)
    @Volatile private var settings = listOf(VisualizerSettings())
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var pointSizeHandle = 0
    private var roundPointHandle = 0
    private var offsetHandle = 0
    private var tintHandle = 0
    private var particleSizeHandle = -1
    private var particleRotationHandle = -1
    private var useParticleSizeHandle = -1
    private var pointAspectHandle = -1
    private var vertexBuffer: FloatBuffer? = null
    private var particleBuffer: FloatBuffer? = null
    private val vertexCapacity = 128 * (3 * 20 * 4 + 4) * 18
    private val rendererStartNanos = System.nanoTime()
    private var previousBassEnergy = 0f
    private var bassNoiseFloor = 0.025f
    private var beatPulse = 0f
    private var lastBeatNanos = 0L
    private val previousBarHeights = mutableMapOf<Int, FloatArray>()

    fun submit(values: FloatArray) {
        spectrum = values.copyOf()
    }

    fun update(value: VisualizerSettings) {
        settings = listOf(value.sanitized())
    }

    fun updateLayers(values: List<VisualizerSettings>) {
        settings = values.map { it.sanitized() }.ifEmpty { listOf(VisualizerSettings(barsEnabled = false)) }
    }

    fun applyCustomShader(vertexSource: String, fragmentSource: String): String? = runCatching {
        require(vertexSource.isNotBlank()) { "Vertex shader is empty." }
        require(fragmentSource.isNotBlank()) { "Fragment shader is empty." }
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val nextProgram = linkProgram(vertex, fragment)
        val nextPosition = GLES20.glGetAttribLocation(nextProgram, "aPosition")
        val nextColor = GLES20.glGetAttribLocation(nextProgram, "aColor")
        val nextParticleSize = GLES20.glGetAttribLocation(nextProgram, "aParticleSize")
        val nextParticleRotation = GLES20.glGetAttribLocation(nextProgram, "aParticleRotation")
        val nextUseParticleSize = GLES20.glGetUniformLocation(nextProgram, "uUseParticleSize")
        val nextPointAspect = GLES20.glGetUniformLocation(nextProgram, "uPointAspect")
        require(nextPosition >= 0 && nextColor >= 0) { "Shader must expose aPosition and aColor attributes." }
        val oldProgram = program
        program = nextProgram
        positionHandle = nextPosition
        colorHandle = nextColor
        pointSizeHandle = GLES20.glGetUniformLocation(nextProgram, "uPointSize")
        roundPointHandle = GLES20.glGetUniformLocation(nextProgram, "uRoundPoint")
        offsetHandle = GLES20.glGetUniformLocation(nextProgram, "uOffset")
        tintHandle = GLES20.glGetUniformLocation(nextProgram, "uTint")
        particleSizeHandle = nextParticleSize
        particleRotationHandle = nextParticleRotation
        useParticleSizeHandle = nextUseParticleSize
        pointAspectHandle = nextPointAspect
        if (oldProgram != 0) GLES20.glDeleteProgram(oldProgram)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
    }.exceptionOrNull()?.message
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.027f, 0.035f, 1f)
        val vertexShader = """
            attribute vec2 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            uniform float uPointSize;
            uniform vec2 uOffset;
            uniform float uUseParticleSize;
            attribute float aParticleSize;
            attribute float aParticleRotation;
            varying float vParticleRotation;
            void main() {
                gl_Position = vec4(aPosition + uOffset, 0.0, 1.0);
                gl_PointSize = uUseParticleSize > 0.5 ? aParticleSize : uPointSize;
                vParticleRotation = aParticleRotation;
                vColor = aColor;
            }
        """.trimIndent()
        val fragmentShader = """
            precision mediump float;
            varying vec4 vColor;
            uniform float uRoundPoint;
            uniform vec4 uTint;
            uniform float uPointAspect;
            varying float vParticleRotation;
            void main() {
                vec2 pointCoord = gl_PointCoord - vec2(0.5);
                float cs = cos(vParticleRotation);
                float sn = sin(vParticleRotation);
                pointCoord = mat2(cs, -sn, sn, cs) * pointCoord;
                if (uPointAspect > 1.0) pointCoord.y *= uPointAspect;
                else pointCoord.x /= max(uPointAspect, 0.01);
                if (uRoundPoint > 0.5 && dot(pointCoord, pointCoord) > 0.25) discard;
                gl_FragColor = vec4(vColor.rgb * uTint.rgb, vColor.a * uTint.a);
            }
        """.trimIndent()
        program = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vertexShader), compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader))
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
        roundPointHandle = GLES20.glGetUniformLocation(program, "uRoundPoint")
        offsetHandle = GLES20.glGetUniformLocation(program, "uOffset")
        tintHandle = GLES20.glGetUniformLocation(program, "uTint")
        particleSizeHandle = GLES20.glGetAttribLocation(program, "aParticleSize")
        particleRotationHandle = GLES20.glGetAttribLocation(program, "aParticleRotation")
        useParticleSizeHandle = GLES20.glGetUniformLocation(program, "uUseParticleSize")
        pointAspectHandle = GLES20.glGetUniformLocation(program, "uPointAspect")
        vertexBuffer = ByteBuffer.allocateDirect(vertexCapacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        particleBuffer = ByteBuffer.allocateDirect(5000 * 4 * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val configs = settings
        val beatConfig = configs.firstOrNull() ?: VisualizerSettings(barsEnabled = false)
        val background = beatConfig.backgroundColor
        val backgroundAlpha = ((background shr 24) and 0xFF).toFloat() / 255f * beatConfig.compositionAlpha
        GLES20.glClearColor(
            ((background shr 16) and 0xFF).toFloat() / 255f,
            ((background shr 8) and 0xFF).toFloat() / 255f,
            (background and 0xFF).toFloat() / 255f,
            backgroundAlpha
        )
        if (beatConfig.clearCanvas) GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        val data = spectrum
        val nowNanos = System.nanoTime()
        val elapsedSeconds = ((nowNanos - rendererStartNanos) / 1_000_000_000.0).toFloat()
        val bassBins = min(12, data.size)
        val bassEnergy = if (bassBins > 0) data.take(bassBins).average().toFloat() else 0f
        bassNoiseFloor = bassNoiseFloor * 0.985f + min(bassEnergy, bassNoiseFloor * 1.8f) * 0.015f
        val beatThreshold = (0.12f - beatConfig.beatSensitivity * 0.075f).coerceIn(0.045f, 0.12f)
        val adaptiveFloor = max(beatThreshold, bassNoiseFloor * (2.0f - beatConfig.beatSensitivity * 0.65f))
        val onsetRatio = 1.48f - beatConfig.beatSensitivity * 0.40f
        if (bassEnergy > adaptiveFloor && bassEnergy > previousBassEnergy * onsetRatio && nowNanos - lastBeatNanos > 200_000_000L) {
            beatPulse = 1f
            lastBeatNanos = nowNanos
        } else {
            beatPulse *= 0.88f
        }
        previousBassEnergy = previousBassEnergy * 0.72f + bassEnergy * 0.28f
        configs.forEachIndexed { layerIndex, config ->
        when (config.blendMode) {
            "Add" -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            "Screen" -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR)
            "Multiply" -> GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ZERO)
            else -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
        val positionSignal = measureSignal(config.positionMeasure, elapsedSeconds, data.average().toFloat(), beatPulse, config.trackPositionFraction)
        val scaleSignal = measureSignal(config.scaleMeasure, elapsedSeconds, data.average().toFloat(), beatPulse, config.trackPositionFraction)
        val rotationSignal = measureSignal(config.rotationMeasure, elapsedSeconds, data.average().toFloat(), beatPulse, config.trackPositionFraction)
        if (config.barsEnabled) {
        val count = config.barCount
        val slot = 2f / count
        val barWidth = max(0.001f, slot * config.barWidth)
        val horizontalInset = (slot - barWidth) * 0.5f
        val amount = config.measureAmount
        val effectivePositionX = (config.positionX + (positionSignal - 0.5f) * amount * 0.6f).coerceIn(-1f, 2f)
        val effectivePositionY = (config.positionY + (positionSignal - 0.5f) * amount * 0.6f).coerceIn(-1f, 2f)
        val scaleMeasureFactor = (1f + (scaleSignal - 0.5f) * amount).coerceIn(0.1f, 3f)
        val effectiveScaleX = (config.scale * config.scaleX * scaleMeasureFactor).coerceIn(0.1f, 6f)
        val effectiveScaleY = (config.scale * config.scaleY * scaleMeasureFactor).coerceIn(0.1f, 6f)
        val effectiveRotation = config.rotation + (rotationSignal - 0.5f) * amount * 90f
        val segmentCount = config.segmentCount.coerceIn(1, 32)
        val segmentGap = config.segmentGap.coerceIn(0f, 0.45f)
        val previousHeights = previousBarHeights[layerIndex]?.takeIf { it.size == count }
            ?: FloatArray(count).also { previousBarHeights[layerIndex] = it }
        val activeStylesForCapacity = config.segmentStyles.take(3).ifEmpty { listOf(BarSegmentStyle()) }
        val hasVerticalMirror = config.mirror || activeStylesForCapacity.any { it.mirror }
        val mirrorCopiesForBars = 1 + (if (hasVerticalMirror) 1 else 0) + (if (config.mirrorX) 1 else 0) + (if (hasVerticalMirror && config.mirrorX) 1 else 0)
        val vertices = FloatArray(count * (segmentCount * 20 * mirrorCopiesForBars + 4) * 18)
        var offset = 0
        for (i in 0 until count) {
            val progress = i.toFloat() / (count - 1).coerceAtLeast(1)
            val sourceProgress = if (config.flipInput) 1f - progress else progress
            val sourceIndex = (sourceProgress * (data.size - 1)).toInt().coerceIn(0, data.lastIndex)
            val raw = (data[sourceIndex] * config.sensitivity).coerceIn(0f, 1f)
            val height = ((config.barMinHeight + raw * (0.88f * config.barHeight)).coerceAtLeast(0.005f)).coerceIn(0.005f, 1.5f)
            val x0 = -1f + i * slot + horizontalInset
            val x1 = min(1f, x0 + barWidth)
            val y0 = -0.94f
            val y1 = y0 + height
            val colorProgress = (progress + config.colorBlendOffset).let { it - kotlin.math.floor(it) }
            fun channel(shift: Int): Float {
                val from = ((config.barColorFrom shr shift) and 0xFF).toFloat()
                val to = ((config.barColorTo shr shift) and 0xFF).toFloat()
                return (from + (to - from) * colorProgress) / 255f
            }
            val red = channel(16)
            val green = channel(8)
            val blue = channel(0)
            val alpha = config.opacity * channel(24) * config.compositionAlpha
            fun appendVertex(x: Float, y: Float, r: Float, g: Float, b: Float, a: Float) {
                val angle = Math.toRadians(effectiveRotation.toDouble())
                val cosine = cos(angle).toFloat()
                val sine = sin(angle).toFloat()
                val localX = x * effectiveScaleX
                val localY = y * effectiveScaleY
                val centerX = (effectivePositionX - 0.5f) * 2f
                val centerY = (0.5f - effectivePositionY) * 2f
                vertices[offset++] = localX * cosine - localY * sine + centerX
                vertices[offset++] = localX * sine + localY * cosine + centerY
                vertices[offset++] = r
                vertices[offset++] = g
                vertices[offset++] = b
                vertices[offset++] = a
            }
            fun appendTriangle(
                ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float,
                a: Float, r: Float, g: Float, b: Float
            ) {
                appendVertex(ax, ay, r, g, b, a)
                appendVertex(bx, by, r, g, b, a)
                appendVertex(cx, cy, r, g, b, a)
            }
            fun appendQuad(
                left: Float, bottom: Float, right: Float, top: Float, a: Float,
                r: Float = red, g: Float = green, b: Float = blue
            ) {
                appendTriangle(left,bottom,right,bottom,right,top,a,r,g,b)
                appendTriangle(left,bottom,right,top,left,top,a,r,g,b)
            }
            fun appendPolygon(
                left: Float, bottom: Float, right: Float, top: Float,
                sides: Int, a: Float, r: Float, g: Float, b: Float, star: Boolean = false, flipVertical: Boolean = false
            ) {
                val cx = (left + right) * 0.5f
                val cy = (bottom + top) * 0.5f
                val rx = (right - left) * 0.5f
                val ry = (top - bottom) * 0.5f
                val points = ArrayList<Pair<Float, Float>>(sides)
                for (point in 0 until sides) {
                    val angle = -Math.PI / 2.0 + point * Math.PI * 2.0 / sides
                    val radius = if (star && point % 2 == 1) 0.42f else 1f
                    points.add(Pair(
                        cx + cos(angle).toFloat() * rx * radius,
                        cy + (if (flipVertical) -1f else 1f) * sin(angle).toFloat() * ry * radius
                    ))
                }
                for (point in points.indices) {
                    val next = points[(point + 1) % points.size]
                    val current = points[point]
                    appendTriangle(cx, cy, current.first, current.second, next.first, next.second, a, r, g, b)
                }
            }
            fun appendShape(
                shape: String, left: Float, bottom: Float, right: Float, top: Float,
                a: Float, r: Float, g: Float, b: Float, flipVertical: Boolean = false
            ) {
                when (shape) {
                    "None" -> Unit
                    "Line", "HorizontalLine" ->
                        appendQuad(left, (bottom + top) * .5f - .0035f, right, (bottom + top) * .5f + .0035f, a, r, g, b)
                    "VerticalLine" ->
                        appendQuad((left + right) * .5f - .0035f, bottom, (left + right) * .5f + .0035f, top, a, r, g, b)
                    "Circle", "Oval" ->
                        appendPolygon(left, bottom, right, top, 20, a, r, g, b, flipVertical = flipVertical)
                    "Triangle" ->
                        appendPolygon(left, bottom, right, top, 3, a, r, g, b, flipVertical = flipVertical)
                    "Star", "Heart" ->
                        appendPolygon(left, bottom, right, top, 10, a, r, g, b, star = true, flipVertical = flipVertical)
                    "Pentagon" ->
                        appendPolygon(left, bottom, right, top, 5, a, r, g, b, flipVertical = flipVertical)
                    "Hexagon", "SidedPolygon" ->
                        appendPolygon(left, bottom, right, top, 6, a, r, g, b, flipVertical = flipVertical)
                    "Square" -> {
                        val side = min(right - left, top - bottom)
                        val cx = (left + right) * .5f
                        appendQuad(cx - side * .5f, bottom, cx + side * .5f, bottom + side, a, r, g, b)
                    }
                    else -> appendQuad(left, bottom, right, top, a, r, g, b)
                }
            }
            val previousHeight = previousHeights[i]
            val previousTop = y0 + previousHeight
            fun blurChannel(color: Long, shift: Int) = ((color shr shift) and 0xFF).toFloat() / 255f
            fun drawGlow() {
                if (config.glow <= 0f) return
                val glowSpread = 0.002f + config.glowRadius / 100f * 0.04f
                appendQuad(
                    x0 - glowSpread, y0 - glowSpread * 1.25f,
                    x1 + glowSpread, y1 + glowSpread * 1.25f,
                    alpha * config.glow * 0.22f * blurChannel(config.blurColor, 24),
                    blurChannel(config.blurColor, 16),
                    blurChannel(config.blurColor, 8),
                    blurChannel(config.blurColor, 0)
                )
            }
            fun drawMotionTrail() {
                if (config.motionBlurAmount <= 0f || previousHeight <= 0f) return
                val sourceFactor = when (config.motionBlurSource) {
                    "AudioVolume" -> raw.coerceIn(0f, 1f)
                    else -> (kotlin.math.abs(height - previousHeight) / height.coerceAtLeast(0.05f)).coerceIn(0.15f, 1f)
                }
                val trailOffset = if (config.motionBlurRelativeMotion) {
                    0.006f + config.motionBlurAmount * 0.035f * sourceFactor
                } else {
                    config.motionBlurAmount * 0.035f
                }
                val trailTop = if (config.motionBlurRelativeMotion) previousTop else y0 + height * 0.68f
                val motionAlpha = alpha * config.motionBlurAmount * 0.32f * sourceFactor * blurChannel(config.motionBlurColor, 24)
                appendQuad(
                    x0 - trailOffset, y0, x1 - trailOffset, trailTop, motionAlpha,
                    blurChannel(config.motionBlurColor, 16),
                    blurChannel(config.motionBlurColor, 8),
                    blurChannel(config.motionBlurColor, 0)
                )
            }
            if (config.blurShowSharpUnder) drawGlow()
            if (config.motionBlurShowSharpUnder) drawMotionTrail()

            val drawSharp = config.blurShowSharp && config.motionBlurShowSharp
            val styles = config.segmentStyles.take(3).ifEmpty { listOf(BarSegmentStyle()) }
            if (drawSharp) {
                val standardSegmentHeight = (y1 - y0) / styles.size.coerceAtLeast(1)
                var segmentCursor = y0
                styles.forEach { style ->
                    val segmentHeight = if (style.fixedHeight > 0f) {
                        (style.fixedHeight * 0.25f).coerceAtMost(1.5f)
                    } else standardSegmentHeight
                    val actualHeight = (segmentHeight * style.heightMultiplier).coerceIn(0f, 1.5f)
                    val gapSize = actualHeight * segmentGap * 0.5f
                    val segmentBottom = segmentCursor + gapSize
                    val segmentTop = segmentCursor + actualHeight - gapSize
                    if (segmentTop > segmentBottom) {
                        fun styleChannel(shift: Int): Float {
                            val from = ((style.colorFrom shr shift) and 0xFF).toFloat()
                            val to = ((style.colorTo shr shift) and 0xFF).toFloat()
                            return (from + (to - from) * colorProgress) / 255f
                        }
                        val sr = styleChannel(16)
                        val sg = styleChannel(8)
                        val sb = styleChannel(0)
                        val sa = alpha * styleChannel(24)
                        fun mirrorChannel(shift: Int) = ((config.mirrorColor shr shift) and 0xFF).toFloat() / 255f
                        fun drawAt(
                            left: Float, bottom: Float, right: Float, top: Float,
                            flip: Boolean = false, mirrorCopy: Boolean = false,
                            offsetX: Float = 0f, offsetY: Float = 0f
                        ) {
                            val tintR = if (mirrorCopy) mirrorChannel(16) else 1f
                            val tintG = if (mirrorCopy) mirrorChannel(8) else 1f
                            val tintB = if (mirrorCopy) mirrorChannel(0) else 1f
                            val tintA = if (mirrorCopy) mirrorChannel(24) else 1f
                            appendShape(
                                style.shape, left + offsetX, bottom + offsetY, right + offsetX, top + offsetY,
                                sa * tintA, sr * tintR, sg * tintG, sb * tintB,
                                flipVertical = flip || (mirrorCopy && config.mirrorFlip)
                            )
                        }
                        val alternatingFlip = style.flipOther && i % 2 == 1
                        drawAt(x0, segmentBottom, x1, segmentTop, alternatingFlip)
                        if (style.mirror || config.mirror) {
                            drawAt(x0, -segmentTop, x1, -segmentBottom, alternatingFlip, true, offsetY = config.mirrorOffset)
                        }
                        if (config.mirrorX) {
                            drawAt(-x1, segmentBottom, -x0, segmentTop, alternatingFlip, true, offsetX = config.mirrorOffset)
                        }
                        if (config.mirrorX && (style.mirror || config.mirror)) {
                            drawAt(-x1, -segmentTop, -x0, -segmentBottom, alternatingFlip, true, config.mirrorOffset, config.mirrorOffset)
                        }
                    }
                    segmentCursor += actualHeight
                }
            }
            if (!config.blurShowSharpUnder) drawGlow()
            if (!config.motionBlurShowSharpUnder) drawMotionTrail()
            previousHeights[i] = height
        }
        val buffer = vertexBuffer ?: return
        buffer.clear()
        buffer.put(vertices, 0, offset)
        buffer.position(0)
        GLES20.glUseProgram(program)
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 24, buffer)
        buffer.position(2)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glUniform1f(roundPointHandle, 0f)
        GLES20.glUniform1f(pointSizeHandle, 1f)
        val vertexCount = offset / 6
        val rgbGlitchActive = !config.rgbSplitOnBeat ||
            (beatPulse > 0.02f && nowNanos - lastBeatNanos <= config.rgbGlitchDurationMs.toLong() * 1_000_000L)
        if (config.rgbSplitAmount > 0f && rgbGlitchActive) {
            val shift = 0.002f + config.rgbSplitAmount * 0.028f
            val angle = Math.toRadians(config.rgbSplitAngle.toDouble())
            val directionX = cos(angle).toFloat()
            val directionY = sin(angle).toFloat()
            val redShift = shift * (1f + config.rgbRedOffset / 100f)
            val greenShift = shift * (config.rgbGreenOffset / 100f)
            val blueShift = shift * (1f + config.rgbBlueOffset / 100f)
            GLES20.glUniform2f(offsetHandle, -directionX * redShift, -directionY * redShift)
            GLES20.glUniform4f(tintHandle, 1f, 0f, 0f, 0.72f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
            GLES20.glUniform2f(offsetHandle, directionX * greenShift, directionY * greenShift)
            GLES20.glUniform4f(tintHandle, 0f, 1f, 0f, 0.72f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
            GLES20.glUniform2f(offsetHandle, directionX * blueShift, directionY * blueShift)
            GLES20.glUniform4f(tintHandle, 0f, 0f, 1f, 0.72f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }
        GLES20.glUniform2f(offsetHandle, 0f, 0f)
        GLES20.glUniform4f(tintHandle, 1f, 1f, 1f, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }

        if (config.particlesEnabled && config.particleCount > 0) {
            val mirrorCopies = 1 + (if (config.particleMirrorX) 1 else 0) +
                (if (config.particleMirrorY) 1 else 0) +
                (if (config.particleMirrorX && config.particleMirrorY) 1 else 0)
            val tailCopies = if (config.particleTrailLength > 0f) 3 else 1
            val requiredFloats = config.particleCount * mirrorCopies * tailCopies * 8
            val points = particleBuffer?.takeIf { it.capacity() >= requiredFloats }
                ?: ByteBuffer.allocateDirect(requiredFloats.coerceAtLeast(6) * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer().also { particleBuffer = it }
            points.clear()
            var renderedParticleCount = 0
            val now = System.nanoTime() / 1_000_000_000.0
            val energy = data.average().toFloat().coerceIn(0f, 1f)
            val particlePositionSignal = measureSignal(config.particlePositionMeasure, elapsedSeconds, energy, beatPulse, config.trackPositionFraction)
            val particleScaleSignal = measureSignal(config.particleScaleMeasure, elapsedSeconds, energy, beatPulse, config.trackPositionFraction)
            val particleRotationSignal = measureSignal(config.particleRotationMeasure, elapsedSeconds, energy, beatPulse, config.trackPositionFraction)
            val particleAmount = config.particleMeasureAmount
            val particleXOffset = (config.particlePositionX - 0.5f) * 2f + (particlePositionSignal - 0.5f) * particleAmount * 0.6f
            val particleYOffset = (0.5f - config.particlePositionY) * 2f - (particlePositionSignal - 0.5f) * particleAmount * 0.6f
            val particleScale = (config.particleScale * (1f + (particleScaleSignal - 0.5f) * particleAmount)).coerceIn(0.1f, 4f)
            val particleRotation = Math.toRadians((config.particleRotation + (particleRotationSignal - 0.5f) * particleAmount * 90f).toDouble())
            val particleCos = cos(particleRotation).toFloat()
            val particleSin = sin(particleRotation).toFloat()

            fun colorChannel(color: Long, shift: Int) = ((color shr shift) and 0xFF).toFloat() / 255f
            fun appendParticle(
                px: Float, py: Float, color: Long, alphaMultiplier: Float,
                particlePointSize: Float, particleAngle: Float, mirrorCopy: Boolean = false,
                offsetX: Float = 0f, offsetY: Float = 0f
            ) {
                val tintR = if (mirrorCopy) colorChannel(config.mirrorColor, 16) else 1f
                val tintG = if (mirrorCopy) colorChannel(config.mirrorColor, 8) else 1f
                val tintB = if (mirrorCopy) colorChannel(config.mirrorColor, 0) else 1f
                val tintA = if (mirrorCopy) colorChannel(config.mirrorColor, 24) else 1f
                points.put((px + offsetX) * config.scale * config.scaleX); points.put((py + offsetY) * config.scale * config.scaleY)
                points.put(colorChannel(color, 16) * tintR)
                points.put(colorChannel(color, 8) * tintG)
                points.put(colorChannel(color, 0) * tintB)
                points.put(colorChannel(color, 24) * alphaMultiplier * tintA)
                points.put(particlePointSize)
                points.put(particleAngle + if (mirrorCopy && config.mirrorFlip) Math.PI.toFloat() else 0f)
                renderedParticleCount++
            }

            for (i in 0 until config.particleCount) {
                val seed = i.toFloat() + 1f
                val randomA = (abs(sin(seed * 12.9898f) * 43758.5453f) % 1f)
                val randomB = (abs(sin(seed * 78.233f) * 19341.381f) % 1f)
                val seedAngle = seed * 2.3999632f
                val life = config.particleLifetime.coerceAtLeast(0.1f)
                val emissionOffset = i.toFloat() * config.particleSpawnTime
                val phase = ((((now - emissionOffset) / life) + randomA) % 1.0).toFloat().coerceIn(0f, 1f)
                val spawnRadius = sqrt(randomB)
                val cameraDepth = config.particlePerspectiveDepth * (0.4f + randomB * 0.6f)
                val perspectiveFactor = (config.particlePerspectiveFov / (config.particlePerspectiveFov + cameraDepth * 0.1f)).coerceIn(0.1f, 1f)
                val nearFadeFactor = (cameraDepth / (cameraDepth + config.particleNearCameraFade.coerceAtLeast(0f))).coerceIn(0.05f, 1f)
                val particleSpeedSignal = measureSignal(config.particleSpeedMeasure, elapsedSeconds, energy, beatPulse, config.trackPositionFraction)
                val particleSpeedFactor = (1f + (particleSpeedSignal - 0.5f) * 0.8f).coerceIn(0.1f, 2f)
                val spawn = when (config.particleSpawnArea) {
                    "Circle" -> Pair(cos(seedAngle) * spawnRadius * config.particleSpread * 0.5f, sin(seedAngle) * spawnRadius * config.particleSpreadY * 0.5f)
                    "Line" -> Pair((randomA - 0.5f) * config.particleSpread, 0f)
                    "Point" -> Pair(0f, 0f)
                    else -> Pair((randomA - 0.5f) * config.particleSpread, (randomB - 0.5f) * config.particleSpreadY)
                }
                var direction = Math.toRadians(
                    ((if (config.particleRandomVector) randomB * 360f else config.particleVectorAngle) +
                        config.particleVectorAngleZ * phase).toDouble()
                )
                if (config.particleVectorRelative) {
                    direction += kotlin.math.atan2(-spawn.second.toDouble(), -spawn.first.toDouble())
                }
                val directionX = cos(direction).toFloat()
                val directionY = sin(direction).toFloat()
                val randomSpeed = (1f + (randomA * 2f - 1f) * config.particleSpeedRandom / 10f).coerceAtLeast(0f)
                val travel = (phase - 0.5f) * config.particleSpeed * randomSpeed * particleSpeedFactor * perspectiveFactor * 0.42f
                var baseX = spawn.first + directionX * travel
                var baseY = spawn.second + directionY * travel

                when (config.particleForceField) {
                    "Gravity" -> {
                        baseX += config.particleGravityX * phase * phase * 0.025f
                        baseY += config.particleGravityY * phase * phase * 0.025f
                    }
                    "Wind" -> baseX += config.particleSpeed * phase * 0.09f
                    "Vortex" -> {
                        val vortexAngle = phase * 6.2831853f
                        val vx = baseX * cos(vortexAngle) - baseY * sin(vortexAngle)
                        val vy = baseX * sin(vortexAngle) + baseY * cos(vortexAngle)
                        baseX = vx
                        baseY = vy
                    }
                    "Attractor", "Repeller" -> {
                        val directionToCenter = if (config.particleForceField == "Attractor") -1f else 1f
                        baseX += baseX * directionToCenter * phase * 0.55f
                        baseY += baseY * directionToCenter * phase * 0.55f
                    }
                }

                val waveFrequency = (config.particleWaveFrequency + randomA * config.particleWaveFrequencyRandom).coerceIn(0f, 50f)
                val waveMagnitude = (config.particleWaveMagnitude + (randomB * 2f - 1f) * config.particleWaveMagnitudeRandom).coerceIn(0f, 10f)
                val wave = sin(phase * waveFrequency * 6.2831853f + seedAngle) * waveMagnitude * 0.025f
                baseX += -directionY * wave
                baseY += directionX * wave

                val localX = baseX * particleScale
                val localY = baseY * particleScale
                val x = (localX * particleCos - localY * particleSin + particleXOffset).coerceIn(-1.4f, 1.4f)
                val y = (localX * particleSin + localY * particleCos + particleYOffset).coerceIn(-1.4f, 1.4f)
                val fadeWindow = (config.particleFadeInOutTime / life).coerceIn(0f, 0.5f)
                val fadeIn = if (fadeWindow <= 0f) 1f else (phase / fadeWindow).coerceIn(0f, 1f)
                val fadeOut = if (fadeWindow <= 0f) 1f else ((1f - phase) / fadeWindow).coerceIn(0f, 1f)
                val energyAlpha = 0.30f + energy * 0.70f
                val alpha = config.particleOpacity * config.compositionAlpha * energyAlpha * fadeIn * fadeOut * nearFadeFactor
                val t = phase.coerceIn(0f, 1f)
                fun lerpChannel(shift: Int) =
                    colorChannel(config.particleColor, shift) +
                        (colorChannel(config.particleColorTo, shift) - colorChannel(config.particleColor, shift)) * t
                val color = ((lerpChannel(24) * 255f).toLong() shl 24) or
                    ((lerpChannel(16) * 255f).toLong() shl 16) or
                    ((lerpChannel(8) * 255f).toLong() shl 8) or
                    (lerpChannel(0) * 255f).toLong()
                val startSize = if (config.particleFadeInSize > 0f) config.particleFadeInSize else config.particleSize
                val randomEndSize = (config.particleEndSize +
                    (randomB * 2f - 1f) * config.particleEndSizeRandom).coerceIn(0f, 64f)
                val zSizeFactor = 1f + config.particleEndSizeZFactor * (1f - perspectiveFactor) * 0.05f
                val particlePointSize = (startSize + (randomEndSize - startSize) * phase) *
                    particleScale * (0.75f + fadeIn * 0.25f) * perspectiveFactor * zSizeFactor

                val particleAngle = if (config.particleVelocityAngle) direction.toFloat() else particleRotation.toFloat()
                appendParticle(x, y, color, alpha, particlePointSize, particleAngle)
                if (config.particleTrailLength > 0f) {
                    val tailDistance = config.particleTrailLength * 0.003f
                    appendParticle(x - directionX * tailDistance, y - directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle)
                    appendParticle(x - directionX * tailDistance * 2f, y - directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle)
                }
                if (config.particleMirrorX) {
                    appendParticle(-x, y, color, alpha, particlePointSize, particleAngle, true, config.mirrorOffset)
                    if (config.particleTrailLength > 0f) {
                        val tailDistance = config.particleTrailLength * 0.003f
                        appendParticle(-x + directionX * tailDistance, y - directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle, true, config.mirrorOffset)
                        appendParticle(-x + directionX * tailDistance * 2f, y - directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle, true, config.mirrorOffset)
                    }
                }
                if (config.particleMirrorY) {
                    appendParticle(x, -y, color, alpha, particlePointSize, particleAngle, true, 0f, config.mirrorOffset)
                    if (config.particleTrailLength > 0f) {
                        val tailDistance = config.particleTrailLength * 0.003f
                        appendParticle(x - directionX * tailDistance, -y + directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle, true, 0f, config.mirrorOffset)
                        appendParticle(x - directionX * tailDistance * 2f, -y + directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle, true, 0f, config.mirrorOffset)
                    }
                }
                if (config.particleMirrorX && config.particleMirrorY) {
                    appendParticle(-x, -y, color, alpha, particlePointSize, particleAngle, true, config.mirrorOffset, config.mirrorOffset)
                    if (config.particleTrailLength > 0f) {
                        val tailDistance = config.particleTrailLength * 0.003f
                        appendParticle(-x + directionX * tailDistance, -y + directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle, true, config.mirrorOffset, config.mirrorOffset)
                        appendParticle(-x + directionX * tailDistance * 2f, -y + directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle, true, config.mirrorOffset, config.mirrorOffset)
                    }
                }
            }
            points.position(0)
            GLES20.glUniform1f(roundPointHandle, 1f)
            GLES20.glUniform1f(pointSizeHandle, config.particleSize * particleScale)
            GLES20.glUniform1f(useParticleSizeHandle, if (particleSizeHandle >= 0) 1f else 0f)
            GLES20.glUniform1f(pointAspectHandle, config.particleSizeAspectRatio)
            GLES20.glUniform2f(offsetHandle, 0f, 0f)
            GLES20.glUniform4f(tintHandle, 1f, 1f, 1f, 1f)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 32, points)
            points.position(2)
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 32, points)
            if (particleSizeHandle >= 0) {
                points.position(6)
                GLES20.glEnableVertexAttribArray(particleSizeHandle)
                GLES20.glVertexAttribPointer(particleSizeHandle, 1, GLES20.GL_FLOAT, false, 32, points)
            }
            if (particleRotationHandle >= 0) {
                points.position(7)
                GLES20.glEnableVertexAttribArray(particleRotationHandle)
                GLES20.glVertexAttribPointer(particleRotationHandle, 1, GLES20.GL_FLOAT, false, 32, points)
            }
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, renderedParticleCount)
            if (particleSizeHandle >= 0) GLES20.glDisableVertexAttribArray(particleSizeHandle)
            if (particleRotationHandle >= 0) GLES20.glDisableVertexAttribArray(particleRotationHandle)
        }
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun measureSignal(what: MeasureWhat, elapsed: Float, energy: Float, beat: Float, trackPosition: Float): Float {
        val phase = elapsed * 0.7f
        return when (what) {
            MeasureWhat.NOTHING -> 0.5f
            MeasureWhat.BEAT -> 0.5f + beat * 0.5f
            MeasureWhat.TOTAL_TIME -> (elapsed * 0.25f) % 1f
            MeasureWhat.TOTAL_TIME_BACKWARD -> 1f - ((elapsed * 0.25f) % 1f)
            MeasureWhat.TOTAL_TIME_WHEN_PLAYING -> if (energy > 0.015f) (elapsed * 0.25f) % 1f else 0.5f
            MeasureWhat.TOTAL_TIME_AND_BEAT -> (((elapsed * 0.25f) % 1f) * 0.65f + beat * 0.35f).coerceIn(0f, 1f)
            MeasureWhat.TRACK_POSITION -> trackPosition
            MeasureWhat.BEAT_RANDOM_SHAKE -> 0.5f + beat * sin(elapsed * 37f) * 0.5f
            MeasureWhat.BEAT_CAM_SHAKE_ROT_MORE -> 0.5f + beat * 0.5f
            MeasureWhat.BEAT_CAM_SHAKE_ROT_LESS -> 0.5f - beat * 0.5f
            MeasureWhat.CONSTANT_SHAKE_ROT_MORE -> 0.5f + sin(phase * 19f) * 0.5f
            MeasureWhat.CONSTANT_SHAKE_ROT_LESS -> 0.5f - sin(phase * 19f) * 0.5f
            MeasureWhat.BEAT_TRIGGER_ANIM, MeasureWhat.BEAT_CAM_SHAKE_ONLY,
            MeasureWhat.BEAT_PULSE_SCALE, MeasureWhat.BEAT_FLASH_COLOR,
            MeasureWhat.BEAT_GLOW_INTENSITY, MeasureWhat.BEAT_HORIZONTAL_STRETCH,
            MeasureWhat.BEAT_VERTICAL_BOUNCE -> 0.5f + beat * 0.5f
            MeasureWhat.CONSTANT_SLOW_ROTATE -> (elapsed * 0.08f) % 1f
            MeasureWhat.CONSTANT_ORBIT_MOVE -> 0.5f + sin(elapsed * 0.35f) * 0.5f
            MeasureWhat.CONSTANT_LINEAR_SCROLL -> (elapsed * 0.12f) % 1f
            MeasureWhat.TRACK_PROGRESS_PERCENTAGE -> trackPosition
            MeasureWhat.TRACK_REMAINING_TIME -> 1f - trackPosition
            MeasureWhat.TRACK_INTRO_OUTRO_TRIGGER -> if (trackPosition < 0.08f || trackPosition > 0.92f) 0.5f + beat * 0.5f else 0.5f
            MeasureWhat.VOLUME_LEVEL_RESPONSIVE, MeasureWhat.PITCH_FREQUENCY_RESPONSIVE -> energy
        }.coerceIn(0f, 1f)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun linkProgram(vertex: Int, fragment: Int): Int {
        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, vertex)
        GLES20.glAttachShader(linked, fragment)
        GLES20.glLinkProgram(linked)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(linked) }
        return linked
    }
}
