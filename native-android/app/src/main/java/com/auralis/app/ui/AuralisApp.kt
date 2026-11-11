package com.auralis.app.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auralis.app.audio.AudioSessionBridge
import com.auralis.app.playback.AuralisPlaybackService
import com.auralis.app.visualizer.MeasureWhat
import com.auralis.app.visualizer.VisualizerCapture
import com.auralis.app.visualizer.VisualizerSettings
import com.auralis.app.visualizer.VisualizerSurface
import com.auralis.app.ui.theme.StudioAccent
import com.auralis.app.ui.theme.StudioBlack
import com.auralis.app.ui.theme.StudioLine
import com.auralis.app.ui.theme.StudioMuted
import com.auralis.app.ui.theme.StudioPanel
import com.auralis.app.ui.theme.StudioRaised
import com.auralis.app.ui.theme.StudioWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun AuralisApp() {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var selectedTrackUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTrackName by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var playerMessage by remember { mutableStateOf("CONNECTING TO AUDIO ENGINE") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val editorLayers = remember { mutableStateListOf(EditorLayer(1, "Composition", "Main Composition"), EditorLayer(2, "Bars", "Spectrum Bars")) }
    var selectedLayerId by remember { mutableIntStateOf(2) }
    var nextLayerId by remember { mutableIntStateOf(3) }
    var addLayerMenuExpanded by remember { mutableStateOf(false) }
    var fftLower by remember { mutableFloatStateOf(0.02f) }
    var fftUpper by remember { mutableFloatStateOf(0.92f) }
    var beatSensitivity by remember { mutableFloatStateOf(0.55f) }
    var mirrorEnabled by remember { mutableStateOf(false) }
    var particleCount by remember { mutableFloatStateOf(0.30f) }
    var particleSpeed by remember { mutableFloatStateOf(0.35f) }
    var particleSize by remember { mutableFloatStateOf(0.25f) }
    var particleSpread by remember { mutableFloatStateOf(0.40f) }

    // These editor values are UI state. They will be connected to the render engine in a later phase.
    var sensitivity by remember { mutableFloatStateOf(0.65f) }
    var barCount by remember { mutableFloatStateOf(0.55f) }
    var smoothing by remember { mutableFloatStateOf(0.35f) }
    var glow by remember { mutableFloatStateOf(0.25f) }
    var audioGain by remember { mutableFloatStateOf(0.70f) }
    var layerOpacity by remember { mutableFloatStateOf(1f) }
    val audioSessionId by AudioSessionBridge.sessionId.collectAsState()
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (!granted) playerMessage = "AUDIO VISUALIZER PERMISSION DENIED"
    }

    LaunchedEffect(audioSessionId) {
        if (audioSessionId > 0 && !hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, AuralisPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var active = true
        future.addListener(
            {
                if (active) {
                    runCatching { future.get() }
                        .onSuccess { mediaController = it }
                        .onFailure { playerMessage = "AUDIO ENGINE CONNECTION FAILED" }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            active = false
            MediaController.releaseFuture(future)
            mediaController = null
        }
    }

    DisposableEffect(mediaController) {
        val player = mediaController
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    playbackDuration = player.duration.coerceAtLeast(0L)
                    playerMessage = when (playbackState) {
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> if (player.isPlaying) "PLAYING" else "READY"
                        Player.STATE_ENDED -> "PLAYBACK COMPLETE"
                        else -> if (player.mediaItemCount == 0) "AUDIO ENGINE READY" else "READY"
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    playbackPosition = 0L
                    playbackDuration = player.duration.coerceAtLeast(0L)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playerMessage = "PLAYBACK ERROR: ${error.errorCodeName}"
                    isPlaying = false
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    LaunchedEffect(mediaController, selectedTrackUri, selectedTrackName) {
        val player = mediaController
        val uri = selectedTrackUri
        if (player != null && uri != null) {
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(selectedTrackName ?: "Unknown track").build())
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    LaunchedEffect(mediaController, isPlaying) {
        while (isPlaying && mediaController != null) {
            playbackPosition = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
            playbackDuration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedTrackName = queryDisplayName(context, uri)
            selectedTrackUri = uri
            playerMessage = "LOADING TRACK"
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val index = editorLayers.indexOfFirst { it.id == selectedLayerId }
            if (index >= 0) editorLayers[index] = editorLayers[index].copy(imageUri = uri.toString())
        }
    }

    val barsLayer = editorLayers.firstOrNull { it.type == "Bars" }
    val particlesLayer = editorLayers.firstOrNull { it.type == "Particles" }

    Scaffold(
        containerColor = StudioBlack,
        bottomBar = {
            NavigationBar(containerColor = StudioPanel, contentColor = StudioWhite) {
                val tabs = listOf("STUDIO", "LAYERS", "AUDIO", "EFFECTS", "SETTINGS")
                val icons = listOf(
                    Icons.Outlined.GraphicEq,
                    Icons.Outlined.Layers,
                    Icons.Outlined.AudioFile,
                    Icons.Outlined.GraphicEq,
                    Icons.Outlined.Settings
                )
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label, modifier = Modifier.size(18.dp)) },
                        label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .background(StudioBlack)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Upper 40%: fixed preview workspace.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.40f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AURALIS", color = StudioWhite, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("PREVIEW  /  40%", color = StudioMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            Brush.linearGradient(listOf(StudioRaised, StudioBlack, StudioPanel)),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiveVisualizerPreview(
                        audioSessionId = audioSessionId,
                        hasAudioPermission = hasAudioPermission,
                        settings = VisualizerSettings(
                            barCount = (12 + barCount * 116).toInt(),
                            sensitivity = 0.35f + sensitivity * 2.65f,
                            smoothing = smoothing * 0.9f,
                            glow = glow,
                            opacity = if (barsLayer?.visible != true) 0f else layerOpacity * barsLayer.opacity,
                            mirror = mirrorEnabled,
                            positionX = barsLayer?.x ?: 0.5f,
                            positionY = barsLayer?.y ?: 0.5f,
                            scale = barsLayer?.scale ?: 1f,
                            rotation = barsLayer?.rotation ?: 0f,
                            particlesEnabled = particlesLayer?.visible == true,
                            particleOpacity = particlesLayer?.opacity ?: 1f,
                            particlePositionX = particlesLayer?.x ?: 0.5f,
                            particlePositionY = particlesLayer?.y ?: 0.5f,
                            particleScale = particlesLayer?.scale ?: 1f,
                            particleRotation = particlesLayer?.rotation ?: 0f,
                            particlePositionMeasure = particlesLayer?.positionMeasure ?: MeasureWhat.NOTHING,
                            particleScaleMeasure = particlesLayer?.scaleMeasure ?: MeasureWhat.NOTHING,
                            particleRotationMeasure = particlesLayer?.rotationMeasure ?: MeasureWhat.NOTHING,
                            particleMeasureAmount = particlesLayer?.measureAmount ?: 0.5f,
                            particleCount = (20 + particleCount * 380).toInt(),
                            particleSpeed = 0.1f + particleSpeed * 3.9f,
                            particleSize = 1f + particleSize * 11f,
                            particleSpread = 0.1f + particleSpread * 1.9f,
                            positionMeasure = barsLayer?.positionMeasure ?: MeasureWhat.NOTHING,
                            scaleMeasure = barsLayer?.scaleMeasure ?: MeasureWhat.NOTHING,
                            rotationMeasure = barsLayer?.rotationMeasure ?: MeasureWhat.NOTHING,
                            measureAmount = barsLayer?.measureAmount ?: 0.5f,
                            trackPositionFraction = if (playbackDuration > 0L) (playbackPosition.toFloat() / playbackDuration.toFloat()).coerceIn(0f, 1f) else 0f,
                            beatSensitivity = beatSensitivity,
                            rgbSplitAmount = editorLayers.filter { it.type == "RGB Split" && it.visible }.maxOfOrNull { it.rgbSplitAmount } ?: 0f
                        ),
                        audioGain = 0.1f + audioGain * 3.9f,
                        lowFrequencyFraction = fftLower,
                        highFrequencyFraction = fftUpper,
                        modifier = Modifier.fillMaxSize()
                    )
                    editorLayers.filter { it.type == "Text" && it.visible && it.textContent.isNotBlank() }.forEach { textLayer ->
                        Text(
                            text = textLayer.textContent,
                            color = Color(textLayer.textColor).copy(alpha = textLayer.opacity),
                            fontSize = textLayer.fontSize.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    translationX = (textLayer.x - 0.5f) * size.width
                                    translationY = (textLayer.y - 0.5f) * size.height
                                    scaleX = textLayer.scale
                                    scaleY = textLayer.scale
                                    rotationZ = textLayer.rotation
                                }
                        )
                    }
                    editorLayers.filter { it.type == "Image" && it.visible && it.imageUri != null }.forEach { imageLayer ->
                        LayerImagePreview(
                            uri = Uri.parse(imageLayer.imageUri),
                            opacity = imageLayer.opacity,
                            x = imageLayer.x,
                            y = imageLayer.y,
                            scale = imageLayer.scale,
                            rotation = imageLayer.rotation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (selectedTrackName == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(30.dp))
                            Text("LIVE VISUALIZER", color = StudioWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("Import audio to begin", color = StudioMuted, fontSize = 9.sp)
                        }
                    } else if (!hasAudioPermission) {
                        Text("Allow audio permission to enable FFT capture", color = StudioMuted, fontSize = 9.sp)
                    }
                }
            }

            // Lower 60%: playback, editor categories and scrollable properties.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.60f)
                    .background(StudioPanel, RoundedCornerShape(3.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("CONTROLS & PROPERTIES  /  60%", color = StudioMuted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(7.dp))

                Surface(
                    color = StudioBlack,
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, StudioLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    selectedTrackName ?: "NO TRACK LOADED",
                                    color = StudioWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(playerMessage, color = StudioMuted, fontSize = 8.sp)
                            }
                            Text("${formatTime(playbackPosition)} / ${formatTime(playbackDuration)}", color = StudioMuted, fontSize = 9.sp)
                        }
                        Slider(
                            value = if (playbackDuration > 0L) playbackPosition.toFloat().coerceIn(0f, playbackDuration.toFloat()) else 0f,
                            onValueChange = { playbackPosition = it.toLong() },
                            onValueChangeFinished = { if (playbackDuration > 0L) mediaController?.seekTo(playbackPosition) },
                            valueRange = 0f..playbackDuration.coerceAtLeast(1L).toFloat(),
                            enabled = mediaController != null && playbackDuration > 0L
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = StudioBlack),
                                shape = RoundedCornerShape(3.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("  IMPORT AUDIO", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    mediaController?.let { player ->
                                        if (player.isPlaying) player.pause()
                                        else if (player.mediaItemCount > 0) player.play()
                                    }
                                },
                                enabled = (mediaController?.mediaItemCount ?: 0) > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioRaised,
                                    contentColor = StudioWhite,
                                    disabledContainerColor = StudioRaised,
                                    disabledContentColor = StudioMuted
                                ),
                                shape = RoundedCornerShape(3.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                                Text(if (isPlaying) " PAUSE" else " PLAY", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOf("STUDIO PROPERTIES", "LAYER EDITOR", "AUDIO PROVIDER", "EFFECTS", "APP SETTINGS")[selectedTab],
                        color = StudioWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text("EDIT PANEL", color = StudioMuted, fontSize = 8.sp)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            PropertySlider("Sensitivity", sensitivity, { sensitivity = it }, "LOW", "HIGH", "${(sensitivity * 100).toInt()}%", resetValue = 0.65f)
                            PropertySlider("Bar count", barCount, { barCount = it }, "FEWER", "MORE", "${(12 + barCount * 116).toInt()}", resetValue = 0.55f)
                            PropertySlider("Smoothing", smoothing, { smoothing = it }, "FAST", "SMOOTH", "${(smoothing * 100).toInt()}%", resetValue = 0.35f)
                            PropertySlider("Opacity", layerOpacity, { layerOpacity = it }, "0%", "100%", "${(layerOpacity * 100).toInt()}%", resetValue = 1f)
                            Text("Live app-audio FFT feeding the OpenGL spectrum renderer.", color = StudioMuted, fontSize = 8.sp)
                        }
                        1 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("LAYER STACK", color = StudioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Box {
                                    Button(onClick = { addLayerMenuExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = StudioBlack), shape = RoundedCornerShape(2.dp)) { Text("+ ADD ELEMENT", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    DropdownMenu(expanded = addLayerMenuExpanded, onDismissRequest = { addLayerMenuExpanded = false }) {
                                        listOf("Composition", "Text", "Bars", "Particles", "Image", "Blur Effect", "RGB Split", "Motion Blur", "Mirror Effect", "Custom Shader").forEach { type ->
                                            DropdownMenuItem(text = { Text(type) }, onClick = {
                                                val layer = EditorLayer(nextLayerId++, type, if (type == "Bars") "Spectrum Bars" else type)
                                                editorLayers.add(layer); selectedLayerId = layer.id; addLayerMenuExpanded = false
                                            })
                                        }
                                    }
                                }
                            }
                            editorLayers.forEachIndexed { index, layer ->
                                Row(Modifier.fillMaxWidth().background(if (layer.id == selectedLayerId) StudioRaised else StudioBlack, RoundedCornerShape(2.dp)).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(if (layer.visible) "●" else "○", color = if (layer.visible) StudioAccent else StudioMuted, fontSize = 10.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(layer.name, color = StudioWhite, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        Text(layer.type.uppercase(Locale.US), color = StudioMuted, fontSize = 7.sp)
                                    }
                                    Button(onClick = { selectedLayerId = layer.id }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = if (layer.id == selectedLayerId) StudioAccent else StudioPanel, contentColor = if (layer.id == selectedLayerId) StudioBlack else StudioWhite), shape = RoundedCornerShape(2.dp)) { Text("EDIT", fontSize = 8.sp) }
                                    Button(onClick = { editorLayers[index] = layer.copy(visible = !layer.visible) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioPanel), shape = RoundedCornerShape(2.dp)) { Text(if (layer.visible) "ON" else "OFF", fontSize = 8.sp, color = StudioWhite) }
                                    Button(onClick = { if (editorLayers.size > 1) { editorLayers.removeAt(index); if (selectedLayerId == layer.id) selectedLayerId = editorLayers.last().id } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioPanel), shape = RoundedCornerShape(2.dp)) { Text("×", fontSize = 11.sp, color = StudioWhite) }
                                }
                            }
                            val selectedIndex = editorLayers.indexOfFirst { it.id == selectedLayerId }
                            if (selectedIndex >= 0) {
                                val selected = editorLayers[selectedIndex]
                                Text("PROPERTIES / ${selected.name.uppercase(Locale.US)}", color = StudioAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                PropertySlider("Opacity", selected.opacity, { editorLayers[selectedIndex] = selected.copy(opacity = it) }, "0%", "100%", "${(selected.opacity * 100).toInt()}%", resetValue = 1f)
                                PropertySlider("Position X", selected.x, { editorLayers[selectedIndex] = selected.copy(x = it) }, "LEFT", "RIGHT", "${(selected.x * 100).toInt()}%", resetValue = 0.5f)
                                PropertySlider("Position Y", selected.y, { editorLayers[selectedIndex] = selected.copy(y = it) }, "TOP", "BOTTOM", "${(selected.y * 100).toInt()}%", resetValue = 0.5f)
                                PropertySlider("Scale", ((selected.scale - 0.1f) / 2.9f).coerceIn(0f, 1f), { editorLayers[selectedIndex] = selected.copy(scale = 0.1f + it * 2.9f) }, "0.1×", "3×", String.format(Locale.US, "%.2f×", selected.scale), resetValue = (1f - 0.1f) / 2.9f)
                                PropertySlider("Rotation", (selected.rotation + 180f) / 360f, { editorLayers[selectedIndex] = selected.copy(rotation = -180f + it * 360f) }, "-180°", "180°", "${selected.rotation.toInt()}°", resetValue = 0.5f)
                                if (selected.type == "Bars" || selected.type == "Particles") {
                                    Text("AUDIO / TIME MODULATION", color = StudioAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    MeasureDropdown("Position · Measure What", selected.positionMeasure) { editorLayers[selectedIndex] = selected.copy(positionMeasure = it) }
                                    MeasureDropdown("Scale · Measure What", selected.scaleMeasure) { editorLayers[selectedIndex] = selected.copy(scaleMeasure = it) }
                                    MeasureDropdown("Rotation · Measure What", selected.rotationMeasure) { editorLayers[selectedIndex] = selected.copy(rotationMeasure = it) }
                                    PropertySlider("Measure amount", selected.measureAmount, { editorLayers[selectedIndex] = selected.copy(measureAmount = it) }, "SUBTLE", "STRONG", "${(selected.measureAmount * 100).toInt()}%", resetValue = 0.5f)
                                    PropertyInfoRow("Beat detection", "Bass FFT onset / live")
                                }
                                if (selected.type == "Particles") {
                                    PropertySlider("Particle count", particleCount, { particleCount = it }, "20", "400", "${(20 + particleCount * 380).toInt()}", resetValue = 0.30f)
                                    PropertySlider("Movement speed", particleSpeed, { particleSpeed = it }, "SLOW", "FAST", String.format(Locale.US, "%.1f×", 0.1f + particleSpeed * 3.9f), resetValue = 0.35f)
                                    PropertySlider("Particle size", particleSize, { particleSize = it }, "1 PX", "12 PX", "${(1f + particleSize * 11f).toInt()} px", resetValue = 0.25f)
                                    PropertySlider("Spread", particleSpread, { particleSpread = it }, "NARROW", "WIDE", "${((0.1f + particleSpread * 1.9f) * 100).toInt()}%", resetValue = 0.40f)
                                    PropertyInfoRow("Renderer", "OpenGL ES / animated points")
                                    PropertyInfoRow("Audio response", "Live FFT energy modulation")
                                }
                                if (selected.type == "RGB Split") {
                                    PropertySlider("Channel separation", selected.rgbSplitAmount, { editorLayers[selectedIndex] = selected.copy(rgbSplitAmount = it) }, "0 PX", "MAX", "${(selected.rgbSplitAmount * 100).toInt()}%", resetValue = 0f)
                                    PropertyInfoRow("GPU renderer", "OpenGL RGB channel offsets")
                                    PropertyInfoRow("Effect scope", "Spectrum geometry only")
                                }
                                if (selected.type == "Image") {
                                    Button(
                                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = StudioBlack),
                                        shape = RoundedCornerShape(2.dp)
                                    ) { Text(if (selected.imageUri == null) "CHOOSE IMAGE" else "REPLACE IMAGE", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    PropertyInfoRow("Image source", if (selected.imageUri == null) "No image selected" else "Device image / loaded")
                                    Text("Image is rendered in the live preview with layer transforms.", color = StudioMuted, fontSize = 8.sp)
                                }
                                if (selected.type == "Text") {
                                    Text("TEXT CONTENT", color = StudioMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    BasicTextField(
                                        value = selected.textContent,
                                        onValueChange = { editorLayers[selectedIndex] = selected.copy(textContent = it) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = StudioWhite, fontSize = 11.sp),
                                        modifier = Modifier.fillMaxWidth().background(StudioBlack, RoundedCornerShape(2.dp)).padding(8.dp)
                                    )
                                    PropertySlider("Font size", ((selected.fontSize - 8f) / 88f).coerceIn(0f, 1f), { editorLayers[selectedIndex] = selected.copy(fontSize = 8f + it * 88f) }, "8 SP", "96 SP", "${selected.fontSize.toInt()} sp", resetValue = 0.25f)
                                    PropertyInfoRow("Text renderer", "Compose overlay / active")
                                }
                                Text("Text, image and particle layers render in preview. Other effects remain pending.", color = StudioMuted, fontSize = 8.sp)
                            }
                        }
                        2 -> {
                            PropertySlider("Input gain", audioGain, { audioGain = it }, "QUIET", "LOUD", "${(audioGain * 100).toInt()}%", resetValue = 0.70f)
                            PropertySlider("Spectrum lower range", fftLower, { fftLower = it.coerceAtMost(fftUpper - 0.05f) }, "LOW", "MID", "${(20 + fftLower * 180).toInt()} Hz", resetValue = 0.02f)
                            PropertySlider("Spectrum upper range", fftUpper, { fftUpper = it.coerceAtLeast(fftLower + 0.05f) }, "MID", "HIGH", "${(200 + fftUpper * 17800).toInt()} Hz", resetValue = 0.92f)
                            PropertySlider("Beat sensitivity", beatSensitivity, { beatSensitivity = it }, "LESS", "MORE", "${(beatSensitivity * 100).toInt()}%", resetValue = 0.55f)
                            PropertyInfoRow("Sample provider", "App playback / FFT")
                            PropertyInfoRow("FFT data", "128 logarithmic analysis bands")
                            PropertyInfoRow("Capture source", "This app's ExoPlayer session")
                        }
                        3 -> {
                            PropertySlider("Glow intensity", glow, { glow = it }, "OFF", "MAX", "${(glow * 100).toInt()}%", resetValue = 0.25f)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = mirrorEnabled, onCheckedChange = { mirrorEnabled = it })
                                Text("Mirror spectrum", color = StudioWhite, fontSize = 10.sp)
                            }
                            PropertyInfoRow("Current renderer", "Spectrum bars")
                            PropertyInfoRow("Next engines", "Particles / blur / RGB split / motion blur")
                            Text("Only implemented effects will be marked as active.", color = StudioMuted, fontSize = 8.sp)
                        }
                        else -> {
                            PropertyInfoRow("Interface", "Monochrome studio")
                            PropertyInfoRow("Preview layout", "40% preview / 60% controls")
                            PropertyInfoRow("Rendering", "OpenGL ES 2.0")
                            PropertyInfoRow("Video export", "Not implemented yet")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerImagePreview(
    uri: Uri,
    opacity: Float,
    x: Float,
    y: Float,
    scale: Float,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    bitmap?.let { decoded ->
        Image(
            bitmap = decoded.asImageBitmap(),
            contentDescription = "Visualizer image layer",
            contentScale = ContentScale.Fit,
            modifier = modifier.graphicsLayer {
                alpha = opacity
                translationX = (x - 0.5f) * size.width
                translationY = (y - 0.5f) * size.height
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
        )
    }
}

@Composable
private fun LiveVisualizerPreview(
    audioSessionId: Int,
    hasAudioPermission: Boolean,
    settings: VisualizerSettings,
    audioGain: Float,
    lowFrequencyFraction: Float,
    highFrequencyFraction: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val surface = remember(context) { VisualizerSurface(context) }

    AndroidView(
        factory = { surface },
        update = { it.updateSettings(settings) },
        modifier = modifier
    )

    DisposableEffect(surface, audioSessionId, hasAudioPermission, settings.smoothing, audioGain, lowFrequencyFraction, highFrequencyFraction) {
        val capture = VisualizerCapture(
            smoothing = settings.smoothing,
            gain = audioGain,
            lowFrequencyFraction = lowFrequencyFraction,
            highFrequencyFraction = highFrequencyFraction
        ) { values ->
            surface.submitSpectrum(values)
        }
        if (hasAudioPermission && audioSessionId > 0) capture.start(audioSessionId)
        onDispose { capture.stop() }
    }
}

@Composable
private fun MeasureDropdown(
    label: String,
    selected: MeasureWhat,
    onSelected: (MeasureWhat) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = StudioMuted, fontSize = 8.sp)
        Box {
            Button(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StudioBlack, contentColor = StudioWhite),
                shape = RoundedCornerShape(2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(selected.label, fontSize = 9.sp)
                Spacer(Modifier.weight(1f))
                Text("▾", color = StudioAccent, fontSize = 10.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MeasureWhat.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, fontSize = 10.sp) },
                        onClick = { onSelected(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    startLabel: String,
    endLabel: String,
    valueLabel: String,
    resetValue: Float = 0.5f
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = StudioWhite, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = StudioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(resetValue) {
                    detectTapGestures(onDoubleTap = { onValueChange(resetValue.coerceIn(0f, 1f)) })
                }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(startLabel, color = StudioMuted, fontSize = 7.sp)
            Spacer(Modifier.weight(1f))
            Text("DOUBLE-TAP TO RESET", color = StudioMuted.copy(alpha = 0.65f), fontSize = 6.sp)
            Spacer(Modifier.weight(1f))
            Text(endLabel, color = StudioMuted, fontSize = 7.sp)
        }
    }
}

@Composable
private fun PropertyInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(StudioBlack, RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = StudioMuted, fontSize = 9.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = StudioWhite, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class EditorLayer(
    val id: Int, val type: String, val name: String,
    val visible: Boolean = true, val opacity: Float = 1f,
    val x: Float = 0.5f, val y: Float = 0.5f,
    val scale: Float = 1f, val rotation: Float = 0f,
    val textContent: String = "{ArtistOrTitle}",
    val fontSize: Float = 30f,
    val textColor: Long = 0xFFFFFFFF,
    val imageUri: String? = null,
    val positionMeasure: MeasureWhat = MeasureWhat.NOTHING,
    val scaleMeasure: MeasureWhat = MeasureWhat.NOTHING,
    val rotationMeasure: MeasureWhat = MeasureWhat.NOTHING,
    val measureAmount: Float = 0.5f,
    val rgbSplitAmount: Float = 0f
)

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected audio"
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
