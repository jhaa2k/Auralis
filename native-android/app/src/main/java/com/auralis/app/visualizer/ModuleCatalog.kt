package com.auralis.app.visualizer

enum class SettingKind { TEXT, NUMBER, SLIDER, CHECKBOX, COLOR, DROPDOWN, IMAGE_PICKER, FONT_PICKER, CODE, ACTION }

data class SettingSpec(val key: String, val label: String, val kind: SettingKind, val options: List<String> = emptyList(), val min: Float = 0f, val max: Float = 1f, val defaultValue: String = "")

data class SettingGroup(val title: String, val settings: List<SettingSpec>)
data class ModuleSpec(val id: String, val title: String, val groups: List<SettingGroup>)

object ModuleCatalog {
    val blendModes = "Alpha,PreMulAlpha,Screen,Add,AddAlpha,Multiply,Darken,Lighten,ColorDodge,ColorBurn,Overlay,SoftLight,HardLight,Difference,Exclusion,Hue,Saturation,Color,Luminosity".split(",")
    val anchors = "unset,start,center,end,top,bottom,topLeft,topCenter,topRight,bottomLeft,bottomCenter,bottomRight,centerLeft,centerRight,justify,fill,stretch".split(",")
    val shapes = "None,Circle,SidedPolygon,Line,HorizontalLine,VerticalLine,Rectangle,Square,Triangle,Star,Heart,WaveformPath,CustomPath,Oval,Capsule,Pentagon,Hexagon".split(",")
    val measures = "Nothing,Beat,TotalTime,TotalTimeBackward,TotalTimeWhenPlaying,TotalTimeAndBeat,TrackPosition,BeatRandomShake,BeatCamShakeRotMore,BeatCamShakeRotLess,ConstantShakeRotMore,ConstantShakeRotLess,BeatTriggerAnim,BeatCamShakeOnly,BeatPulseScale,BeatFlashColor,BeatGlowIntensity,BeatHorizontalStretch,BeatVerticalBounce,ConstantSlowRotate,ConstantOrbitMove,ConstantLinearScroll,TrackProgressPercentage,TrackRemainingTime,TrackIntroOutroTrigger,VolumeLevelResponsive,PitchFrequencyResponsive".split(",")
    val fullBlend = blendModes
    private fun defaultRange(key: String): Pair<Float, Float> = when (key) {
        "positionX", "positionY", "alignmentX", "alignmentY", "pivotX", "pivotY" -> 0f to 1f
        "scaleX", "scaleY" -> 0.1f to 3f
        "particleScale" -> 0.1f to 4f
        "alpha", "opacity", "opacityStrength", "threshold", "softness", "colorBlendOffset",
        "barSpacing" -> 0f to 0.45f
        "blurAmount", "splitAmount", "relativeMotion" -> 0f to 1f
        "barCount" -> 16f to 128f
        "barWidth" -> 0.05f to 1f
        "heightScale", "maxHeightScale" -> 0f to 4f
        "minHeightScale" -> 0f to 1f
        "rotation" -> -180f to 180f
        "angle", "vectorAngle", "vectorAngleZ" -> 0f to 360f
        "fixedHeight", "segment1FixedHeight", "segment2FixedHeight", "segment3FixedHeight" -> 0f to 4f
        "segment1HeightMultiplier", "segment2HeightMultiplier", "segment3HeightMultiplier" -> 0f to 5f
        "reactionDelay", "reactionAccumulatedDelay" -> 0f to 1000f
        "countLimit" -> 0f to 5000f
        "perspectiveDepth" -> 1f to 2000f
        "perspectiveFov" -> 1f to 180f
        "trailLength" -> 0f to 50f
        "fadeInOut", "fadeInOutTime" -> 0f to 10f
        "sizeAspectRatio" -> 0.1f to 5f
        "startSize", "fadeInSize", "endSize", "endSizeRandom" -> 0f to 64f
        "endSizeZFactor" -> 0f to 10f
        "nearCameraFade" -> 0f to 1000f
        "spawnTime" -> 0f to 10f
        "amountX", "amountY" -> 0f to 10f
        "speed", "speedRandom" -> 0f to 10f
        "lifetime" -> 0.1f to 120f
        "sideSineFreq", "sideSineFreqRandom" -> 0f to 50f
        "sideSineMag", "sideSineMagRandom" -> 0f to 10f
        "volume" -> 0f to 2f
        "preamp" -> 0f to 10f
        "typewriterSpeed", "animationSpeed" -> 0.1f to 20f
        "fontSize" -> 6f to 300f
        "letterSpacing" -> -2f to 10f
        "lineHeight" -> 0.5f to 3f
        "shadowX", "shadowY" -> -100f to 100f
        "shadowBlur" -> 0f to 100f
        "outlineWidth" -> 0f to 20f
        "brightness" -> -1f to 1f
        "contrast", "saturation" -> 0f to 3f
        "blurRadius" -> 0f to 100f
        "blurMultiplier" -> 0f to 10f
        "glitchDuration" -> 0f to 5000f
        "redOffset", "greenOffset", "blueOffset" -> -100f to 100f
        "offset" -> -1f to 1f
        "bpmSync" -> 1f to 300f
        "audioOffset" -> -5000f to 5000f
        "beatOffset" -> -10f to 10f
        "gravityX", "gravityY" -> -10f to 10f
        "resolutionWidth", "resolutionHeight" -> 1f to 8192f
        "imageIndex", "sourceCompositionIndex", "audioProviderIndex" -> 0f to 999f
        else -> 0f to 1f
    }
    private fun defaultFor(key: String, kind: SettingKind, min: Float, max: Float, options: List<String>): String =
        when (key) {
            "visible", "keepAspect", "metadataVariables", "uniformScale", "clearCanvas" -> "true"
            "positionX", "positionY", "alignmentX", "alignmentY", "pivotX", "pivotY" -> "0.5"
            "scaleX", "scaleY", "particleScale", "opacity", "opacityStrength", "alpha", "contrast", "saturation", "lineHeight" -> "1"
            "rotation", "splitAmount", "brightness", "colorBlendOffset", "gravityX", "gravityY" -> "0"
            "barCount" -> "64"
            "barWidth" -> "0.72"
            "barSpacing" -> "0.18"
            "heightScale", "maxHeightScale", "segment1HeightMultiplier", "segment2HeightMultiplier", "segment3HeightMultiplier" -> "1"
            "minHeightScale", "segment1FixedHeight", "segment2FixedHeight", "segment3FixedHeight" -> "0"
            "softness" -> "0.35"
            "countLimit" -> "120"
            "speed", "volume", "preamp", "animationSpeed", "typewriterSpeed" -> "1"
            "speedRandom", "sideSineMag", "sideSineMagRandom", "trailLength", "fadeInSize" -> "0"
            "startSize" -> "3"
            "endSize" -> "4"
            "endSizeRandom" -> "10"
            "amountX", "amountY" -> "0.8"
            "lifetime" -> "8"
            "sideSineFreq" -> "5"
            "sideSineFreqRandom" -> "0"
            "fadeInOut" -> "0.1"
            "vectorAngle" -> "270"
            "vectorAngleZ" -> "0"
            "sizeAspectRatio" -> "1"
            "fontSize" -> "30"
            "fontWeight" -> "400"
            "letterSpacing", "shadowX", "shadowY", "outlineWidth" -> "0"
            "shadowBlur" -> "0"
            "blurRadius" -> "2"
            "blurMultiplier" -> "4.1"
            "blurAmount" -> "0.2"
            "lowerHz" -> "20"
            "higherHz" -> "18000"
            "bpmSync" -> "120"
            "audioOffset", "beatOffset" -> "0"
            else -> when (kind) {
                SettingKind.CHECKBOX -> "false"
                SettingKind.COLOR -> "#FFFFFFFF"
                SettingKind.DROPDOWN -> options.firstOrNull().orEmpty()
                SettingKind.SLIDER, SettingKind.NUMBER -> min.toString()
                else -> ""
            }
        }

    private fun s(
        key: String,
        label: String,
        kind: SettingKind = SettingKind.SLIDER,
        min: Float = defaultRange(key).first,
        max: Float = defaultRange(key).second,
        options: List<String> = emptyList(),
        default: String = ""
    ): SettingSpec {
        val resolvedDefault = default.ifBlank { defaultFor(key, kind, min, max, options) }
        return SettingSpec(key, label, kind, options, min, max, resolvedDefault)
    }
    private fun general() = SettingGroup("General", listOf(
        s("tag","Tag",SettingKind.TEXT), s("visible","Visible",SettingKind.CHECKBOX,default="true"),
        s("blendMode","Blend Mode",SettingKind.DROPDOWN,options=blendModes,default="Alpha"),
        s("positionX","Position X"),s("positionY","Position Y"),
        s("anchorX","Anchor X",SettingKind.DROPDOWN,options=anchors,default="center"),s("anchorY","Anchor Y",SettingKind.DROPDOWN,options=anchors,default="center"),
        s("alignmentX","Alignment Position X"),s("alignmentY","Alignment Position Y"),
        s("uniformScale","Scale Is Uniform",SettingKind.CHECKBOX,default="true"),s("scaleX","Scale X"),s("scaleY","Scale Y"),s("rotation","Rotation",min=-180f,max=180f),s("pivotX","Pivot X"),s("pivotY","Pivot Y"),
        s("measurePos","Measure Pos",SettingKind.DROPDOWN,options=measures,default="Nothing"),s("measureScale","Measure Scale",SettingKind.DROPDOWN,options=measures,default="Nothing"),s("measureRot","Measure Rot",SettingKind.DROPDOWN,options=measures,default="Nothing")
    ))
    private fun group(title:String,vararg fields:SettingSpec)=SettingGroup(title,fields.toList())
    val modules: List<ModuleSpec> = listOf(
        ModuleSpec("COMPOSITION","COMPOSITION",listOf(general(),group("Render Settings",s("resolutionWidth","Resolution Width",SettingKind.NUMBER,1f,8192f,default="1920"),s("resolutionHeight","Resolution Height",SettingKind.NUMBER,1f,8192f,default="1080"),s("fpsLimit","FPS Limit",SettingKind.DROPDOWN,options=listOf("30","60","120"),default="60"),s("backgroundColor","Background Color",SettingKind.COLOR,default="#FF000000"),s("alpha","Alpha / Transparency"),s("clearCanvas","Clear Canvas on Frame",SettingKind.CHECKBOX,default="true")))),
        ModuleSpec("IMAGE","IMAGE",listOf(general(),group("Image Source",s("pickImage","Pick Image",SettingKind.IMAGE_PICKER),s("keepAspect","Keep Aspect Ratio",SettingKind.CHECKBOX,default="true"),s("cropToFit","Crop to Fit",SettingKind.CHECKBOX),s("shape","Shape",SettingKind.DROPDOWN,options=shapes),s("imageIndex","Image Index",SettingKind.NUMBER,0f,999f)),group("Color Adjustments",s("tint","Tint",SettingKind.COLOR,default="#FFFFFFFF"),s("brightness","Brightness"),s("contrast","Contrast"),s("saturation","Saturation"),s("hueShift","Hue Shift",min=-180f,max=180f)),group("Color Key (Chroma Key)",s("enableColorKey","Enable Color Key",SettingKind.CHECKBOX),s("keyColor","Key Color",SettingKind.COLOR,default="#FF00FF00"),s("threshold","Threshold / Tolerance"),s("softness","Softness")),group("Blur",s("blurRadius","Blur Radius",max=100f),s("blurType","Blur Type",SettingKind.DROPDOWN,options=listOf("Gaussian","Box","Motion"))),group("Generated Album Art",s("autoFetch","Enable Auto-Fetch",SettingKind.CHECKBOX),s("fallbackImage","Fallback Image",SettingKind.IMAGE_PICKER)),group("Masking",s("maskImage","Mask Image",SettingKind.IMAGE_PICKER),s("maskMode","Mask Mode",SettingKind.DROPDOWN,options=listOf("Alpha","Luminance","Inverted Alpha"))))),
        ModuleSpec("TEXT","TEXT",listOf(general(),group("Content",s("textInput","Text Input",SettingKind.TEXT,default="{ArtistOrTitle}"),s("metadataVariables","Use Metadata Variables",SettingKind.CHECKBOX,default="true"),s("typewriterSpeed","Typewriter Speed")),group("Typography",s("fontFamily","Font Family",SettingKind.FONT_PICKER),s("fontSize","Font Size",max=300f,default="30"),s("fontWeight","Font Weight",SettingKind.DROPDOWN,options=(100..900 step 100).map{it.toString()},default="400"),s("style","Style",SettingKind.DROPDOWN,options=listOf("Normal","Italic","Bold","Bold-Italic")),s("letterSpacing","Letter Spacing",min=-2f,max=10f),s("lineHeight","Line Height",max=3f),s("alignment","Alignment",SettingKind.DROPDOWN,options=listOf("Left","Center","Right","Justify"))),group("Appearance",s("textColor","Text Color",SettingKind.COLOR,default="#FFFFFFFF"),s("shadowColor","Shadow Color",SettingKind.COLOR,default="#FF000000"),s("shadowX","Shadow X"),s("shadowY","Shadow Y"),s("shadowBlur","Shadow Blur",max=100f),s("outlineColor","Outline/Stroke Color",SettingKind.COLOR,default="#FF000000"),s("outlineWidth","Outline Width",max=20f)),group("Text Animation",s("animationType","Animation Type",SettingKind.DROPDOWN,options=listOf("None","Fade","Slide Up","Slide Down","Zoom","Typewriter")),s("animationSpeed","Animation Speed")))),
        ModuleSpec("BARS","BARS / SEGMENTS",
            listOf(
                general(),
                group("Bars Setup",
                    s("flipInput","Flip Input",SettingKind.CHECKBOX),
                    s("heightScale","Height Scale",max=2f), s("minHeightScale","Min Height Scale"),
                    s("maxHeightScale","Max Height Scale"), s("shapePath","Shape Path",SettingKind.DROPDOWN,options=shapes),
                    s("colorBlendOffset","Color Blend Offset"), s("barCount","Bar Count",max=128f,default="64"),
                    s("barSpacing","Bar Spacing"), s("barWidth","Bar Width")
                )
            ) + (1..3).map { n ->
                group("Segment $n",
                    s("segment${n}Enabled","Enable Segment",SettingKind.CHECKBOX,default=if(n==1)"true" else "false"),
                    s("segment${n}Shape","Shape Path",SettingKind.DROPDOWN,options=shapes),
                    s("segment${n}ColorFrom","Color From",SettingKind.COLOR,default="#FFFFFFFF"),
                    s("segment${n}ColorTo","Color To",SettingKind.COLOR,default="#FF00FFFF"),
                    s("segment${n}FixedHeight","Fixed Height"), s("segment${n}HeightMultiplier","Bar Height Multiplier"),
                    s("segment${n}Mirror","Mirror",SettingKind.CHECKBOX), s("segment${n}FlipOther","Flip Every Other",SettingKind.CHECKBOX)
                )
            } + listOf(
                group("Behavior",s("softness","Softness"),s("reactionDelay","Reaction Delay"),
                    s("reactionAccumulatedDelay","Reaction Accumulated Delay"),
                    s("audioProviderIndex","Audio Provider Index",SettingKind.NUMBER,0f,32f),
                    s("audioChannel","Audio Channel",SettingKind.DROPDOWN,options=listOf("Mono","Left","Right","Stereo")))
            )
        ),
        ModuleSpec("PARTICLES","PARTICLES",listOf(general(),group("Overall Settings",s("color","Color",SettingKind.COLOR,default="#FFFFFFFF"),s("countLimit","Count Limit",max=5000f),s("mirrorX","Mirror X",SettingKind.CHECKBOX),s("mirrorY","Mirror Y",SettingKind.CHECKBOX),s("perspectiveDepth","Perspective Depth"),s("perspectiveFov","Perspective Fov",max=180f),s("spawnArea","Spawn Area",SettingKind.DROPDOWN,options=listOf("Rectangle","Circle","Line","Point")),s("vectorRelative","Vector Relative To Center",SettingKind.CHECKBOX),s("randomVector","Use Random Vector Instead",SettingKind.CHECKBOX),s("vectorAngle","Vector Angle",max=360f),s("vectorAngleZ","Vector Angle Z",max=360f),s("forceField","Force Field",SettingKind.DROPDOWN,options=listOf("None","Gravity","Wind","Vortex","Attractor","Repeller"))),group("Appearance",s("customImage","Custom Image",SettingKind.IMAGE_PICKER),s("particleScale","Particle Scale"),s("trailLength","Trail Length"),s("colorFrom","Color From",SettingKind.COLOR,default="#FFFFFFFF"),s("colorTo","Color To",SettingKind.COLOR,default="#FFFFFFFF"),s("fadeInOut","Fade In And Out Time"),s("velocityAngle","Velocity Angle",SettingKind.CHECKBOX),s("sizeAspectRatio","Size Aspect Ratio"),s("startSize","Start Size"),s("fadeInSize","Fade In Size"),s("endSize","End Size"),s("endSizeRandom","End Size Random"),s("endSizeZFactor","End Size ZFactor"),s("nearCameraFade","Near Camera Fade Out Distance")),group("Behaviour",s("spawnTime","Spawn Time"),s("measureOverallSpeed","Measure Overall Speed",SettingKind.DROPDOWN,options=measures),s("amountX","A X Amount"),s("amountY","B Y Amount"),s("speed","Speed"),s("speedRandom","Speed Random"),s("lifetime","Lifetime",max=120f),s("gravityX","Gravity X",min=-10f,max=10f),s("gravityY","Gravity Y",min=-10f,max=10f)),group("Wave Behaviour",s("sideSineFreq","Side Sine Wave Freq"),s("sideSineFreqRandom","Side Sine Wave Freq Random"),s("sideSineMag","Side Sine Wave Mag"),s("sideSineMagRandom","Side Sine Wave Mag Random")))),
        ModuleSpec("AUDIOPROVIDER","AUDIOPROVIDER",listOf(group("General",s("visible","Visible",SettingKind.CHECKBOX,default="true"),s("audioSource","Audio Source",SettingKind.DROPDOWN,options=listOf("Player Output"),default="Player Output"),s("volume","Volume"),s("balance","Balance",min=-1f,max=1f),s("preamp","Preamp",max=10f),s("measureSource","Measure Source",SettingKind.DROPDOWN,options=listOf("Bass","Mid","Treble","Volume","Peak"))),group("Sync & Offset",s("audioOffset","Audio Offset (ms)",min=-5000f,max=5000f),s("bpmSync","BPM Sync",SettingKind.NUMBER,1f,300f),s("beatOffset","Beat Offset",min=-10f,max=10f)))),
        ModuleSpec("BLUREFFECT","BLUR EFFECT",listOf(general(),group("Appearance",s("blendModeContent","Blend Mode Content",SettingKind.DROPDOWN,options=fullBlend),s("color","Color",SettingKind.COLOR,default="#FFFFFFFF"),s("sourceCompositionIndex","Source Composition Index",SettingKind.NUMBER,0f,999f),s("maskImage","Mask Image",SettingKind.IMAGE_PICKER),s("showSharp","Show Unblurred Content",SettingKind.CHECKBOX),s("showSharpUnder","Show Unblurred Content Under",SettingKind.CHECKBOX)),group("Blur Settings",s("blurRadius","Blur Radius",max=100f),s("blurMultiplier","Blur Multiplier",max=10f),s("layer1ScaleX","1Layer Scale X"),s("layer1ScaleY","1Layer Scale Y"),s("layer2ScaleX","2Layer Scale X"),s("layer2ScaleY","2Layer Scale Y"),s("layer3ScaleX","3Layer Scale X"),s("layer3ScaleY","3Layer Scale Y"),s("layer1Color","1Layer Color",SettingKind.COLOR,default="#FFFFFFFF"),s("layer2Color","2Layer Color",SettingKind.COLOR,default="#FFFFFFFF"),s("layer3Color","3Layer Color",SettingKind.COLOR,default="#FFFFFFFF")))),
        ModuleSpec("RGBSPLITEFFECT","RGB SPLIT EFFECT",listOf(general(),group("Effect Settings",s("splitAmount","Split Amount"),s("angle","Angle / Direction",max=360f),s("glitchOnBeat","Enable Glitch on Beat",SettingKind.CHECKBOX),s("glitchDuration","Glitch Duration"),s("redOffset","Red Channel Offset"),s("greenOffset","Green Channel Offset"),s("blueOffset","Blue Channel Offset")))),
        ModuleSpec("MOTIONBLUREFFECT","MOTION BLUR EFFECT",listOf(general(),group("Appearance",s("blendModeContent","Blend Mode Content",SettingKind.DROPDOWN,options=fullBlend),s("color","Color",SettingKind.COLOR,default="#FFFFFFFF"),s("targetImage","Target Image",SettingKind.IMAGE_PICKER),s("showSharp","Show Unblurred Content",SettingKind.CHECKBOX),s("showSharpUnder","Show Unblurred Content Under",SettingKind.CHECKBOX)),group("Motion Blur",s("blurAmount","Blur Amount Multiplier"),s("motionSource","Motion Source",SettingKind.DROPDOWN,options=listOf("EffectTransform","TargetImage","AudioVolume")),s("relativeMotion","Relative Motion Mode",SettingKind.CHECKBOX)))),
        ModuleSpec("MIRROREREFFECT","MIRROR EFFECT",listOf(general(),group("Appearance",s("targetImage","Target Image",SettingKind.IMAGE_PICKER),s("mirrorMode","Mirror Mode",SettingKind.DROPDOWN,options=listOf("Horizontal","Vertical","Both","Kaleidoscope")),s("flipMirror","Flip Mirror",SettingKind.CHECKBOX),s("color","Color",SettingKind.COLOR,default="#FFFFFFFF"),s("offset","Offset")))),
        ModuleSpec("CUSTOMSHADER","CUSTOM SHADER",listOf(general(),group("Appearance",s("targetImage","Target Image",SettingKind.IMAGE_PICKER)),group("Shader Code",s("vertexShader","Shader Vertex",SettingKind.CODE,default="attribute vec2 aPosition; attribute vec4 aColor; varying vec4 vColor; uniform float uPointSize; uniform vec2 uOffset; void main(){ gl_Position=vec4(aPosition+uOffset,0.0,1.0); gl_PointSize=uPointSize; vColor=aColor; }"),s("fragmentShader","Fragment Shader",SettingKind.CODE,default="precision mediump float; varying vec4 vColor; uniform float uRoundPoint; uniform vec4 uTint; void main(){ if(uRoundPoint>0.5 && distance(gl_PointCoord,vec2(0.5,0.5))>0.5) discard; gl_FragColor=vec4(vColor.rgb*uTint.rgb,vColor.a*uTint.a); }"),s("compileApply","Compile / Apply",SettingKind.ACTION)),group("Variables",s("newVariable","New Variable",SettingKind.DROPDOWN,options=listOf("None","Float","Vector2","Vector3","Color")),s("addVariable","Add Variable",SettingKind.ACTION),s("variableMappings","Variable Mappings",SettingKind.TEXT))))
    )
}
