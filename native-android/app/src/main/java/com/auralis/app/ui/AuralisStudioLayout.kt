package com.auralis.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.auralis.app.ui.theme.*
import com.auralis.app.visualizer.AuralisCanvasView
import com.auralis.app.visualizer.Layer
import com.auralis.app.visualizer.LayerType
import com.auralis.app.visualizer.VisualizerProject

private enum class StudioPanelTab(val label: String) {
    MEDIA("Media"), LAYERS("Layers"), PRESETS("Presets"), AUDIO("Audio"), SCENE("Scene"), EXPORT("Export")
}

/**
 * New full-screen studio shell. This is a reusable UI route; the existing playback/export
 * controllers are passed in so the app keeps one Media3 session and one export pipeline.
 */
@Composable
fun AuralisStudioLayout(
    project: VisualizerProject,
    spectrum: FloatArray,
    trackTitle: String,
    currentTime: String,
    totalTime: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSettings: () -> Unit,
    onImportMedia: () -> Unit,
    onAddLayer: (LayerType) -> Unit,
    onSelectLayer: (String) -> Unit,
    onUpdateLayer: (Layer) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(StudioPanelTab.MEDIA) }
    var panelVisible by remember { mutableStateOf(true) }
    var seek by remember { mutableFloatStateOf(0f) }
    var selectedLayerId by remember(project.selectedLayerId) { mutableStateOf(project.selectedLayerId) }
    val selectedLayer = project.layers.firstOrNull { it.id == selectedLayerId }
    var showSettings by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(StudioBlack)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(StudioPanel).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AUDIOVIS PRO", color = StudioWhite, fontSize = 13.sp, letterSpacing = 1.8.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(7.dp).background(StudioAccent, RoundedCornerShape(50)))
            Spacer(Modifier.weight(1f))
            Text("↶", color = StudioWhite, modifier = Modifier.clickable(onClick = onUndo).padding(8.dp))
            Text("↷", color = StudioWhite, modifier = Modifier.clickable(onClick = onRedo).padding(8.dp))
            Text("▣", color = StudioWhite, modifier = Modifier.clickable { panelVisible = !panelVisible }.padding(8.dp))
            Text("⚙", color = StudioWhite, modifier = Modifier.clickable { showSettings = true; onSettings() }.padding(8.dp))
        }

        // Stage occupies 60% of the available editor workspace.
        Column(Modifier.weight(0.60f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                AndroidView(
                    factory = { context -> AuralisCanvasView(context) },
                    update = { it.submit(project, spectrum) },
                    modifier = Modifier.fillMaxSize()
                )
                Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text("PREVIEW", color = StudioWhite, fontSize = 9.sp)
                    Text("${project.width} × ${project.height}  ·  ${project.frameRate} FPS", color = StudioMuted, fontSize = 8.sp)
                }
            }
            Column(Modifier.fillMaxWidth().background(StudioPanel).padding(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trackTitle.ifBlank { "No track selected" }, color = StudioWhite, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("$currentTime / $totalTime", color = StudioMuted, fontSize = 10.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("⤨", color = StudioMuted, modifier = Modifier.clickable { }.padding(8.dp))
                    Text("|◀", color = StudioWhite, modifier = Modifier.clickable(onClick = onPrevious).padding(8.dp))
                    Text(if (isPlaying) "Ⅱ" else "▶", color = StudioAccent, modifier = Modifier.clickable(onClick = onPlayPause).padding(horizontal = 18.dp, vertical = 8.dp))
                    Text("▶|", color = StudioWhite, modifier = Modifier.clickable(onClick = onNext).padding(8.dp))
                    Text("↻", color = StudioMuted, modifier = Modifier.clickable { }.padding(8.dp))
                }
                Slider(value = seek, onValueChange = { seek = it }, onValueChangeFinished = { onSeekFraction(seek) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onAddLayer(LayerType.TEXT) }, modifier = Modifier.weight(1f)) { Text("Add Text", fontSize = 10.sp) }
                    OutlinedButton(onClick = { onAddLayer(LayerType.IMAGE) }, modifier = Modifier.weight(1f)) { Text("Add Image", fontSize = 10.sp) }
                    var addExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { addExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Add Layer ▾", fontSize = 10.sp) }
                        DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                            LayerType.values().forEach { type ->
                                DropdownMenuItem(text = { Text(type.name.replace('_', ' ')) }, onClick = { onAddLayer(type); addExpanded = false })
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = panelVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.weight(0.40f).fillMaxWidth()
        ) {
            Column(Modifier.fillMaxSize().background(StudioPanel)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(activeTab.label.uppercase(), color = StudioWhite, modifier = Modifier.weight(1f))
                    Text("▶", color = StudioAccent, modifier = Modifier.clickable(onClick = onPlayPause).padding(7.dp))
                    Text("×", color = StudioWhite, modifier = Modifier.clickable { panelVisible = false }.padding(7.dp))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    StudioPanelTab.values().forEach { tab ->
                        Text(tab.label, color = if (tab == activeTab) StudioAccent else StudioMuted,
                            modifier = Modifier.clickable { activeTab = tab }.padding(horizontal = 11.dp, vertical = 9.dp))
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    when (activeTab) {
                        StudioPanelTab.MEDIA -> {
                            Text("QUEUE", color = StudioMuted)
                            Text("Choose audio from device storage.", color = StudioWhite)
                            Button(onClick = onImportMedia) { Text("IMPORT AUDIO") }
                        }
                        StudioPanelTab.LAYERS -> {
                            project.layers.forEach { layer ->
                                Row(Modifier.fillMaxWidth().clickable { selectedLayerId = layer.id; onSelectLayer(layer.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(layer.name, color = if (layer.id == selectedLayerId) StudioAccent else StudioWhite, modifier = Modifier.weight(1f))
                                    Text(layer.type.name, color = StudioMuted, fontSize = 9.sp)
                                }
                            }
                            selectedLayer?.let { InspectorControls(it, onUpdateLayer) }
                        }
                        StudioPanelTab.PRESETS -> Text("Project presets will be listed here.", color = StudioMuted)
                        StudioPanelTab.AUDIO -> Text("FFT · Bass / Mid / Treble · Attack / Release", color = StudioMuted)
                        StudioPanelTab.SCENE -> Text("Background · Blend · Glow · Blur · Motion", color = StudioMuted)
                        StudioPanelTab.EXPORT -> {
                            Text("MP4 EXPORT", color = StudioWhite)
                            Text("${project.exportSettings.width} × ${project.exportSettings.height} · ${project.exportSettings.fps} FPS", color = StudioMuted)
                            Button(onClick = onExport) { Text("EXPORT VIDEO") }
                        }
                    }
                }
            }
        }

        NavigationBar(containerColor = StudioPanel) {
            StudioPanelTab.values().forEach { tab ->
                NavigationBarItem(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab; panelVisible = true },
                    icon = { Text(tab.label.take(1), color = if (activeTab == tab) StudioAccent else StudioMuted) },
                    label = { Text(tab.label, fontSize = 8.sp) }
                )
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Studio Settings") },
            text = { Text("Theme · Preview Quality · Frame Limit · FPS Overlay · Guides") },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("DONE") } }
        )
    }
}

@Composable
private fun InspectorControls(layer: Layer, onUpdate: (Layer) -> Unit) {
    var expanded by remember(layer.id) { mutableStateOf<String?>(null) }
    val sections = remember(layer.type) {
        buildList {
            add("Transform"); add("Rotate"); add("Audio"); add("Color"); add("Effects")
            add("Repeat"); add("Motion"); add("Animation")
            when (layer.type) {
                LayerType.BARS -> add("Bars")
                LayerType.TEXT -> add("Typography")
                LayerType.PARTICLES -> add("Particles")
                else -> Unit
            }
        }
    }
    sections.forEach { section ->
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(StudioBlack)) {
            Text(section.uppercase(), color = StudioWhite, modifier = Modifier.fillMaxWidth().clickable { expanded = if (expanded == section) null else section }.padding(11.dp))
            AnimatedVisibility(visible = expanded == section) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    when (section) {
                        "Transform" -> {
                            InspectorSlider("Position X", layer.transform.x) { onUpdate(layer.copy(transform = layer.transform.copy(x = it))) }
                            InspectorSlider("Position Y", layer.transform.y) { onUpdate(layer.copy(transform = layer.transform.copy(y = it))) }
                            InspectorSlider("Scale X", layer.transform.scaleX / 4f) { onUpdate(layer.copy(transform = layer.transform.copy(scaleX = it * 4f))) }
                            InspectorSlider("Scale Y", layer.transform.scaleY / 4f) { onUpdate(layer.copy(transform = layer.transform.copy(scaleY = it * 4f))) }
                            InspectorSlider("Opacity", layer.transform.opacity) { onUpdate(layer.copy(transform = layer.transform.copy(opacity = it))) }
                            var blend by remember(layer.id) { mutableStateOf(layer.transform.blendMode) }
                            DropdownMenuBox("Blend Mode", StudioBlendMode.values().map { it.name }, blend.name) { value ->
                                blend = StudioBlendMode.valueOf(value)
                                onUpdate(layer.copy(transform = layer.transform.copy(blendMode = blend)))
                            }
                        }
                        "Rotate" -> InspectorSlider("Rotation", (layer.transform.rotation + 180f) / 360f) { onUpdate(layer.copy(transform = layer.transform.copy(rotation = it * 360f - 180f))) }
                        "Audio" -> {
                            InspectorSlider("Sensitivity", layer.audio.sensitivity / 8f) { onUpdate(layer.copy(audio = layer.audio.copy(sensitivity = it * 8f))) }
                            InspectorSlider("Attack", layer.audio.attackMs / 2000f) { onUpdate(layer.copy(audio = layer.audio.copy(attackMs = (it * 2000).toInt()))) }
                            InspectorSlider("Release", layer.audio.releaseMs / 5000f) { onUpdate(layer.copy(audio = layer.audio.copy(releaseMs = (it * 5000).toInt()))) }
                        }
                        "Color" -> Text("Hex color: ${layer.colorHex} (color picker integration point)", color = StudioMuted)
                        "Effects" -> {
                            InspectorSlider("Glow", layer.effects.glow) { onUpdate(layer.copy(effects = layer.effects.copy(glow = it))) }
                            InspectorSlider("Blur", layer.effects.blur) { onUpdate(layer.copy(effects = layer.effects.copy(blur = it))) }
                        }
                        "Repeat" -> InspectorSlider("Repeat X", (layer.effects.repeatX - 1) / 15f) { onUpdate(layer.copy(effects = layer.effects.copy(repeatX = 1 + (it * 15).toInt()))) }
                        "Motion" -> InspectorSlider("Motion", layer.effects.motionAmount) { onUpdate(layer.copy(effects = layer.effects.copy(motionAmount = it))) }
                        "Animation" -> Text("Reactive curve: Linear / Smooth", color = StudioMuted)
                        "Bars" -> {
                            InspectorSlider("Thickness", layer.barThickness) { onUpdate(layer.copy(barThickness = it)) }
                            InspectorSlider("Count", layer.barCount / 256f) { onUpdate(layer.copy(barCount = (it * 256).toInt().coerceAtLeast(4))) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(layer.mirror, { onUpdate(layer.copy(mirror = it)) }); Text("Mirror", color = StudioWhite) }
                        }
                        "Typography" -> {
                            OutlinedTextField(layer.text, { onUpdate(layer.copy(text = it)) }, label = { Text("Text") })
                            InspectorSlider("Font size", layer.fontSizeSp / 120f) { onUpdate(layer.copy(fontSizeSp = it * 120f)) }
                            OutlinedTextField(layer.fontFamily, { onUpdate(layer.copy(fontFamily = it)) }, label = { Text("Font family") })
                        }
                        "Particles" -> {
                            InspectorSlider("Spawn rate", layer.particleSpawnRate / 1000f) { onUpdate(layer.copy(particleSpawnRate = it * 1000f)) }
                            InspectorSlider("Gravity", (layer.particleGravity + 100f) / 200f) { onUpdate(layer.copy(particleGravity = it * 200f - 100f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = StudioMuted, fontSize = 10.sp)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange)
    }
}

@Composable
private fun DropdownMenuBox(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text("$label: $selected  ▾", color = StudioWhite, modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(8.dp))
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); open = false }) }
    }
}

