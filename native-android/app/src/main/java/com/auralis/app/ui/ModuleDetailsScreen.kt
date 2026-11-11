package com.auralis.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.app.ui.theme.*
import java.util.Locale

private enum class SettingKind { TEXT, CHECK, SLIDER, NUMBER, DROPDOWN, COLOR, IMAGE, CODE }
private data class SettingSpec(val name: String, val kind: SettingKind, val defaultValue: String = "", val options: List<String> = emptyList())
private data class AccordionSpec(val title: String, val settings: List<SettingSpec>)
private data class ModuleSpec(val title: String, val groups: List<AccordionSpec>)

private val blendOptions = listOf("Alpha","PreMulAlpha","Screen","Add","AddAlpha","Multiply","Darken","Lighten","ColorDodge","ColorBurn","Overlay","SoftLight","HardLight","Difference","Exclusion","Hue","Saturation","Color","Luminosity")
private val anchorOptions = listOf("unset","start","center","end","top","bottom","topLeft","topCenter","topRight","bottomLeft","bottomCenter","bottomRight","centerLeft","centerRight","justify","fill","stretch")
private val measureOptions = listOf("Nothing","Beat","TotalTime","TotalTimeBackward","TotalTimeWhenPlaying","TotalTimeAndBeat","TrackPosition","BeatRandomShake","BeatCamShakeRotMore","BeatCamShakeRotLess","ConstantShakeRotMore","ConstantShakeRotLess","BeatTriggerAnim","BeatCamShakeOnly","BeatPulseScale","BeatFlashColor","BeatGlowIntensity","BeatHorizontalStretch","BeatVerticalBounce","ConstantSlowRotate","ConstantOrbitMove","ConstantLinearScroll","TrackProgressPercentage","TrackRemainingTime","TrackIntroOutroTrigger","VolumeLevelResponsive","PitchFrequencyResponsive")
private val shapeOptions = listOf("None","Circle","SidedPolygon","Line","HorizontalLine","VerticalLine","Rectangle","Square","Triangle","Star","Heart","WaveformPath","CustomPath","Oval","Capsule","Pentagon","Hexagon")
private fun f(n: String, k: SettingKind, d: String = "", o: List<String> = emptyList()) = SettingSpec(n,k,d,o)
private fun general() = AccordionSpec("General", listOf(
    f("Tag",SettingKind.TEXT), f("Visible",SettingKind.CHECK,"true"), f("Blend Mode",SettingKind.DROPDOWN,"Alpha",blendOptions),
    f("Position X",SettingKind.SLIDER,"0.5"), f("Position Y",SettingKind.SLIDER,"0.5"),
    f("Anchor X",SettingKind.DROPDOWN,"center",anchorOptions), f("Anchor Y",SettingKind.DROPDOWN,"center",anchorOptions),
    f("Measure Pos",SettingKind.DROPDOWN,"Nothing",measureOptions),
    f("Alignment Position X",SettingKind.SLIDER,"0.5"), f("Alignment Position Y",SettingKind.SLIDER,"0.5"),
    f("Scale Is Uniform",SettingKind.CHECK,"false"), f("Scale X",SettingKind.SLIDER,"1.0"), f("Scale Y",SettingKind.SLIDER,"1.0"),
    f("Measure Scale",SettingKind.DROPDOWN,"Nothing",measureOptions), f("Rotation",SettingKind.SLIDER,"0.0"),
    f("Measure Rot",SettingKind.DROPDOWN,"Nothing",measureOptions)
))
private fun segment(n: Int) = AccordionSpec("Segment $n", listOf(
    f("Shape Path",SettingKind.DROPDOWN,"Line",shapeOptions), f("Color From",SettingKind.COLOR,"#FFFFFFFF"),
    f("Color To",SettingKind.COLOR,"#FFFFFFFF"), f("Fixed Height",SettingKind.SLIDER,"0.0"),
    f("Bar Height Multiplier",SettingKind.SLIDER,"1.0"), f("Mirror",SettingKind.CHECK,"false"),
    f("Flip Every Other",SettingKind.CHECK,"false")
))
private val modules: Map<String,ModuleSpec> = listOf(
    ModuleSpec("COMPOSITION", listOf(general(), AccordionSpec("Appearance", listOf(
        f("Target Image",SettingKind.IMAGE), f("Custom Image",SettingKind.IMAGE), f("Blend Mode Content",SettingKind.DROPDOWN,"Alpha",blendOptions),
        f("Creation Index",SettingKind.NUMBER,"0"), f("Target Composition Index",SettingKind.NUMBER,"0"), f("Opacity",SettingKind.SLIDER,"1.0")
    )))),
    ModuleSpec("TEXT", listOf(general(), AccordionSpec("Appearance", listOf(
        f("Text",SettingKind.TEXT,"%title%  •  %artist%"), f("Color",SettingKind.COLOR,"#FFFFFFFF"),
        f("Font Size",SettingKind.SLIDER,"30"), f("Type Face",SettingKind.TEXT,"Default")
    )))),
    ModuleSpec("BARS", listOf(general(), AccordionSpec("Bars", listOf(
        f("Flip Input",SettingKind.CHECK,"false"), f("Height Scale",SettingKind.SLIDER,"10"), f("Min Height Scale",SettingKind.SLIDER,"0"),
        f("Max Height Scale",SettingKind.SLIDER,"1"), f("Shape Path",SettingKind.DROPDOWN,"HorizontalLine",shapeOptions),
        f("Color Blend Offset",SettingKind.SLIDER,"0"), f("Bars Count",SettingKind.NUMBER,"64"), f("Segments Count",SettingKind.NUMBER,"3"),
        f("Speed",SettingKind.SLIDER,"1"), f("Beat Amount",SettingKind.SLIDER,"0.5")
    ), segment(1).settings), segment(1), segment(2), segment(3), AccordionSpec("Behavior", listOf(
        f("Softness",SettingKind.SLIDER,"8"), f("Reaction Delay",SettingKind.SLIDER,"0"),
        f("Reaction Accumulated Delay",SettingKind.SLIDER,"0"), f("Audio Provider Index",SettingKind.NUMBER,"1")
    )))),
    ModuleSpec("PARTICLES", listOf(general(), AccordionSpec("Overall", listOf(
        f("Color",SettingKind.COLOR,"#FFFFFFFF"), f("Count Limit",SettingKind.SLIDER,"1500"), f("Mirror X",SettingKind.CHECK,"false"),
        f("Mirror Y",SettingKind.CHECK,"false"), f("Perspective Depth",SettingKind.SLIDER,"900"), f("Perspective Fov",SettingKind.SLIDER,"150"),
        f("Spawn Area",SettingKind.DROPDOWN,"Rectangle",listOf("Rectangle","Circle","Line")),
        f("Vector Relative To Center",SettingKind.CHECK,"false"), f("Use Random Vector Instead",SettingKind.CHECK,"false"),
        f("Vector Angle",SettingKind.SLIDER,"270"), f("Vector Angle Z",SettingKind.SLIDER,"1"),
        f("Force Field",SettingKind.DROPDOWN,"None",listOf("None","Gravity","Wind","Vortex"))
    )), AccordionSpec("Appearance", listOf(
        f("Custom Image",SettingKind.IMAGE), f("Particle Scale",SettingKind.SLIDER,"2"), f("Trail Length",SettingKind.SLIDER,"0"),
        f("Color From",SettingKind.COLOR,"#FFFFFFFF"), f("Color To",SettingKind.COLOR,"#7F808080"),
        f("Fade In And Out Time",SettingKind.SLIDER,"0.1"), f("Velocity Angle",SettingKind.CHECK,"true"),
        f("Size Aspect Ratio",SettingKind.SLIDER,"1"), f("Start Size",SettingKind.SLIDER,"0"),
        f("Fade In Size",SettingKind.SLIDER,"4"), f("End Size",SettingKind.SLIDER,"4"),
        f("End Size Random",SettingKind.SLIDER,"10"), f("End Size ZFactor",SettingKind.SLIDER,"0"),
        f("Near Camera Fade Out Distance",SettingKind.SLIDER,"40")
    )), AccordionSpec("Behaviour", listOf(
        f("Spawn Time",SettingKind.SLIDER,"0.020"), f("Measure Overall Speed",SettingKind.DROPDOWN,"Nothing",measureOptions),
        f("A X Amount",SettingKind.SLIDER,"1.5"), f("B Y Amount",SettingKind.SLIDER,"1.5"),
        f("Speed",SettingKind.SLIDER,"100"), f("Speed Random",SettingKind.SLIDER,"60"),
        f("Lifetime",SettingKind.SLIDER,"8"), f("Gravity X",SettingKind.SLIDER,"0"), f("Gravity Y",SettingKind.SLIDER,"0")
    )), AccordionSpec("Wave Behaviour", listOf(
        f("Side Sine Wave Freq",SettingKind.SLIDER,"5"), f("Side Sine Wave Freq Random",SettingKind.SLIDER,"0"),
        f("Side Sine Wave Mag",SettingKind.SLIDER,"0"), f("Side Sine Wave Mag Random",SettingKind.SLIDER,"4")
    )))),
    ModuleSpec("CUSTOMSHADER", listOf(general(), AccordionSpec("Appearance", listOf(f("Target Image",SettingKind.IMAGE))),
        AccordionSpec("Shader", listOf(f("Shader Vertex",SettingKind.CODE,"// Vertex shader\\nattribute vec4 aPosition;\\nvoid main(){ gl_Position=aPosition; }"),
            f("Fragment Shader",SettingKind.CODE,"// Fragment shader\\nprecision mediump float;\\nvoid main(){ gl_FragColor=vec4(1.0); }"))),
        AccordionSpec("Variables", listOf(f("New Variable",SettingKind.DROPDOWN,"None",listOf("None","Float","Vector2","Vector3","Color")))))),
    ModuleSpec("MOTIONBLUREFFECT", listOf(general(), AccordionSpec("Appearance", listOf(
        f("Blend Mode Content",SettingKind.DROPDOWN,"Add",blendOptions), f("Color",SettingKind.COLOR,"#FFFFFFFF"),
        f("Target Image",SettingKind.IMAGE), f("Show Unblurred Content",SettingKind.CHECK,"false"),
        f("Show Unblurred Content Under",SettingKind.CHECK,"false")
    )), AccordionSpec("Motion Blur", listOf(
        f("Blur Amount Multiplier",SettingKind.SLIDER,"1"), f("Motion Source",SettingKind.DROPDOWN,"EffectTransform",listOf("EffectTransform","TargetImage")),
        f("Relative Motion Mode",SettingKind.CHECK,"true")
    )))),
    ModuleSpec("MIRROREFFECT", listOf(general(), AccordionSpec("Appearance", listOf(
        f("Target Image",SettingKind.IMAGE), f("Mirror Mode",SettingKind.DROPDOWN,"Horizontal",listOf("Horizontal","Vertical","Both")),
        f("Flip Mirror",SettingKind.CHECK,"false"), f("Color",SettingKind.COLOR,"#FFFFFFFF")
    )))),
    ModuleSpec("BLUREFFECT", listOf(general(), AccordionSpec("Appearance", listOf(
        f("Blend Mode Content",SettingKind.DROPDOWN,"PreMulAlpha",blendOptions), f("Color",SettingKind.COLOR,"#FFFF0017"),
        f("Source Composition Index",SettingKind.NUMBER,"2"), f("Mask Image",SettingKind.IMAGE),
        f("Show Unblurred Content",SettingKind.CHECK,"true"), f("Show Unblurred Content Under",SettingKind.CHECK,"true")
    )), AccordionSpec("Blur", listOf(
        f("Blur Radius",SettingKind.SLIDER,"2"), f("Blur Multiplier",SettingKind.SLIDER,"4.1"),
        f("1Layer Scale X",SettingKind.SLIDER,"1"), f("1Layer Scale Y",SettingKind.SLIDER,"1"),
        f("2Layer Scale X",SettingKind.SLIDER,"0"), f("2Layer Scale Y",SettingKind.SLIDER,"0"),
        f("3Layer Scale X",SettingKind.SLIDER,"0"), f("3Layer Scale Y",SettingKind.SLIDER,"0"),
        f("1Layer Color",SettingKind.COLOR,"#FFFFFFFF"), f("2Layer Color",SettingKind.COLOR,"#FFFFFFFF"),
        f("3Layer Color",SettingKind.COLOR,"#FFFFFFFF")
    )))),
    ModuleSpec("RGBSPLITEFFECT", listOf(general(), AccordionSpec("Appearance", listOf(
        f("Blend Mode Content",SettingKind.DROPDOWN,"Screen",blendOptions), f("Color",SettingKind.COLOR,"#FFDDDDDD"),
        f("Target Image",SettingKind.IMAGE), f("Show Unblurred Content",SettingKind.CHECK,"false"),
        f("Show Unblurred Content Under",SettingKind.CHECK,"false"), f("Split Multiplier",SettingKind.SLIDER,"0"),
        f("Split Amount X",SettingKind.SLIDER,"0.5"), f("Split Amount Y",SettingKind.SLIDER,"0.5"),
        f("Split Color 0",SettingKind.COLOR,"#FFFFFFFF"), f("Split Color 1",SettingKind.COLOR,"#FFFFFFFF"),
        f("Split Color 2",SettingKind.COLOR,"#FFFFFFFF"), f("Trigger On Heavy Beat",SettingKind.CHECK,"true")
    )))),
    ModuleSpec("IMAGE", listOf(general(), AccordionSpec("Image", listOf(
        f("Pick Image",SettingKind.IMAGE), f("Keep Aspect Ratio",SettingKind.CHECK,"true"), f("Crop to Fit",SettingKind.CHECK,"false"),
        f("Shape",SettingKind.DROPDOWN,"None",shapeOptions)
    )), AccordionSpec("Color", listOf(f("Tint",SettingKind.COLOR,"#FFFFFFFF"),f("Brightness",SettingKind.SLIDER,"1"),f("Contrast",SettingKind.SLIDER,"1"))),
        AccordionSpec("Color Key", listOf(f("Threshold",SettingKind.SLIDER,"0.1"))),
        AccordionSpec("Blur", listOf(f("Radius",SettingKind.SLIDER,"0"))),
        AccordionSpec("Generated Album Art", listOf(f("Enable",SettingKind.CHECK,"false"))))),
    ModuleSpec("AUDIOPROVIDER", listOf(AccordionSpec("General", listOf(
        f("Sample Provider",SettingKind.DROPDOWN,"Spectrum",listOf("Spectrum","Waveform","FFT","PCM")),
        f("Sample Out Count",SettingKind.NUMBER,"64"), f("Mirror Samples",SettingKind.CHECK,"false"),
        f("Repeat Samples",SettingKind.NUMBER,"1"), f("Start And End Gap",SettingKind.NUMBER,"0"),
        f("Smooth",SettingKind.SLIDER,"0.8"), f("Pre Smooth",SettingKind.SLIDER,"1"),
        f("Filter Radius",SettingKind.NUMBER,"1"), f("Filter Strength",SettingKind.SLIDER,"1"),
        f("A Weight",SettingKind.SLIDER,"0.2"), f("Output Multiplier",SettingKind.SLIDER,"1"),
        f("Lower Hz",SettingKind.SLIDER,"20"), f("Higher Hz",SettingKind.SLIDER,"18000"),
        f("Hz Linear Factor",SettingKind.SLIDER,"0.06"), f("Frequency Shift",SettingKind.SLIDER,"0"),
        f("Beat Smooth",SettingKind.SLIDER,"0.4"), f("Beat Range Bar First",SettingKind.SLIDER,"0"),
        f("Beat Range Bar Last",SettingKind.SLIDER,"0.2"), f("Beat Range Value Lower",SettingKind.SLIDER,"0.7"),
        f("Beat Range Value Higher",SettingKind.SLIDER,"35"), f("FFT Size",SettingKind.DROPDOWN,"11",(8..14).map { it.toString() })
    ))))
).associateBy { it.title }

/** Full-screen details editor. Each layer owns its values through the stable layerId.key namespace. */
@Composable
internal fun ModuleDetailsScreen(
    module: String,
    layerId: Int,
    values: MutableMap<String,String>,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onPickImage: (String) -> Unit
) {
    val spec = modules[module] ?: modules["BARS"]!!
    var expanded by remember(module, layerId) { mutableStateOf(spec.groups.firstOrNull()?.title.orEmpty()) }
    var info by remember { mutableStateOf(false) }
    val prefix = "$layerId/"
    fun read(field: SettingSpec): String {
        val key = prefix + field.name
        if (!values.containsKey(key)) values[key] = field.defaultValue
        return values[key] ?: field.defaultValue
    }
    fun write(field: SettingSpec, value: String) { values[prefix + field.name] = value }
    Column(Modifier.fillMaxSize().background(StudioBlack).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = StudioWhite, fontSize = 28.sp, modifier = Modifier.padding(end=16.dp))
            androidx.compose.foundation.clickableNoRipple(onBack) { Text("‹", color=StudioWhite,fontSize=28.sp) }
            Column(Modifier.weight(1f)) {
                Text(spec.title, color=StudioWhite,fontSize=14.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp)
                Text("LAYER $layerId  /  PROPERTIES",color=StudioMuted,fontSize=8.sp)
            }
            TextButton(onClick=onReset) { Text("↺",color=StudioWhite,fontSize=20.sp) }
            TextButton(onClick={info=true}) { Text("ⓘ",color=StudioWhite,fontSize=18.sp) }
        }
        Divider(color=StudioLine)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top=10.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
            spec.groups.forEach { group ->
                Surface(color=StudioPanel, border=BorderStroke(1.dp,StudioLine), shape=RoundedCornerShape(2.dp), modifier=Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().height(44.dp).clickableNoRipple { expanded=if(expanded==group.title) "" else group.title }.padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
                            Text(group.title.uppercase(Locale.US),color=StudioWhite,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=0.8.sp)
                            Spacer(Modifier.weight(1f))
                            Text(if(expanded==group.title) "−" else "+",color=StudioAccent,fontSize=18.sp)
                        }
                        if(expanded==group.title) {
                            Divider(color=StudioLine)
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                group.settings.forEach { field ->
                                    val value=read(field)
                                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text(field.name,color=StudioMuted,fontSize=9.sp,fontWeight=FontWeight.Medium)
                                        when(field.kind) {
                                            SettingKind.CHECK -> Row(verticalAlignment=Alignment.CenterVertically) {
                                                Checkbox(checked=value.toBoolean(),onCheckedChange={write(field,it.toString())},colors=CheckboxDefaults.colors(checkedColor=StudioAccent,checkmarkColor=StudioBlack))
                                                Text(if(value.toBoolean()) "Enabled" else "Disabled",color=StudioWhite,fontSize=9.sp)
                                            }
                                            SettingKind.DROPDOWN -> {
                                                var open by remember(field.name) { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,StudioLine),shape=RoundedCornerShape(2.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=StudioWhite)) {
                                                        Text(value.ifBlank { field.options.firstOrNull().orEmpty() },fontSize=9.sp)
                                                        Spacer(Modifier.weight(1f)); Text("▾",color=StudioAccent)
                                                    }
                                                    DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                                                        field.options.forEach { option -> DropdownMenuItem(text={Text(option,fontSize=10.sp)},onClick={write(field,option);open=false}) }
                                                    }
                                                }
                                            }
                                            SettingKind.SLIDER -> {
                                                val numeric=value.toFloatOrNull() ?: 0f
                                                Row(verticalAlignment=Alignment.CenterVertically) {
                                                    Slider(value=numeric.coerceIn(0f,1f),onValueChange={write(field,String.format(Locale.US,"%.3f",it))},modifier=Modifier.weight(1f),colors=SliderDefaults.colors(thumbColor=StudioAccent,activeTrackColor=StudioAccent,inactiveTrackColor=StudioLine))
                                                    Text(value,color=StudioWhite,fontSize=8.sp,modifier=Modifier.width(54.dp))
                                                }
                                            }
                                            SettingKind.TEXT, SettingKind.NUMBER, SettingKind.COLOR, SettingKind.IMAGE, SettingKind.CODE -> {
                                                OutlinedTextField(value=value,onValueChange={write(field,it)},modifier=Modifier.fillMaxWidth(),singleLine=field.kind!=SettingKind.CODE, minLines=if(field.kind==SettingKind.CODE) 5 else 1,
                                                    label={Text(when(field.kind){SettingKind.COLOR->"Hex color (#RRGGBB or #AARRGGBB)";SettingKind.IMAGE->"Image URI / selected asset";SettingKind.NUMBER->"Numeric value";SettingKind.CODE->"GLSL source";else->"Value"},fontSize=8.sp)},
                                                    colors=OutlinedTextFieldDefaults.colors(focusedTextColor=StudioWhite,unfocusedTextColor=StudioWhite,focusedBorderColor=StudioAccent,unfocusedBorderColor=StudioLine,focusedLabelColor=StudioAccent,unfocusedLabelColor=StudioMuted))
                                                if(field.kind==SettingKind.IMAGE) TextButton(onClick={onPickImage(field.name)}) { Text("CHOOSE IMAGE",color=StudioAccent,fontSize=8.sp) }
                                                if(field.kind==SettingKind.COLOR) Text("Stored as HEX: $value",color=StudioMuted,fontSize=8.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(info) AlertDialog(onDismissRequest={info=false},confirmButton={TextButton(onClick={info=false}){Text("OK")}},title={Text(spec.title)},text={Text("Settings are stored per layer. A property affects the live preview only when the corresponding renderer consumes it; unconnected values remain editable and saved in this in-memory editor model.")})
}

/** Tiny click modifier wrapper to keep the details screen's header/accordion surfaces sharp. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.clickableNoRipple(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { content() }
}
