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
    private var vertexBuffer: FloatBuffer? = null
    private val vertexCapacity = 128 * 6 * 6

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
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vColor = aColor;
            }
        """.trimIndent()
        val fragmentShader = """
            precision mediump float;
            varying vec4 vColor;
            void main() { gl_FragColor = vColor; }
        """.trimIndent()
        program = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vertexShader), compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader))
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        vertexBuffer = ByteBuffer.allocateDirect(vertexCapacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
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
        val vertices = FloatArray(count * 6 * 6)
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
            val points = floatArrayOf(
                x0,y0, x1,y0, x1,y1,
                x0,y0, x1,y1, x0,y1
            )
            for (p in 0 until 6) {
                vertices[offset++] = points[p * 2]
                vertices[offset++] = points[p * 2 + 1]
                vertices[offset++] = red
                vertices[offset++] = green
                vertices[offset++] = blue
                vertices[offset++] = alpha
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
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count * 6)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
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
