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
}

private class SpectrumGlRenderer : GLSurfaceView.Renderer {
    @Volatile private var spectrum = FloatArray(128)
    @Volatile private var settings = VisualizerSettings()
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var pointSizeHandle = 0
    private var roundPointHandle = 0
    private var vertexBuffer: FloatBuffer? = null
    private var particleBuffer: FloatBuffer? = null
    private val vertexCapacity = 128 * 24 * 6
    private val rendererStartNanos = System.nanoTime()
    private var previousBassEnergy = 0f
    private var bassNoiseFloor = 0.025f
    private var beatPulse = 0f
    private var lastBeatNanos = 0L

    fun submit(values: FloatArray) {
        spectrum = values.copyOf()
    }

    fun update(value: VisualizerSettings) {
        settings = value.sanitized()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.027f, 0.035f, 1f)
        val vertexShader = """
            attribute vec2 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            uniform float uPointSize;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                gl_PointSize = uPointSize;
                vColor = aColor;
            }
        """.trimIndent()
        val fragmentShader = """
            precision mediump float;
            varying vec4 vColor;
            uniform float uRoundPoint;
            void main() {
                if (uRoundPoint > 0.5 && distance(gl_PointCoord, vec2(0.5, 0.5)) > 0.5) discard;
                gl_FragColor = vColor;
            }
        """.trimIndent()
        program = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vertexShader), compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader))
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
        roundPointHandle = GLES20.glGetUniformLocation(program, "uRoundPoint")
        vertexBuffer = ByteBuffer.allocateDirect(vertexCapacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        particleBuffer = ByteBuffer.allocateDirect(400 * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        val config = settings.sanitized()
        val count = config.barCount
        val gap = 0.28f / count
        val slot = 2f / count
        val barWidth = max(0.001f, slot - gap)
        val data = spectrum
        val nowNanos = System.nanoTime()
        val elapsedSeconds = ((nowNanos - rendererStartNanos) / 1_000_000_000.0).toFloat()
        val bassBins = min(12, data.size)
        val bassEnergy = if (bassBins > 0) data.take(bassBins).average().toFloat() else 0f
        bassNoiseFloor = bassNoiseFloor * 0.985f + min(bassEnergy, bassNoiseFloor * 1.8f) * 0.015f
        val beatThreshold = (0.12f - config.beatSensitivity * 0.075f).coerceIn(0.045f, 0.12f)
        val adaptiveFloor = max(beatThreshold, bassNoiseFloor * (2.0f - config.beatSensitivity * 0.65f))
        val onsetRatio = 1.48f - config.beatSensitivity * 0.40f
        if (bassEnergy > adaptiveFloor && bassEnergy > previousBassEnergy * onsetRatio && nowNanos - lastBeatNanos > 200_000_000L) {
            beatPulse = 1f
            lastBeatNanos = nowNanos
        } else {
            beatPulse *= 0.88f
        }
        previousBassEnergy = previousBassEnergy * 0.72f + bassEnergy * 0.28f
        val positionSignal = measureSignal(config.positionMeasure, elapsedSeconds, data.average().toFloat(), beatPulse, config.trackPositionFraction)
        val scaleSignal = measureSignal(config.scaleMeasure, elapsedSeconds, data.average().toFloat(), beatPulse, config.trackPositionFraction)
        val rotationSignal = measureSignal(config.rotationMeasure, elapsedSeconds, data.average().toFloat(), beatPulse, config.trackPositionFraction)
        val amount = config.measureAmount
        val effectivePositionX = (config.positionX + (positionSignal - 0.5f) * amount * 0.6f).coerceIn(-1f, 2f)
        val effectivePositionY = (config.positionY + (positionSignal - 0.5f) * amount * 0.6f).coerceIn(-1f, 2f)
        val effectiveScale = (config.scale * (1f + (scaleSignal - 0.5f) * amount)).coerceIn(0.1f, 3f)
        val effectiveRotation = config.rotation + (rotationSignal - 0.5f) * amount * 90f
        val vertices = FloatArray(count * 24 * 6)
        var offset = 0
        for (i in 0 until count) {
            val sourceIndex = (i.toFloat() / count * data.size).toInt().coerceIn(0, data.lastIndex)
            val raw = (data[sourceIndex] * config.sensitivity).coerceIn(0f, 1f)
            val height = (0.025f + raw * 0.88f).coerceIn(0.025f, 0.92f)
            val x0 = -1f + i * slot + gap * 0.5f
            val x1 = min(1f, x0 + barWidth)
            val y0 = -0.94f
            val y1 = y0 + height
            val hue = i.toFloat() / count
            val red = (0.15f + 0.75f * hue).coerceIn(0f, 1f)
            val green = (0.72f - 0.35f * hue).coerceIn(0f, 1f)
            val blue = (0.95f - 0.55f * hue).coerceIn(0f, 1f)
            val alpha = config.opacity
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
            appendQuad(x0, y0, x1, y1, alpha)
            if (config.mirror) {
                if (config.glow > 0f) appendQuad(x0 - 0.006f, -y1 - 0.008f, x1 + 0.006f, -y0 + 0.008f, alpha * config.glow * 0.22f)
                appendQuad(x0, -y1, x1, -y0, alpha)
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
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, offset / 6)

        if (config.particlesEnabled && config.particleCount > 0) {
            val points = particleBuffer ?: return
            points.clear()
            val now = System.nanoTime() / 1_000_000_000.0
            val energy = data.average().coerceIn(0f, 1f)
            for (i in 0 until config.particleCount) {
                val seed = i.toFloat()
                val phase = ((now * config.particleSpeed * 0.12 + seed * 0.6180339) % 1.0).toFloat()
                val angle = seed * 2.3999632f
                val radial = (0.12f + ((seed * 0.7548776f) % 1f) * config.particleSpread) * (0.65f + energy * 0.75f)
                val x = (cos(angle + now.toFloat() * 0.10f * config.particleSpeed) * radial).coerceIn(-0.98f, 0.98f)
                val y = (phase * 2f - 1f + sin(angle * 1.7f + now.toFloat() * 0.4f) * 0.08f).coerceIn(-0.98f, 0.98f)
                val alpha = (config.opacity * (0.30f + energy * 0.70f) * (0.45f + ((seed * 0.31f) % 0.55f))).coerceIn(0f, 1f)
                points.put(x)
                points.put(y)
                points.put(0.48f + energy * 0.30f)
                points.put(0.78f + energy * 0.18f)
                points.put(1f)
                points.put(alpha)
            }
            points.position(0)
            GLES20.glUniform1f(roundPointHandle, 1f)
            GLES20.glUniform1f(pointSizeHandle, config.particleSize)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 24, points)
            points.position(2)
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 24, points)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, config.particleCount)
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
            MeasureWhat.BEAT_TRIGGER_ANIM -> 0.5f + beat * 0.5f
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
