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
        private val program:Int;private val p:Int;private val col:Int;private val buf:FloatBuffer
        init {
            val v=IntArray(2);check(EGL14.eglInitialize(display,v,0,v,1))
            val attrs=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE)
            val cs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1);check(EGL14.eglChooseConfig(display,attrs,0,cs,0,1,n,0)&&n[0]>0)
            context=EGL14.eglCreateContext(display,cs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            surface=EGL14.eglCreateWindowSurface(display,cs[0],input,intArrayOf(EGL14.EGL_NONE),0)
            check(EGL14.eglMakeCurrent(display,surface,surface,context))
            val vs="attribute vec2 p;attribute vec4 c;varying vec4 v;void main(){gl_Position=vec4(p,0.,1.);v=c;}"
            val fs="precision mediump float;varying vec4 v;void main(){gl_FragColor=v;}"
            program=link(shader(GLES20.GL_VERTEX_SHADER,vs),shader(GLES20.GL_FRAGMENT_SHADER,fs));p=GLES20.glGetAttribLocation(program,"p");col=GLES20.glGetAttribLocation(program,"c")
            buf=ByteBuffer.allocateDirect(64*36*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            GLES20.glViewport(0,0,w,h);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
        fun draw(bands:FloatArray) {
            GLES20.glClearColor(.018f,.02f,.028f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);buf.clear()
            for(i in bands.indices){val l=-1f+2f*i/bands.size;val r=-1f+2f*(i+.78f)/bands.size;val top=-.88f+.025f+bands[i]*.82f
                val red=.2f+.72f*i/bands.size;val green=.82f-.4f*i/bands.size;val blue=.98f-.48f*i/bands.size
                buf.put(floatArrayOf(l,-.88f,red,green,blue,1f,r,-.88f,red,green,blue,1f,r,top,red,green,blue,1f,l,-.88f,red,green,blue,1f,r,top,red,green,blue,1f,l,top,red,green,blue,1f))}
            buf.position(0);GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(col)
            GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,24,buf);buf.position(2);GLES20.glVertexAttribPointer(col,4,GLES20.GL_FLOAT,false,24,buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,bands.size*6);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(col)
        }
        fun release(){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)}
        private fun shader(type:Int,src:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]==GLES20.GL_TRUE){GLES20.glGetShaderInfoLog(s)};return s}
        private fun link(v:Int,f:Int):Int{val x=GLES20.glCreateProgram();GLES20.glAttachShader(x,v);GLES20.glAttachShader(x,f);GLES20.glLinkProgram(x);val ok=IntArray(1);GLES20.glGetProgramiv(x,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]==GLES20.GL_TRUE){GLES20.glGetProgramInfoLog(x)};return x}
    }
}