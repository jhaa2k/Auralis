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
    private val vertexCapacity = 128 * (32 * 4 + 2) * 36
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
        GLES20.glClearColor(((background shr 16) and 0xFF).toFloat() / 255f, ((background shr 8) and 0xFF).toFloat() / 255f, (background and 0xFF).toFloat() / 255f, ((background shr 24) and 0xFF).toFloat() / 255f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
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
        val effectiveScale = (config.scale * (1f + (scaleSignal - 0.5f) * amount)).coerceIn(0.1f, 3f)
        val effectiveRotation = config.rotation + (rotationSignal - 0.5f) * amount * 90f
        val segmentCount = config.segmentCount.coerceIn(1, 32)
        val segmentGap = config.segmentGap.coerceIn(0f, 0.45f)
        val previousHeights = previousBarHeights[layerIndex]?.takeIf { it.size == count }
            ?: FloatArray(count).also { previousBarHeights[layerIndex] = it }
        val vertices = FloatArray(count * (segmentCount * 4 + 2) * 36)
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
            fun channel(shift: Int): Float {
                val from = ((config.barColorFrom shr shift) and 0xFF).toFloat()
                val to = ((config.barColorTo shr shift) and 0xFF).toFloat()
                return (from + (to - from) * progress) / 255f
            }
            val red = channel(16)
            val green = channel(8)
            val blue = channel(0)
            val alpha = config.opacity * channel(24)
            fun appendQuad(left: Float, bottom: Float, right: Float, top: Float, a: Float) {
                val points = floatArrayOf(
                    left,bottom, right,bottom, right,top,
                    left,bottom, right,top, left,top
                )
                val angle = Math.toRadians(effectiveRotation.toDouble())
                val cosine = cos(angle).toFloat()
                val sine = sin(angle).toFloat()
                val centerX = (effectivePositionX - 0.5f) * 2f
                val centerY = (0.5f - effectivePositionY) * 2f
                for (p in 0 until 6) {
                    val localX = points[p * 2] * effectiveScale
                    val localY = points[p * 2 + 1] * effectiveScale
                    val rotatedX = localX * cosine - localY * sine + centerX
                    val rotatedY = localX * sine + localY * cosine + centerY
                    vertices[offset++] = rotatedX
                    vertices[offset++] = rotatedY
                    vertices[offset++] = red
                    vertices[offset++] = green
                    vertices[offset++] = blue
                    vertices[offset++] = a
                }
            }
            // Soft expanded underlay approximates a glow without an expensive blur pass.
            if (config.glow > 0f) appendQuad(x0 - 0.006f, y0 - 0.008f, x1 + 0.006f, y1 + 0.008f, alpha * config.glow * 0.22f)
            val previousTop = y0 + previousHeights[i]
            if (config.motionBlurAmount > 0f && previousHeights[i] > 0f) {
                val trailOffset = 0.006f + config.motionBlurAmount * 0.035f
                appendQuad(x0 - trailOffset, y0, x1 - trailOffset, previousTop, alpha * config.motionBlurAmount * 0.32f)
            }
            previousHeights[i] = height
            val segmentHeight = (y1 - y0) / segmentCount
            for (segment in 0 until segmentCount) {
                val segmentBottom = y0 + segment * segmentHeight + segmentHeight * segmentGap * 0.5f
                val segmentTop = y0 + (segment + 1) * segmentHeight - segmentHeight * segmentGap * 0.5f
                if (segmentTop > segmentBottom) appendQuad(x0, segmentBottom, x1, segmentTop, alpha)
            }
            if (config.mirror) {
                if (config.glow > 0f) appendQuad(x0 - 0.006f, -y1 - 0.008f, x1 + 0.006f, -y0 + 0.008f, alpha * config.glow * 0.22f)
                for (segment in 0 until segmentCount) {
                    val segmentBottom = y0 + segment * segmentHeight + segmentHeight * segmentGap * 0.5f
                    val segmentTop = y0 + (segment + 1) * segmentHeight - segmentHeight * segmentGap * 0.5f
                    if (segmentTop > segmentBottom) appendQuad(x0, -segmentTop, x1, -segmentBottom, alpha)
                }
            }
            if (config.mirrorX) {
                for (segment in 0 until segmentCount) {
                    val segmentBottom = y0 + segment * segmentHeight + segmentHeight * segmentGap * 0.5f
                    val segmentTop = y0 + (segment + 1) * segmentHeight - segmentHeight * segmentGap * 0.5f
                    if (segmentTop > segmentBottom) appendQuad(-x1, segmentBottom, -x0, segmentTop, alpha)
                }
            }
            if (config.mirror && config.mirrorX) {
                for (segment in 0 until segmentCount) {
                    val segmentBottom = y0 + segment * segmentHeight + segmentHeight * segmentGap * 0.5f
                    val segmentTop = y0 + (segment + 1) * segmentHeight - segmentHeight * segmentGap * 0.5f
                    if (segmentTop > segmentBottom) appendQuad(-x1, -segmentTop, -x0, -segmentBottom, alpha)
                }
            }
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
        if (config.rgbSplitAmount > 0f) {
            val shift = 0.002f + config.rgbSplitAmount * 0.028f
            GLES20.glUniform2f(offsetHandle, -shift, 0f)
            GLES20.glUniform4f(tintHandle, 1f, 0.18f, 0.18f, 0.62f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
            GLES20.glUniform2f(offsetHandle, shift, 0f)
            GLES20.glUniform4f(tintHandle, 0.18f, 0.85f, 1f, 0.62f)
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
            fun appendParticle(px: Float, py: Float, color: Long, alphaMultiplier: Float, particlePointSize: Float, particleAngle: Float) {
                points.put(px); points.put(py)
                points.put(colorChannel(color, 16))
                points.put(colorChannel(color, 8))
                points.put(colorChannel(color, 0))
                points.put(colorChannel(color, 24) * alphaMultiplier)
                points.put(particlePointSize)
                points.put(particleAngle)
                renderedParticleCount++
            }

            for (i in 0 until config.particleCount) {
                val seed = i.toFloat() + 1f
                val randomA = (abs(sin(seed * 12.9898f) * 43758.5453f) % 1f)
                val randomB = (abs(sin(seed * 78.233f) * 19341.381f) % 1f)
                val seedAngle = seed * 2.3999632f
                val life = config.particleLifetime.coerceAtLeast(0.1f)
                val phase = (((now / life) + randomA) % 1.0).toFloat().coerceIn(0f, 1f)
                val spawnRadius = sqrt(randomB)
                val spawn = when (config.particleSpawnArea) {
                    "Circle" -> Pair(cos(seedAngle) * spawnRadius, sin(seedAngle) * spawnRadius)
                    "Line" -> Pair((randomA - 0.5f) * 1.8f, 0f)
                    "Point" -> Pair(0f, 0f)
                    else -> Pair((randomA - 0.5f) * 1.8f, (randomB - 0.5f) * 1.8f)
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
                val travel = (phase - 0.5f) * config.particleSpeed * randomSpeed * 0.42f
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

                val wave = sin(phase * config.particleWaveFrequency * 6.2831853f + seedAngle) *
                    config.particleWaveMagnitude * 0.025f
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
                val alpha = config.particleOpacity * energyAlpha * fadeIn * fadeOut
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
                val particlePointSize = (startSize + (randomEndSize - startSize) * phase) *
                    particleScale * (0.75f + fadeIn * 0.25f)

                val particleAngle = if (config.particleVelocityAngle) direction.toFloat() else particleRotation.toFloat()
                appendParticle(x, y, color, alpha, particlePointSize, particleAngle)
                if (config.particleTrailLength > 0f) {
                    val tailDistance = config.particleTrailLength * 0.003f
                    appendParticle(x - directionX * tailDistance, y - directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle)
                    appendParticle(x - directionX * tailDistance * 2f, y - directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle)
                }
                if (config.particleMirrorX) {
                    appendParticle(-x, y, color, alpha, particlePointSize, particleAngle)
                    if (config.particleTrailLength > 0f) {
                        val tailDistance = config.particleTrailLength * 0.003f
                        appendParticle(-x + directionX * tailDistance, y - directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle)
                        appendParticle(-x + directionX * tailDistance * 2f, y - directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle)
                    }
                }
                if (config.particleMirrorY) {
                    appendParticle(x, -y, color, alpha, particlePointSize, particleAngle)
                    if (config.particleTrailLength > 0f) {
                        val tailDistance = config.particleTrailLength * 0.003f
                        appendParticle(x - directionX * tailDistance, -y + directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle)
                        appendParticle(x - directionX * tailDistance * 2f, -y + directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle)
                    }
                }
                if (config.particleMirrorX && config.particleMirrorY) {
                    appendParticle(-x, -y, color, alpha, particlePointSize, particleAngle)
                    if (config.particleTrailLength > 0f) {
                        val tailDistance = config.particleTrailLength * 0.003f
                        appendParticle(-x + directionX * tailDistance, -y + directionY * tailDistance, color, alpha * 0.42f, particlePointSize * 0.78f, particleAngle)
                        appendParticle(-x + directionX * tailDistance * 2f, -y + directionY * tailDistance * 2f, color, alpha * 0.18f, particlePointSize * 0.58f, particleAngle)
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
