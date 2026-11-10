package com.auralis.app.visualizer

import android.media.audiofx.Visualizer
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * Reads FFT frames from the audio session created by this app's ExoPlayer.
 * Android requires RECORD_AUDIO permission for the Visualizer API.
 */
class VisualizerCapture(
    private val smoothing: Float = 0.35f,
    private val onSpectrum: (FloatArray) -> Unit
) {
    private var visualizer: Visualizer? = null
    private var smoothed = FloatArray(128)

    fun start(audioSessionId: Int) {
        stop()
        if (audioSessionId <= 0) return

        runCatching {
            val capture = Visualizer(audioSessionId)
            val range = Visualizer.getCaptureSizeRange()
            capture.captureSize = range[1]
            capture.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 8) return
                        val result = FloatArray(128)
                        val bins = fft.size / 2
                        for (bar in result.indices) {
                            // Logarithmic grouping gives bass frequencies more screen space.
                            val start = (2.0 * (bins / 2.0).pow(bar / 128.0)).toInt().coerceIn(1, bins - 1)
                            val end = (2.0 * (bins / 2.0).pow((bar + 1) / 128.0)).toInt()
                                .coerceIn(start + 1, bins)
                            var peak = 0f
                            for (bin in start until end) {
                                val real = fft[bin * 2].toInt()
                                val imaginary = fft[bin * 2 + 1].toInt()
                                val magnitude = hypot(real.toDouble(), imaginary.toDouble()).toFloat() / 128f
                                if (magnitude > peak) peak = magnitude
                            }
                            result[bar] = (ln(1f + peak * 8f) / ln(9f)).coerceIn(0f, 1f)
                        }
                        for (i in result.indices) {
                            smoothed[i] = smoothed[i] * smoothing.coerceIn(0f, 0.95f) + result[i] * (1f - smoothing.coerceIn(0f, 0.95f))
                        }
                        onSpectrum(smoothed.copyOf())
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true
            )
            capture.enabled = true
            visualizer = capture
        }
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        smoothed.fill(0f)
    }
}
