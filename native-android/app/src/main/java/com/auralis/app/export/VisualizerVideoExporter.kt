package com.auralis.app.export

import android.content.Context
import android.media.*
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import com.auralis.app.visualizer.VisualizerSettings
import com.auralis.app.visualizer.BarSegmentStyle

data class VideoExportConfig(
    val width: Int = 1920, val height: Int = 1080, val fps: Int = 30,
    val bitrate: Int = 8_000_000, val includeAudio: Boolean = true,
    val visualizerLayers: List<VisualizerSettings> = emptyList()
) {
    fun safe() = copy(width = width.coerceIn(640,3840), height = height.coerceIn(480,2160),
        fps = listOf(25,30,50,60).minBy { abs(it-fps) }, bitrate = bitrate.coerceIn(900_000,35_000_000))
}

/** Offline MP4 renderer. It decodes the chosen audio to PCM/FFT, renders GPU frames,
 * encodes AVC with MediaCodec, then muxes the original audio track into the MP4. */
object VisualizerVideoExporter {
    private data class Analysis(val bands: List<FloatArray>, val durationUs: Long, val sampleRate: Int)

    suspend fun export(context: Context, audio: Uri, destination: Uri,
        config: VideoExportConfig = VideoExportConfig(), progress: (Float,String)->Unit = { _,_-> }) =
        withContext(Dispatchers.IO) {
            val cfg=config.safe()
            val temp=File(context.cacheDir,"auralis-export-"+System.nanoTime()+".mp4")
            var fd: ParcelFileDescriptor?=null
            try {
                progress(0f,"DECODING AUDIO")
                val data=decode(context,audio)
                require(data.bands.isNotEmpty() && data.durationUs>0) { "No decodable audio track." }
                progress(.08f,"RENDERING VIDEO")
                encode(temp,data,cfg) { progress(.08f+.72f*it,"RENDERING VISUALIZER") }
                fd=context.contentResolver.openFileDescriptor(destination,"w") ?: error("Cannot open destination.")
                if(cfg.includeAudio) mux(context,temp,audio,fd!!.fileDescriptor)
                else muxVideo(temp,fd!!.fileDescriptor)
                progress(1f,"EXPORT COMPLETE")
            } catch(t:Throwable) {
                runCatching { context.contentResolver.delete(destination,null,null) }
                throw t
            } finally { runCatching { fd?.close() }; temp.delete() }
        }

    private fun decode(ctx:Context,uri:Uri):Analysis {
        val ex=MediaExtractor(); var dec:MediaCodec?=null
        try {
            ex.setDataSource(ctx,uri,null)
            val ti=(0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: error("No audio track.")
            val f=ex.getTrackFormat(ti); val mime=f.getString(MediaFormat.KEY_MIME)!!
            val duration=if(f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            ex.selectTrack(ti); dec=MediaCodec.createDecoderByType(mime); dec.configure(f,null,null,0); dec.start()
            var channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE); var isFloat=false
            val window=FloatArray(1024); var fill=0; val result=ArrayList<FloatArray>()
            val info=MediaCodec.BufferInfo(); var inputDone=false; var outputDone=false
            while(!outputDone) {
                if(!inputDone) {
                    val ix=dec.dequeueInputBuffer(10000)
                    if(ix>=0) {
                        val b=dec.getInputBuffer(ix)!!; b.clear(); val n=ex.readSampleData(b,0)
                        if(n<0) { dec.queueInputBuffer(ix,0,0,0L,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true }
                        else { dec.queueInputBuffer(ix,0,n,ex.sampleTime,0); ex.advance() }
                    }
                }
                val ix=dec.dequeueOutputBuffer(info,10000)
                if(ix==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of=dec.outputFormat; channels=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    rate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    isFloat=of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING)==AudioFormat.ENCODING_PCM_FLOAT
                } else if(ix>=0) {
                    if(info.size>0) {
                        val b=dec.getOutputBuffer(ix)!!; b.position(info.offset); b.limit(info.offset+info.size)
                        if(isFloat) {
                            val pcm=b.slice().order(ByteOrder.nativeOrder()).asFloatBuffer()
                            while(pcm.remaining()>=channels) { var v=0f; repeat(channels){v+=pcm.get()}; window[fill++]=v/channels
                                if(fill==1024){result.add(fft(window));fill=0} }
                        } else {
                            val pcm=b.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                            while(pcm.remaining()>=channels) { var v=0f; repeat(channels){v+=pcm.get()/32768f}; window[fill++]=v/channels
                                if(fill==1024){result.add(fft(window));fill=0} }
                        }
                    }
                    outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0; dec.releaseOutputBuffer(ix,false)
                }
            }
            if(fill>0){for(i in fill until 1024)window[i]=0f;result.add(fft(window))}
            return Analysis(result,duration.takeIf{it>0}?:result.size.toLong()*1024*1_000_000/rate,rate)
        } finally { runCatching{dec?.stop()};runCatching{dec?.release()};ex.release() }
    }

    private fun fft(input:FloatArray):FloatArray {
        val n=input.size; val re=DoubleArray(n){input[it]*(.5-.5*cos(2*PI*it/(n-1)))}; val im=DoubleArray(n)
        var j=0
        for(i in 1 until n){var bit=n shr 1;while(j and bit!=0){j=j xor bit;bit=bit shr 1};j=j xor bit
            if(i<j){val t=re[i];re[i]=re[j];re[j]=t}}
        var len=2
        while(len<=n){val a=-2*PI/len;val wr0=cos(a);val wi0=sin(a);var base=0
            while(base<n){var wr=1.0;var wi=0.0
                for(k in 0 until len/2){val e=base+k;val o=e+len/2;val tr=wr*re[o]-wi*im[o];val ti=wr*im[o]+wi*re[o]
                    re[o]=re[e]-tr;im[o]=im[e]-ti;re[e]+=tr;im[e]+=ti
                    val nr=wr*wr0-wi*wi0;wi=wr*wi0+wi*wr0;wr=nr};base+=len};len*=2}
        return FloatArray(64){band->val lo=(1+2.0.pow(band/8.0)).toInt().coerceAtMost(511);val hi=(1+2.0.pow((band+1)/8.0)).toInt().coerceAtMost(512)
            var peak=0.0;for(k in lo until max(lo+1,hi))peak=max(peak,sqrt(re[k]*re[k]+im[k]*im[k])/n)
            (ln(1+peak*90)/ln(91.0)).toFloat().coerceIn(0f,1f)}
    }

    private fun encode(file:File,a:Analysis,c:VideoExportConfig,progress:(Float)->Unit) {
        val format=MediaFormat.createVideoFormat("video/avc",c.width,c.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE,c.bitrate);setInteger(MediaFormat.KEY_FRAME_RATE,c.fps);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2)}
        val codec=MediaCodec.createEncoderByType("video/avc");var gl:GlSurface?=null;var mux:MediaMuxer?=null
        try {
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);gl=GlSurface(codec.createInputSurface(),c.width,c.height)
            mux=MediaMuxer(file.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);codec.start()
            var track=-1;var started=false;val info=MediaCodec.BufferInfo()
            val total=(a.durationUs*c.fps/1_000_000).toInt().coerceAtLeast(1)
            fun drain(eos:Boolean=false) {
                while(true){val ix=codec.dequeueOutputBuffer(info,if(eos)10000 else 0)
                    when {
                        ix==MediaCodec.INFO_TRY_AGAIN_LATER->return
                        ix==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{if(track<0){track=mux.addTrack(codec.outputFormat);mux.start();started=true}}
                        ix>=0->{val b=codec.getOutputBuffer(ix)!!;if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG!=0)info.size=0
                            if(info.size>0){check(started);b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info)}
                            val done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(ix,false);if(done)return}
                    }}
            }
            for(i in 0 until total){val pts=i.toLong()*1_000_000/c.fps
                val bi=(pts*a.sampleRate/(1024L*1_000_000)).toInt().coerceIn(0,a.bands.lastIndex)
                gl.draw(a.bands[bi], c.visualizerLayers);EGLExt.eglPresentationTimeANDROID(gl.display,gl.surface,pts*1000)
                check(EGL14.eglSwapBuffers(gl.display,gl.surface));drain()
                if(i%c.fps==0)progress(i.toFloat()/total)}
            codec.signalEndOfInputStream()
            var done=false;var attempts=0
            while(!done && attempts++<10000){val old=info.flags;drain(true);done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                if(old==info.flags && !done) Thread.yield()}
            check(done){"Video encoder timed out."};progress(1f)
        } finally {runCatching{gl?.release()};runCatching{codec.stop()};runCatching{codec.release()};runCatching{mux?.stop()};runCatching{mux?.release()}}
    }

    private fun mux(ctx:Context,video:File,audio:Uri,fd:java.io.FileDescriptor) {
        val v=MediaExtractor();val a=MediaExtractor();var m:MediaMuxer?=null
        try {v.setDataSource(video.absolutePath);a.setDataSource(ctx,audio,null)
            val vi=(0 until v.trackCount).first{v.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}
            val ai=(0 until a.trackCount).first{a.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
            v.selectTrack(vi);a.selectTrack(ai);m=MediaMuxer(fd,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vt=m.addTrack(v.getTrackFormat(vi));val at=m.addTrack(a.getTrackFormat(ai));m.start()
            copy(v,m,vt);copy(a,m,at)
        } finally {runCatching{m?.stop()};runCatching{m?.release()};v.release();a.release()}
    }
    private fun muxVideo(video:File,fd:java.io.FileDescriptor) {
        val e=MediaExtractor();var m:MediaMuxer?=null
        try {e.setDataSource(video.absolutePath);val i=(0 until e.trackCount).first{e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}
            e.selectTrack(i);m=MediaMuxer(fd,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);val t=m.addTrack(e.getTrackFormat(i));m.start();copy(e,m,t)
        } finally {runCatching{m?.stop()};runCatching{m?.release()};e.release()}
    }
    private fun copy(e:MediaExtractor,m:MediaMuxer,t:Int) {
        val b=ByteBuffer.allocate(2*1024*1024);val info=MediaCodec.BufferInfo()
        while(true){b.clear();val n=e.readSampleData(b,0);if(n<0)break
            info.set(0,n,e.sampleTime,e.sampleFlags);m.writeSampleData(t,b,info);e.advance()}
    }

    private class GlSurface(input:android.view.Surface,w:Int,h:Int) {
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);private val context:android.opengl.EGLContext;val surface:android.opengl.EGLSurface
        private val program:Int;private val p:Int;private val col:Int;private val offsetHandle:Int;private val tintHandle:Int;private var buf:FloatBuffer;private val previousBarHeights = FloatArray(128);private var previousEnergy = 0f;private var lastBeatTimestamp = 0.0
        init {
            val v=IntArray(2);check(EGL14.eglInitialize(display,v,0,v,1))
            val attrs=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE)
            val cs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1);check(EGL14.eglChooseConfig(display,attrs,0,cs,0,1,n,0)&&n[0]>0)
            context=EGL14.eglCreateContext(display,cs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            surface=EGL14.eglCreateWindowSurface(display,cs[0],input,intArrayOf(EGL14.EGL_NONE),0)
            check(EGL14.eglMakeCurrent(display,surface,surface,context))
            val vs="attribute vec2 p;attribute vec4 c;varying vec4 v;uniform vec2 uOffset;void main(){gl_Position=vec4(p+uOffset,0.,1.);v=c;}"
            val fs="precision mediump float;varying vec4 v;uniform vec4 uTint;void main(){gl_FragColor=v*uTint;}"
            program=link(shader(GLES20.GL_VERTEX_SHADER,vs),shader(GLES20.GL_FRAGMENT_SHADER,fs));p=GLES20.glGetAttribLocation(program,"p");col=GLES20.glGetAttribLocation(program,"c");offsetHandle=GLES20.glGetUniformLocation(program,"uOffset");tintHandle=GLES20.glGetUniformLocation(program,"uTint")
            buf=ByteBuffer.allocateDirect(64*36*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            GLES20.glViewport(0,0,w,h);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
        fun draw(bands: FloatArray, layers: List<VisualizerSettings>) {
            val configs = (layers.ifEmpty { listOf(VisualizerSettings()) }).take(16)
            val requiredFloats = configs.sumOf { layer ->
                val segmentStyles = layer.segmentStyles.take(3).ifEmpty { listOf(BarSegmentStyle()) }
                val mirrorY = layer.mirror || segmentStyles.any { it.mirror }
                val mirrorCopies = 1 + (if (mirrorY) 1 else 0) + (if (layer.mirrorX) 1 else 0) +
                    (if (mirrorY && layer.mirrorX) 1 else 0)
                val bars = if (layer.barsEnabled) layer.barCount.coerceIn(16,128) *
                    (segmentStyles.size * 20 * mirrorCopies + 4) * 18 else 0
                val particleMirrors = 1 + (if (layer.particleMirrorX) 1 else 0) +
                    (if (layer.particleMirrorY) 1 else 0) +
                    (if (layer.particleMirrorX && layer.particleMirrorY) 1 else 0)
                val particleTails = if (layer.particleTrailLength > 0f) 3 else 1
                val particles = if (layer.particlesEnabled) layer.particleCount.coerceIn(0,5000) * particleMirrors * particleTails * 36 else 0
                bars + particles
            }.coerceAtLeast(36)
            if (buf.capacity() < requiredFloats) {
                buf = ByteBuffer.allocateDirect(requiredFloats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            }
            buf.clear()
            val composition = configs.firstOrNull() ?: VisualizerSettings()
            val background = composition.backgroundColor
            val backgroundAlpha = ((background shr 24) and 255).toFloat()/255f * composition.compositionAlpha
            GLES20.glClearColor(
                ((background shr 16) and 255).toFloat()/255f,
                ((background shr 8) and 255).toFloat()/255f,
                (background and 255).toFloat()/255f,
                backgroundAlpha
            )
            if (composition.clearCanvas) GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val time = System.nanoTime() / 1_000_000_000.0
            val energy = bands.average().toFloat().coerceIn(0f, 1f)
            val beatPulse = ((energy - previousEnergy) * 4f).coerceIn(0f, 1f)
            if (beatPulse > 0.12f) lastBeatTimestamp = time
            previousEnergy = previousEnergy * .72f + energy * .28f
            fun measureValue(name: String, elapsed: Float, currentEnergy: Float, beat: Float, trackPosition: Float): Float {
                val phase = elapsed * .7f
                return when (name) {
                    "Beat" -> .5f + beat * .5f
                    "TotalTime" -> (elapsed * .25f) % 1f
                    "TotalTimeBackward" -> 1f - ((elapsed * .25f) % 1f)
                    "TotalTimeWhenPlaying" -> if (currentEnergy > .015f) (elapsed * .25f) % 1f else .5f
                    "TotalTimeAndBeat" -> (((elapsed * .25f) % 1f) * .65f + beat * .35f).coerceIn(0f, 1f)
                    "TrackPosition", "TrackProgressPercentage" -> trackPosition
                    "BeatRandomShake" -> .5f + beat * sin(elapsed * 37f) * .5f
                    "BeatCamShakeRotMore", "BeatCamShakeOnly", "BeatTriggerAnim", "BeatPulseScale",
                    "BeatFlashColor", "BeatGlowIntensity", "BeatHorizontalStretch", "BeatVerticalBounce" -> .5f + beat * .5f
                    "BeatCamShakeRotLess" -> .5f - beat * .5f
                    "ConstantShakeRotMore" -> .5f + sin(phase * 19f) * .5f
                    "ConstantShakeRotLess" -> .5f - sin(phase * 19f) * .5f
                    "ConstantSlowRotate" -> (elapsed * .08f) % 1f
                    "ConstantOrbitMove" -> .5f + sin(elapsed * .35f) * .5f
                    "ConstantLinearScroll" -> (elapsed * .12f) % 1f
                    "TrackRemainingTime" -> 1f - trackPosition
                    "TrackIntroOutroTrigger" -> if (trackPosition < .08f || trackPosition > .92f) .5f + beat * .5f else .5f
                    "VolumeLevelResponsive", "PitchFrequencyResponsive" -> currentEnergy
                    else -> .5f
                }.coerceIn(0f, 1f)
            }
            fun vertex(x: Float, y: Float, red: Float, green: Float, blue: Float, alpha: Float, config: VisualizerSettings, rotationOffset: Float = 0f) {
                val angle = Math.toRadians((config.rotation + rotationOffset).toDouble())
                val cs = cos(angle).toFloat(); val sn = sin(angle).toFloat()
                val cx = (config.positionX - .5f) * 2f; val cy = (.5f - config.positionY) * 2f
                val sx = x * config.scale * config.scaleX; val sy = y * config.scale * config.scaleY
                buf.put(sx*cs-sy*sn+cx); buf.put(sx*sn+sy*cs+cy)
                buf.put(red); buf.put(green); buf.put(blue); buf.put(alpha)
            }
            fun triangle(
                ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float,
                red: Float, green: Float, blue: Float, alpha: Float,
                config: VisualizerSettings, rotationOffset: Float = 0f
            ) {
                vertex(ax,ay,red,green,blue,alpha,config,rotationOffset)
                vertex(bx,by,red,green,blue,alpha,config,rotationOffset)
                vertex(cx,cy,red,green,blue,alpha,config,rotationOffset)
            }
            fun quad(
                left: Float, bottom: Float, right: Float, top: Float,
                red: Float, green: Float, blue: Float, alpha: Float,
                config: VisualizerSettings, rotationOffset: Float = 0f
            ) {
                triangle(left,bottom,right,bottom,right,top,red,green,blue,alpha,config,rotationOffset)
                triangle(left,bottom,right,top,left,top,red,green,blue,alpha,config,rotationOffset)
            }
            fun polygon(
                left: Float, bottom: Float, right: Float, top: Float, sides: Int,
                red: Float, green: Float, blue: Float, alpha: Float,
                config: VisualizerSettings, star: Boolean = false, rotationOffset: Float = 0f, flipVertical: Boolean = false
            ) {
                val cx = (left + right) * .5f; val cy = (bottom + top) * .5f
                val rx = (right - left) * .5f; val ry = (top - bottom) * .5f
                val points = ArrayList<Pair<Float,Float>>(sides)
                for (index in 0 until sides) {
                    val a = -PI/2.0 + index * PI * 2.0 / sides
                    val radius = if (star && index % 2 == 1) .42f else 1f
                    points.add(Pair(cx + cos(a).toFloat()*rx*radius, cy + (if (flipVertical) -1f else 1f)*sin(a).toFloat()*ry*radius))
                }
                points.indices.forEach { index ->
                    val p0 = points[index]; val p1 = points[(index+1)%points.size]
                    triangle(cx,cy,p0.first,p0.second,p1.first,p1.second,red,green,blue,alpha,config,rotationOffset)
                }
            }
            fun shape(
                name: String, left: Float, bottom: Float, right: Float, top: Float,
                red: Float, green: Float, blue: Float, alpha: Float,
                config: VisualizerSettings, rotationOffset: Float = 0f, flipVertical: Boolean = false
            ) {
                when (name) {
                    "None" -> Unit
                    "Line", "HorizontalLine" -> quad(left,(bottom+top)*.5f-.0035f,right,(bottom+top)*.5f+.0035f,red,green,blue,alpha,config,rotationOffset)
                    "VerticalLine" -> quad((left+right)*.5f-.0035f,bottom,(left+right)*.5f+.0035f,top,red,green,blue,alpha,config,rotationOffset)
                    "Circle", "Oval" -> polygon(left,bottom,right,top,20,red,green,blue,alpha,config,rotationOffset=rotationOffset,flipVertical=flipVertical)
                    "Triangle" -> polygon(left,bottom,right,top,3,red,green,blue,alpha,config,rotationOffset=rotationOffset,flipVertical=flipVertical)
                    "Star" -> polygon(left,bottom,right,top,10,red,green,blue,alpha,config,star=true,rotationOffset=rotationOffset,flipVertical=flipVertical)
                    "Pentagon" -> polygon(left,bottom,right,top,5,red,green,blue,alpha,config,rotationOffset=rotationOffset,flipVertical=flipVertical)
                    "Hexagon", "SidedPolygon" -> polygon(left,bottom,right,top,6,red,green,blue,alpha,config,rotationOffset=rotationOffset,flipVertical=flipVertical)
                    "Square" -> {
                        val side = minOf(right-left,top-bottom); val cx=(left+right)*.5f
                        quad(cx-side*.5f,bottom,cx+side*.5f,bottom+side,red,green,blue,alpha,config,rotationOffset)
                    }
                    else -> quad(left,bottom,right,top,red,green,blue,alpha,config,rotationOffset)
                }
            }
            configs.forEach { config ->
                when (config.blendMode) {
                    "Add" -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
                    "Screen" -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR)
                    "Multiply" -> GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ZERO)
                    else -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                }
                if (config.barsEnabled) {
                    val count = config.barCount.coerceIn(16,128)
                    val slot = 2f/count; val width = slot*config.barWidth.coerceIn(.15f,1f)
                    val gap = config.segmentGap.coerceIn(0f,.45f)
                    val styles = config.segmentStyles.take(3).ifEmpty { listOf(BarSegmentStyle()) }
                    for (i in 0 until count) {
                        val progress = i.toFloat()/(count-1).coerceAtLeast(1)
                        val colorProgress = (progress + config.colorBlendOffset).let { it - floor(it) }
                        val index = ((if(config.flipInput) 1f-progress else progress)*(bands.size-1)).toInt().coerceIn(0,bands.lastIndex)
                        val height = (config.barMinHeight + bands[index]*config.sensitivity*config.barHeight*.88f).coerceIn(.005f,1.5f)
                        val left=-1f+i*slot+(slot-width)*.5f; val right=left+width; val base=-.94f
                        val previousHeight = previousBarHeights[i]
                        fun effectChannel(color: Long, shift: Int) = ((color shr shift) and 255).toFloat()/255f
                        fun drawGlow() {
                            if (config.glow <= 0f) return
                            val glowSpread = .002f + config.glowRadius / 100f * .04f
                            quad(left-glowSpread,base-glowSpread*1.25f,right+glowSpread,base+height+glowSpread*1.25f,
                                effectChannel(config.blurColor,16),effectChannel(config.blurColor,8),effectChannel(config.blurColor,0),
                                config.opacity*config.compositionAlpha*config.glow*.22f*effectChannel(config.blurColor,24),config)
                        }
                        fun drawMotionTrail() {
                            if (config.motionBlurAmount <= 0f || previousHeight <= 0f) return
                            val sourceFactor = when (config.motionBlurSource) {
                                "AudioVolume" -> bands[index].coerceIn(0f,1f)
                                else -> (kotlin.math.abs(height-previousHeight)/height.coerceAtLeast(.05f)).coerceIn(.15f,1f)
                            }
                            val trailOffset = if (config.motionBlurRelativeMotion) .006f+config.motionBlurAmount*.035f*sourceFactor else config.motionBlurAmount*.035f
                            val trailTop = if (config.motionBlurRelativeMotion) base+previousHeight else base+height*.68f
                            quad(left-trailOffset,base,right-trailOffset,trailTop,
                                effectChannel(config.motionBlurColor,16),effectChannel(config.motionBlurColor,8),effectChannel(config.motionBlurColor,0),
                                config.opacity*config.compositionAlpha*config.motionBlurAmount*.32f*sourceFactor*effectChannel(config.motionBlurColor,24),config)
                        }
                        if (config.blurShowSharpUnder) drawGlow()
                        if (config.motionBlurShowSharpUnder) drawMotionTrail()
                        val drawSharp = config.blurShowSharp && config.motionBlurShowSharp
                        if (drawSharp) {
                            var cursor = base
                            val defaultSegmentHeight = height / styles.size.coerceAtLeast(1)
                            styles.forEach { style ->
                                val partHeight = if (style.fixedHeight > 0f) (style.fixedHeight*.25f).coerceAtMost(1.5f) else defaultSegmentHeight
                                val segmentHeight = (partHeight*style.heightMultiplier).coerceIn(0f,1.5f)
                                val bottom=cursor+segmentHeight*gap*.5f
                                val top=cursor+segmentHeight-segmentHeight*gap*.5f
                                if(top>bottom) {
                                    fun segmentChannel(shift:Int) =
                                        ((((style.colorFrom shr shift) and 255).toFloat() +
                                            (((style.colorTo shr shift) and 255).toFloat()-((style.colorFrom shr shift) and 255).toFloat())*colorProgress)/255f)
                                    val sr=segmentChannel(16); val sg=segmentChannel(8); val sb=segmentChannel(0)
                                    val alpha=config.opacity*config.compositionAlpha*segmentChannel(24)
                                    val flip = style.flipOther && i%2==1
                                    fun mirrorChannel(shift:Int) = ((config.mirrorColor shr shift) and 255).toFloat()/255f
                                    fun drawSegment(
                                        l:Float,b:Float,r:Float,t:Float,mirrorCopy:Boolean=false,offsetX:Float=0f,offsetY:Float=0f
                                    ) {
                                        val tintR=if(mirrorCopy) mirrorChannel(16) else 1f
                                        val tintG=if(mirrorCopy) mirrorChannel(8) else 1f
                                        val tintB=if(mirrorCopy) mirrorChannel(0) else 1f
                                        val tintA=if(mirrorCopy) mirrorChannel(24) else 1f
                                        shape(
                                            style.shape,l+offsetX,b+offsetY,r+offsetX,t+offsetY,
                                            sr*tintR,sg*tintG,sb*tintB,alpha*tintA,config,
                                            flipVertical=flip || (mirrorCopy && config.mirrorFlip)
                                        )
                                    }
                                    drawSegment(left,bottom,right,top)
                                    if (style.mirror || config.mirror) drawSegment(left,-top,right,-bottom,true,offsetY=config.mirrorOffset)
                                    if (config.mirrorX) drawSegment(-right,bottom,-left,top,true,offsetX=config.mirrorOffset)
                                    if (config.mirrorX && (style.mirror || config.mirror)) {
                                        drawSegment(-right,-top,-left,-bottom,true,config.mirrorOffset,config.mirrorOffset)
                                    }
                                }
                                cursor += segmentHeight
                            }
                        }
                        if (!config.blurShowSharpUnder) drawGlow()
                        if (!config.motionBlurShowSharpUnder) drawMotionTrail()
                        previousBarHeights[i] = height
                    }
                }
                if (config.particlesEnabled && config.particleCount > 0) {
                    val count = config.particleCount.coerceIn(0, 5000)
                    val energy = bands.average().toFloat().coerceIn(0f, 1f)
                    fun channel(color: Long, shift: Int) = ((color shr shift) and 255).toFloat() / 255f
                    for (i in 0 until count) {
                        val seed = i.toFloat() + 1f
                        val randomA = (kotlin.math.abs(sin(seed * 12.9898f) * 43758.5453f) % 1f)
                        val randomB = (kotlin.math.abs(sin(seed * 78.233f) * 19341.381f) % 1f)
                        val life = config.particleLifetime.coerceAtLeast(0.1f)
                        val emissionOffset = i.toFloat() * config.particleSpawnTime
                        val phase = ((((time - emissionOffset) / life) + randomA) % 1.0).toFloat().coerceIn(0f, 1f)
                        val seedAngle = seed * 2.3999632f
                        val radius = kotlin.math.sqrt(randomB)
                        val cameraDepth = config.particlePerspectiveDepth * (.4f + randomB * .6f)
                        val perspectiveFactor = (config.particlePerspectiveFov / (config.particlePerspectiveFov + cameraDepth * .1f)).coerceIn(.1f, 1f)
                        val nearFadeFactor = (cameraDepth / (cameraDepth + config.particleNearCameraFade.coerceAtLeast(0f))).coerceIn(.05f, 1f)
                        val speedSignal = measureValue(
                            config.particleSpeedMeasure.label, time.toFloat(), energy, beatPulse, config.trackPositionFraction
                        )
                        val speedFactor = (1f + (speedSignal - .5f) * .8f).coerceIn(.1f, 2f)
                        val spawn = when (config.particleSpawnArea) {
                            "Circle" -> Pair(cos(seedAngle) * radius * config.particleSpread * .5f, sin(seedAngle) * radius * config.particleSpreadY * .5f)
                            "Line" -> Pair((randomA - .5f) * config.particleSpread, 0f)
                            "Point" -> Pair(0f, 0f)
                            else -> Pair((randomA - .5f) * config.particleSpread, (randomB - .5f) * config.particleSpreadY)
                        }
                        var direction = Math.toRadians(((if (config.particleRandomVector) randomB * 360f else config.particleVectorAngle) + config.particleVectorAngleZ * phase).toDouble())
                        if (config.particleVectorRelative) direction += kotlin.math.atan2(-spawn.second.toDouble(), -spawn.first.toDouble())
                        val directionX = cos(direction).toFloat()
                        val directionY = sin(direction).toFloat()
                        val randomSpeed = (1f + (randomA * 2f - 1f) * config.particleSpeedRandom / 10f).coerceAtLeast(0f)
                        val travel = (phase - .5f) * config.particleSpeed * randomSpeed * speedFactor * perspectiveFactor * .42f
                        var baseX = spawn.first + directionX * travel
                        var baseY = spawn.second + directionY * travel
                        when (config.particleForceField) {
                            "Gravity" -> {
                                baseX += config.particleGravityX * phase * phase * .025f
                                baseY += config.particleGravityY * phase * phase * .025f
                            }
                            "Wind" -> baseX += config.particleSpeed * phase * .09f
                            "Vortex" -> {
                                val vortexAngle = phase * 6.2831853f
                                val vx = baseX * cos(vortexAngle) - baseY * sin(vortexAngle)
                                val vy = baseX * sin(vortexAngle) + baseY * cos(vortexAngle)
                                baseX = vx; baseY = vy
                            }
                            "Attractor", "Repeller" -> {
                                val force = if (config.particleForceField == "Attractor") -1f else 1f
                                baseX += baseX * force * phase * .55f
                                baseY += baseY * force * phase * .55f
                            }
                        }
                        val waveFrequency = (config.particleWaveFrequency + randomA * config.particleWaveFrequencyRandom).coerceIn(0f, 50f)
                        val waveMagnitude = (config.particleWaveMagnitude + (randomB * 2f - 1f) * config.particleWaveMagnitudeRandom).coerceIn(0f, 10f)
                        val wave = sin(phase * waveFrequency * 6.2831853f + seedAngle) * waveMagnitude * .025f
                        baseX += -directionY * wave
                        baseY += directionX * wave
                        val particlePositionSignal = measureValue(
                            config.particlePositionMeasure.label, time.toFloat(), energy, beatPulse, config.trackPositionFraction
                        )
                        val particleScaleSignal = measureValue(
                            config.particleScaleMeasure.label, time.toFloat(), energy, beatPulse, config.trackPositionFraction
                        )
                        val particleRotationSignal = measureValue(
                            config.particleRotationMeasure.label, time.toFloat(), energy, beatPulse, config.trackPositionFraction
                        )
                        val particleAmount = config.particleMeasureAmount
                        val measureOffsetX = (config.particlePositionX - .5f) * 2f +
                            (particlePositionSignal - .5f) * particleAmount * .6f
                        val measureOffsetY = (.5f - config.particlePositionY) * 2f -
                            (particlePositionSignal - .5f) * particleAmount * .6f
                        val particleScale = (config.particleScale *
                            (1f + (particleScaleSignal - .5f) * particleAmount)).coerceIn(.1f, 4f)
                        val particleRotation = config.particleRotation +
                            (particleRotationSignal - .5f) * particleAmount * 90f
                        val x = (baseX * particleScale + measureOffsetX).coerceIn(-1.4f, 1.4f)
                        val y = (baseY * particleScale + measureOffsetY).coerceIn(-1.4f, 1.4f)
                        val fadeWindow = (config.particleFadeInOutTime / life).coerceIn(0f, .5f)
                        val fadeIn = if (fadeWindow <= 0f) 1f else (phase / fadeWindow).coerceIn(0f, 1f)
                        val fadeOut = if (fadeWindow <= 0f) 1f else ((1f - phase) / fadeWindow).coerceIn(0f, 1f)
                        val alphaFactor = config.particleOpacity * config.compositionAlpha * (.3f + energy * .7f) * fadeIn * fadeOut * nearFadeFactor
                        fun mix(shift: Int) = channel(config.particleColor, shift) +
                            (channel(config.particleColorTo, shift) - channel(config.particleColor, shift)) * phase
                        val red = mix(16); val green = mix(8); val blue = mix(0)
                        val alpha = mix(24) * alphaFactor
                        val startSize = if (config.particleFadeInSize > 0f) config.particleFadeInSize else config.particleSize
                        val endSize = (config.particleEndSize + (randomB * 2f - 1f) * config.particleEndSizeRandom).coerceIn(0f, 64f)
                        val zSizeFactor = 1f + config.particleEndSizeZFactor * (1f - perspectiveFactor) * .05f
                        val size = (startSize + (endSize - startSize) * phase) * particleScale * perspectiveFactor * zSizeFactor / 160f
                        val halfX = size * config.particleSizeAspectRatio
                        val halfY = size
                        val drawAngle = if (config.particleVelocityAngle) Math.toDegrees(direction).toFloat() else particleRotation
                        fun drawParticle(
                            px: Float, py: Float, alphaMul: Float = 1f, sizeMul: Float = 1f,
                            mirrorCopy: Boolean = false, offsetX: Float = 0f, offsetY: Float = 0f
                        ) {
                            val hx = halfX * sizeMul; val hy = halfY * sizeMul
                            val tintR = if (mirrorCopy) channel(config.mirrorColor, 16) else 1f
                            val tintG = if (mirrorCopy) channel(config.mirrorColor, 8) else 1f
                            val tintB = if (mirrorCopy) channel(config.mirrorColor, 0) else 1f
                            val tintA = if (mirrorCopy) channel(config.mirrorColor, 24) else 1f
                            val angle = drawAngle + if (mirrorCopy && config.mirrorFlip) 180f else 0f
                            quad(
                                px+offsetX-hx,py+offsetY-hy,px+offsetX+hx,py+offsetY+hy,
                                red*tintR,green*tintG,blue*tintB,alpha*alphaMul*tintA,config,angle
                            )
                        }
                        drawParticle(x, y)
                        if (config.particleTrailLength > 0f) {
                            val tail = config.particleTrailLength * .003f
                            drawParticle(x-directionX*tail,y-directionY*tail,.42f,.78f)
                            drawParticle(x-directionX*tail*2f,y-directionY*tail*2f,.18f,.58f)
                        }
                        if (config.particleMirrorX) {
                            drawParticle(-x, y, mirrorCopy = true, offsetX = config.mirrorOffset)
                            if (config.particleTrailLength > 0f) {
                                val tail = config.particleTrailLength * .003f
                                drawParticle(-x+directionX*tail,y-directionY*tail,.42f,.78f,true,config.mirrorOffset)
                                drawParticle(-x+directionX*tail*2f,y-directionY*tail*2f,.18f,.58f,true,config.mirrorOffset)
                            }
                        }
                        if (config.particleMirrorY) {
                            drawParticle(x, -y, mirrorCopy = true, offsetY = config.mirrorOffset)
                            if (config.particleTrailLength > 0f) {
                                val tail = config.particleTrailLength * .003f
                                drawParticle(x-directionX*tail,-y+directionY*tail,.42f,.78f,true,0f,config.mirrorOffset)
                                drawParticle(x-directionX*tail*2f,-y+directionY*tail*2f,.18f,.58f,true,0f,config.mirrorOffset)
                            }
                        }
                        if (config.particleMirrorX && config.particleMirrorY) {
                            drawParticle(-x, -y, mirrorCopy = true, offsetX = config.mirrorOffset, offsetY = config.mirrorOffset)
                            if (config.particleTrailLength > 0f) {
                                val tail = config.particleTrailLength * .003f
                                drawParticle(-x+directionX*tail,-y+directionY*tail,.42f,.78f,true,config.mirrorOffset,config.mirrorOffset)
                                drawParticle(-x+directionX*tail*2f,-y+directionY*tail*2f,.18f,.58f,true,config.mirrorOffset,config.mirrorOffset)
                            }
                        }
                    }
                }
            }
            val vertices=buf.position()/6
            buf.position(0)
            GLES20.glUseProgram(program)
            GLES20.glEnableVertexAttribArray(p); GLES20.glEnableVertexAttribArray(col)
            GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,24,buf)
            buf.position(2)
            GLES20.glVertexAttribPointer(col,4,GLES20.GL_FLOAT,false,24,buf)
            val rgb = configs.firstOrNull { it.rgbSplitAmount > 0f }
            val rgbActive = rgb != null && (!rgb.rgbSplitOnBeat || time - lastBeatTimestamp <= rgb.rgbGlitchDurationMs / 1000.0)
            if (rgbActive && rgb != null) {
                val shift = 0.002f + rgb.rgbSplitAmount * 0.028f
                val angle = Math.toRadians(rgb.rgbSplitAngle.toDouble())
                val dx = cos(angle).toFloat(); val dy = sin(angle).toFloat()
                val redShift = shift * (1f + rgb.rgbRedOffset / 100f)
                val greenShift = shift * (rgb.rgbGreenOffset / 100f)
                val blueShift = shift * (1f + rgb.rgbBlueOffset / 100f)
                GLES20.glUniform2f(offsetHandle,-dx*redShift,-dy*redShift)
                GLES20.glUniform4f(tintHandle,1f,0f,0f,.72f)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices)
                GLES20.glUniform2f(offsetHandle,dx*greenShift,dy*greenShift)
                GLES20.glUniform4f(tintHandle,0f,1f,0f,.72f)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices)
                GLES20.glUniform2f(offsetHandle,dx*blueShift,dy*blueShift)
                GLES20.glUniform4f(tintHandle,0f,0f,1f,.72f)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices)
            }
            GLES20.glUniform2f(offsetHandle,0f,0f)
            GLES20.glUniform4f(tintHandle,1f,1f,1f,1f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices)
            GLES20.glDisableVertexAttribArray(p); GLES20.glDisableVertexAttribArray(col)
        }
        fun release(){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)}
        private fun shader(type:Int,src:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]==GLES20.GL_TRUE){GLES20.glGetShaderInfoLog(s)};return s}
        private fun link(v:Int,f:Int):Int{val x=GLES20.glCreateProgram();GLES20.glAttachShader(x,v);GLES20.glAttachShader(x,f);GLES20.glLinkProgram(x);val ok=IntArray(1);GLES20.glGetProgramiv(x,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]==GLES20.GL_TRUE){GLES20.glGetProgramInfoLog(x)};return x}
    }
}