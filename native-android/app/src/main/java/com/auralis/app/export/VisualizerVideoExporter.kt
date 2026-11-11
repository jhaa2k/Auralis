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
        private val program:Int;private val p:Int;private val col:Int;private val offsetHandle:Int;private val tintHandle:Int;private var buf:FloatBuffer
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
                val bars = if (layer.barsEnabled) layer.barCount.coerceIn(16,128) *
                    (segmentStyles.size * 20 + 4) * 18 else 0
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
            val background = configs.firstOrNull()?.backgroundColor ?: 0xFF101116L
            GLES20.glClearColor(((background shr 16) and 255).toFloat()/255f, ((background shr 8) and 255).toFloat()/255f, (background and 255).toFloat()/255f, ((background shr 24) and 255).toFloat()/255f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            val time = System.nanoTime() / 1_000_000_000.0
            fun vertex(x: Float, y: Float, red: Float, green: Float, blue: Float, alpha: Float, config: VisualizerSettings, rotationOffset: Float = 0f) {
                val angle = Math.toRadians((config.rotation + rotationOffset).toDouble())
                val cs = cos(angle).toFloat(); val sn = sin(angle).toFloat()
                val cx = (config.positionX - .5f) * 2f; val cy = (.5f - config.positionY) * 2f
                val sx = x * config.scale; val sy = y * config.scale
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
                config: VisualizerSettings, star: Boolean = false, rotationOffset: Float = 0f
            ) {
                val cx = (left + right) * .5f; val cy = (bottom + top) * .5f
                val rx = (right - left) * .5f; val ry = (top - bottom) * .5f
                val points = ArrayList<Pair<Float,Float>>(sides)
                for (index in 0 until sides) {
                    val a = -PI/2.0 + index * PI * 2.0 / sides
                    val radius = if (star && index % 2 == 1) .42f else 1f
                    points.add(Pair(cx + cos(a).toFloat()*rx*radius, cy + sin(a).toFloat()*ry*radius))
                }
                points.indices.forEach { index ->
                    val p0 = points[index]; val p1 = points[(index+1)%points.size]
                    triangle(cx,cy,p0.first,p0.second,p1.first,p1.second,red,green,blue,alpha,config,rotationOffset)
                }
            }
            fun shape(
                name: String, left: Float, bottom: Float, right: Float, top: Float,
                red: Float, green: Float, blue: Float, alpha: Float,
                config: VisualizerSettings, rotationOffset: Float = 0f
            ) {
                when (name) {
                    "None" -> Unit
                    "Line", "HorizontalLine" -> quad(left,(bottom+top)*.5f-.0035f,right,(bottom+top)*.5f+.0035f,red,green,blue,alpha,config,rotationOffset)
                    "VerticalLine" -> quad((left+right)*.5f-.0035f,bottom,(left+right)*.5f+.0035f,top,red,green,blue,alpha,config,rotationOffset)
                    "Circle", "Oval" -> polygon(left,bottom,right,top,20,red,green,blue,alpha,config,rotationOffset=rotationOffset)
                    "Triangle" -> polygon(left,bottom,right,top,3,red,green,blue,alpha,config,rotationOffset=rotationOffset)
                    "Star", "Heart" -> polygon(left,bottom,right,top,10,red,green,blue,alpha,config,star=true,rotationOffset=rotationOffset)
                    "Pentagon" -> polygon(left,bottom,right,top,5,red,green,blue,alpha,config,rotationOffset=rotationOffset)
                    "Hexagon", "SidedPolygon" -> polygon(left,bottom,right,top,6,red,green,blue,alpha,config,rotationOffset=rotationOffset)
                    "Square" -> {
                        val side = minOf(right-left,top-bottom); val cx=(left+right)*.5f
                        quad(cx-side*.5f,bottom,cx+side*.5f,bottom+side,red,green,blue,alpha,config,rotationOffset)
                    }
                    else -> quad(left,bottom,right,top,red,green,blue,alpha,config,rotationOffset)
                }
            }
            configs.forEach { config ->
                if (config.barsEnabled) {
                    val count = config.barCount.coerceIn(16,128)
                    val slot = 2f/count; val width = slot*config.barWidth.coerceIn(.15f,1f)
                    val gap = config.segmentGap.coerceIn(0f,.45f)
                    val styles = config.segmentStyles.take(3).ifEmpty { listOf(BarSegmentStyle()) }
                    for (i in 0 until count) {
                        val progress = i.toFloat()/(count-1).coerceAtLeast(1)
                        val index = ((if(config.flipInput) 1f-progress else progress)*(bands.size-1)).toInt().coerceIn(0,bands.lastIndex)
                        val height = (config.barMinHeight + bands[index]*config.sensitivity*config.barHeight*.88f).coerceIn(.005f,1.5f)
                        val left=-1f+i*slot+(slot-width)*.5f; val right=left+width; val base=-.94f
                        var cursor = base
                        val defaultSegmentHeight = height / styles.size.coerceAtLeast(1)
                        styles.forEachIndexed { segmentIndex, style ->
                            val partHeight = if (style.fixedHeight > 0f) (style.fixedHeight*.25f).coerceAtMost(1.5f) else defaultSegmentHeight
                            val segmentHeight = (partHeight*style.heightMultiplier).coerceIn(0f,1.5f)
                            val bottom=cursor+segmentHeight*gap*.5f
                            val top=cursor+segmentHeight-segmentHeight*gap*.5f
                            if(top>bottom) {
                                fun segmentChannel(shift:Int) =
                                    ((((style.colorFrom shr shift) and 255).toFloat() +
                                        (((style.colorTo shr shift) and 255).toFloat()-((style.colorFrom shr shift) and 255).toFloat())*progress)/255f)
                                val sr=segmentChannel(16); val sg=segmentChannel(8); val sb=segmentChannel(0)
                                val alpha=config.opacity*segmentChannel(24)
                                if (config.glow > 0f) quad(left-.008f,bottom-.008f,right+.008f,top+.008f,sr,sg,sb,alpha*config.glow*.2f,config)
                                if (config.motionBlurAmount > 0f) quad(left-.006f,bottom,right-.006f,top,sr,sg,sb,alpha*config.motionBlurAmount*.28f,config)
                                val flip = if (style.flipOther && i%2==1) 180f else 0f
                                shape(style.shape,left,bottom,right,top,sr,sg,sb,alpha,config,flip)
                                if (style.mirror || config.mirror) shape(style.shape,left,-top,right,-bottom,sr,sg,sb,alpha,config,flip)
                                if (config.mirrorX) shape(style.shape,-right,bottom,-left,top,sr,sg,sb,alpha,config,flip)
                                if (config.mirrorX && (style.mirror || config.mirror)) shape(style.shape,-right,-top,-left,-bottom,sr,sg,sb,alpha,config,flip)
                            }
                            cursor += segmentHeight
                        }
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
                        val phase = (((time / life) + randomA) % 1.0).toFloat().coerceIn(0f, 1f)
                        val seedAngle = seed * 2.3999632f
                        val radius = kotlin.math.sqrt(randomB)
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
                        val travel = (phase - .5f) * config.particleSpeed * randomSpeed * .42f
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
                        val wave = sin(phase * config.particleWaveFrequency * 6.2831853f + seedAngle) * config.particleWaveMagnitude * .025f
                        baseX += -directionY * wave
                        baseY += directionX * wave
                        val measureOffsetX = (config.particlePositionX - .5f) * 2f
                        val measureOffsetY = (.5f - config.particlePositionY) * 2f
                        val x = (baseX * config.particleScale + measureOffsetX).coerceIn(-1.4f, 1.4f)
                        val y = (baseY * config.particleScale + measureOffsetY).coerceIn(-1.4f, 1.4f)
                        val fadeWindow = (config.particleFadeInOutTime / life).coerceIn(0f, .5f)
                        val fadeIn = if (fadeWindow <= 0f) 1f else (phase / fadeWindow).coerceIn(0f, 1f)
                        val fadeOut = if (fadeWindow <= 0f) 1f else ((1f - phase) / fadeWindow).coerceIn(0f, 1f)
                        val alphaFactor = config.particleOpacity * (.3f + energy * .7f) * fadeIn * fadeOut
                        fun mix(shift: Int) = channel(config.particleColor, shift) +
                            (channel(config.particleColorTo, shift) - channel(config.particleColor, shift)) * phase
                        val red = mix(16); val green = mix(8); val blue = mix(0)
                        val alpha = mix(24) * alphaFactor
                        val startSize = if (config.particleFadeInSize > 0f) config.particleFadeInSize else config.particleSize
                        val endSize = (config.particleEndSize + (randomB * 2f - 1f) * config.particleEndSizeRandom).coerceIn(0f, 64f)
                        val size = (startSize + (endSize - startSize) * phase) * config.particleScale / 160f
                        val halfX = size * config.particleSizeAspectRatio
                        val halfY = size
                        val drawAngle = if (config.particleVelocityAngle) Math.toDegrees(direction).toFloat() else config.particleRotation
                        fun drawParticle(px: Float, py: Float, alphaMul: Float = 1f, sizeMul: Float = 1f) {
                            val hx = halfX * sizeMul; val hy = halfY * sizeMul
                            quad(px-hx,py-hy,px+hx,py+hy,red,green,blue,alpha*alphaMul,config,drawAngle)
                        }
                        drawParticle(x, y)
                        if (config.particleTrailLength > 0f) {
                            val tail = config.particleTrailLength * .003f
                            drawParticle(x-directionX*tail,y-directionY*tail,.42f,.78f)
                            drawParticle(x-directionX*tail*2f,y-directionY*tail*2f,.18f,.58f)
                        }
                        if (config.particleMirrorX) {
                            drawParticle(-x, y)
                            if (config.particleTrailLength > 0f) {
                                val tail = config.particleTrailLength * .003f
                                drawParticle(-x+directionX*tail,y-directionY*tail,.42f,.78f)
                                drawParticle(-x+directionX*tail*2f,y-directionY*tail*2f,.18f,.58f)
                            }
                        }
                        if (config.particleMirrorY) {
                            drawParticle(x, -y)
                            if (config.particleTrailLength > 0f) {
                                val tail = config.particleTrailLength * .003f
                                drawParticle(x-directionX*tail,-y+directionY*tail,.42f,.78f)
                                drawParticle(x-directionX*tail*2f,-y+directionY*tail*2f,.18f,.58f)
                            }
                        }
                        if (config.particleMirrorX && config.particleMirrorY) {
                            drawParticle(-x, -y)
                            if (config.particleTrailLength > 0f) {
                                val tail = config.particleTrailLength * .003f
                                drawParticle(-x+directionX*tail,-y+directionY*tail,.42f,.78f)
                                drawParticle(-x+directionX*tail*2f,-y+directionY*tail*2f,.18f,.58f)
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
            val rgbActive = rgb != null && (!rgb.rgbSplitOnBeat || bands.average().toFloat() > 0.15f)
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