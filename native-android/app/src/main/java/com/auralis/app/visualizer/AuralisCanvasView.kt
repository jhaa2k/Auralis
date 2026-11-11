package com.auralis.app.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.sin

/**
 * Canvas-backed preview surface. Supply immutable project snapshots and current FFT bands.
 * It draws layers in list order (bottom to top); advanced GPU-only effects are intentionally
 * represented as explicit extension points rather than silently approximated.
 */
class AuralisCanvasView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var project = VisualizerProject(id = "preview")
    private var spectrum: FloatArray = FloatArray(64)
    private var beat: Float = 0f

    fun submit(project: VisualizerProject, fft: FloatArray, beatLevel: Float = 0f) {
        this.project = project.normalized()
        this.spectrum = fft.copyOf()
        this.beat = beatLevel.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(project.backgroundColor.toInt())
        project.layers.filter { it.visible }.forEach { layer -> drawLayer(canvas, layer) }
    }

    private fun drawLayer(canvas: Canvas, layer: Layer) {
        val save = canvas.save()
        val t = layer.transform
        canvas.translate(t.x * width, t.y * height)
        canvas.rotate(t.rotation)
        canvas.scale(t.scaleX, t.scaleY)
        paint.alpha = (t.opacity * 255f).toInt().coerceIn(0, 255)
        paint.color = runCatching { android.graphics.Color.parseColor(layer.colorHex) }.getOrDefault(android.graphics.Color.WHITE)
        paint.style = Paint.Style.FILL
        paint.xfermode = when (t.blendMode) {
            StudioBlendMode.NORMAL -> null
            StudioBlendMode.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
            StudioBlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            StudioBlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            StudioBlendMode.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            StudioBlendMode.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            StudioBlendMode.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        }
        when (layer.type) {
            LayerType.BARS -> drawBars(canvas, layer)
            LayerType.TEXT -> drawText(canvas, layer)
            LayerType.PARTICLES -> drawParticles(canvas, layer)
            LayerType.COMPOSITION -> canvas.drawRect(-width.toFloat() / 2f, -height.toFloat() / 2f, width.toFloat() / 2f, height.toFloat() / 2f, paint)
            else -> Unit // Image/effect/shader layers are handled by their dedicated render passes.
        }
        paint.xfermode = null
        paint.alpha = 255
        canvas.restoreToCount(save)
    }

    private fun drawBars(canvas: Canvas, layer: Layer) {
        val count = layer.barCount.coerceIn(4, 256)
        val slot = width.toFloat() / count
        val barWidth = max(1f, slot * layer.barThickness.coerceIn(0.05f, 1f))
        val baseY = height * 0.5f
        for (i in 0 until count) {
            val value = spectrum.getOrElse(i * spectrum.size / count) { 0f }.coerceIn(0f, 1f)
            val h = max(2f, value * height * 0.42f)
            val x = -width / 2f + i * slot + (slot - barWidth) / 2f
            bounds.set(x, baseY - h, x + barWidth, baseY)
            canvas.drawRect(bounds, paint)
            if (layer.mirror) {
                bounds.set(x, baseY, x + barWidth, baseY + h)
                canvas.drawRect(bounds, paint)
            }
        }
    }

    private fun drawText(canvas: Canvas, layer: Layer) {
        paint.textSize = layer.fontSizeSp * resources.displayMetrics.scaledDensity
        paint.typeface = android.graphics.Typeface.create(layer.fontFamily, android.graphics.Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(layer.text, 0f, 0f, paint)
    }

    private fun drawParticles(canvas: Canvas, layer: Layer) {
        val count = layer.particleSpawnRate.toInt().coerceIn(0, 250)
        for (i in 0 until count) {
            val phase = i * 2.399f + beat * 2f
            val radius = (0.08f + (i % 17) / 20f) * minOf(width, height)
            val x = sin(phase) * radius
            val y = kotlin.math.cos(phase * 1.13f) * radius
            canvas.drawCircle(x, y, 1.5f + (i % 4), paint)
        }
    }
}
