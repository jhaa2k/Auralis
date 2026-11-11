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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.runtime.snapshotFlow
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
import com.auralis.app.export.VideoExportConfig
import com.auralis.app.export.VisualizerVideoExporter
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce
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
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var isLooping by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var playerMessage by remember { mutableStateOf("CONNECTING TO AUDIO ENGINE") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val exportScope = rememberCoroutineScope()
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatus by remember { mutableStateOf("READY TO EXPORT") }
    var exportResolution by remember { mutableFloatStateOf(0.5f) }
    var exportFps by remember { mutableFloatStateOf(0.2f) }
    val editorLayers = remember { mutableStateListOf(EditorLayer(1, "Composition", "Main Composition"), EditorLayer(2, "Bars", "Spectrum Bars")) }
    var selectedLayerId by remember { mutableIntStateOf(2) }
    var selectedPropertyCategory by remember { mutableStateOf("GENERAL") }
    var detailsModule by remember { mutableStateOf<String?>(null) }
    var detailsLayerId by remember { mutableIntStateOf(-1) }
    val modulePropertyValues = remember { mutableStateMapOf<String, String>() }
    var pendingImageField by remember { mutableStateOf("Pick Image") }

    // Restore the per-layer property dictionary once, then persist edits with a short debounce.
    LaunchedEffect(context) {
        modulePropertyValues.putAll(restoreModuleProperties(context))
    }
    LaunchedEffect(context) {
        snapshotFlow { modulePropertyValues.toMap() }
            .drop(1)
            .debounce(450)
            .collectLatest { persistModuleProperties(context, it) }
    }
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
    var barWidth by remember { mutableFloatStateOf(0.67f) }
    var barHeight by remember { mutableFloatStateOf(0.47f) }
    var segmentCountValue by remember { mutableFloatStateOf(0f) }
    var segmentGapValue by remember { mutableFloatStateOf(0.40f) }

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

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { outputUri ->
        val sourceUri = selectedTrackUri
        if (outputUri != null && sourceUri != null) {
            exportScope.launch {
                exportProgress = 0f
                exportStatus = "STARTING EXPORT"
                val sizes = listOf(1280 to 720, 1920 to 1080, 2560 to 1440, 3840 to 2160)
                val size = sizes[(exportResolution * (sizes.size - 1)).toInt().coerceIn(0, sizes.lastIndex)]
                val fpsValues = listOf(25, 30, 50, 60)
                val fps = fpsValues[(exportFps * (fpsValues.size - 1)).toInt().coerceIn(0, fpsValues.lastIndex)]
                runCatching {
                    VisualizerVideoExporter.export(
                        context = context,
                        audio = sourceUri,
                        destination = outputUri,
                        config = VideoExportConfig(width = size.first, height = size.second, fps = fps, includeAudio = true)
                    ) { progress, message ->
                        exportProgress = progress
                        exportStatus = message
                    }
                }.onSuccess {
                    exportProgress = 1f
                    exportStatus = "MP4 SAVED"
                }.onFailure { error ->
                    exportStatus = "EXPORT FAILED: ${error.message ?: "UNKNOWN ERROR"}"
                }
            }
        } else {
            exportStatus = "IMPORT AN AUDIO TRACK FIRST"
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val index = editorLayers.indexOfFirst { it.id == selectedLayerId }
            if (index >= 0) editorLayers[index] = editorLayers[index].copy(imageUri = uri.toString())
            modulePropertyValues["$selectedLayerId/$pendingImageField"] = uri.toString()
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
                            barWidth = 0.15f + barWidth * 0.85f,
                            barHeight = 0.1f + barHeight * 1.9f,
                            segmentCount = 1 + (segmentCountValue * 31f).toInt(),
                            segmentGap = segmentGapValue * 0.45f,
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
                            text = textLayer.textContent.replace("%title%", selectedTrackName ?: textLayer.songTitle).replace("%artist%", textLayer.artistName).replace("%time%", formatTime(playbackPosition)).replace("%duration%", formatTime(playbackDuration)),
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
                            fitMode = imageLayer.imageFitMode,
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
                        TimelineSeekBar(
                            positionMs = playbackPosition,
                            durationMs = playbackDuration,
                            enabled = mediaController != null && playbackDuration > 0L,
                            onSeek = { target -> playbackPosition = target; mediaController?.seekTo(target) }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { mediaController?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) } }, enabled = selectedTrackUri != null, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioRaised, contentColor = StudioWhite), shape = RoundedCornerShape(2.dp)) { Text("-10", fontSize = 8.sp) }
                            Button(onClick = { isLooping = !isLooping; mediaController?.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isLooping) StudioAccent else StudioRaised, contentColor = if (isLooping) StudioBlack else StudioWhite), shape = RoundedCornerShape(2.dp)) { Text(if (isLooping) "LOOP ON" else "LOOP OFF", fontSize = 8.sp) }
                            Button(onClick = { mediaController?.let { it.seekTo((it.currentPosition + 10_000L).coerceAtMost(it.duration.coerceAtLeast(0L))) } }, enabled = selectedTrackUri != null, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioRaised, contentColor = StudioWhite), shape = RoundedCornerShape(2.dp)) { Text("+10", fontSize = 8.sp) }
                            Box {
                                Button(onClick = { speedMenuExpanded = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioRaised, contentColor = StudioWhite), shape = RoundedCornerShape(2.dp)) { Text("${playbackSpeed}×", fontSize = 8.sp) }
                                DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                        DropdownMenuItem(text = { Text("${speed}×") }, onClick = { playbackSpeed = speed; mediaController?.setPlaybackSpeed(speed); speedMenuExpanded = false })
                                    }
                                }
                            }
                        }
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
                            Text("MODULE CANVAS", color = StudioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("SELECT A MODULE TO ADD IT TO THE COMPOSITION", color = StudioMuted, fontSize = 7.sp)
                            val moduleGrid = listOf(
                                "COMPOSITION" to "Composition",
                                "TEXT" to "Text",
                                "BARS" to "Bars",
                                "PARTICLES" to "Particles",
                                "IMAGE" to "Image",
                                "AUDIOPROVIDER" to "Audio Provider",
                                "BLUREFFECT" to "Blur Effect",
                                "RGBSPLITEFFECT" to "RGB Split",
                                "MOTIONBLUREFFECT" to "Motion Blur",
                                "MIRROREFFECT" to "Mirror Effect",
                                "CUSTOMSHADER" to "Custom Shader"
                            )
                            moduleGrid.chunked(2).forEach { gridRow ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    gridRow.forEach { (label, type) ->
                                        Surface(
                                            modifier = Modifier.weight(1f).height(43.dp),
                                            color = StudioBlack,
                                            shape = RoundedCornerShape(2.dp),
                                            border = BorderStroke(1.dp, StudioLine),
                                            onClick = {
                                                if (type == "Composition") {
                                                    selectedLayerId = editorLayers.firstOrNull { it.type == "Composition" }?.id ?: editorLayers.first().id
                                                } else {
                                                    val layer = EditorLayer(nextLayerId++, type, if (type == "Bars") "Spectrum Bars" else type)
                                                    editorLayers.add(layer)
                                                    selectedLayerId = layer.id
                                                }
                                                detailsLayerId = selectedLayerId
                                                detailsModule = when(type) {
                                                    "Composition" -> "COMPOSITION"
                                                    "Text" -> "TEXT"
                                                    "Bars" -> "BARS"
                                                    "Particles" -> "PARTICLES"
                                                    "Image" -> "IMAGE"
                                                    "Audio Provider" -> "AUDIOPROVIDER"
                                                    "Blur Effect" -> "BLUREFFECT"
                                                    "RGB Split" -> "RGBSPLITEFFECT"
                                                    "Motion Blur" -> "MOTIONBLUREFFECT"
                                                    "Mirror Effect" -> "MIRROREFFECT"
                                                    else -> "CUSTOMSHADER"
                                                }
                                                selectedTab = if (type == "Audio Provider") 2 else 1
                                                selectedPropertyCategory = "GENERAL"
                                            }
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(label, color = StudioWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            }
                                        }
                                    }
                                    if (gridRow.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
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
                                    Button(onClick = { selectedLayerId = layer.id; detailsLayerId = layer.id; detailsModule = moduleKeyForLayer(layer.type) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = if (layer.id == selectedLayerId) StudioAccent else StudioPanel, contentColor = if (layer.id == selectedLayerId) StudioBlack else StudioWhite), shape = RoundedCornerShape(2.dp)) { Text("EDIT", fontSize = 8.sp) }
                                    Button(onClick = { editorLayers[index] = layer.copy(visible = !layer.visible) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioPanel), shape = RoundedCornerShape(2.dp)) { Text(if (layer.visible) "ON" else "OFF", fontSize = 8.sp, color = StudioWhite) }
                                    Button(onClick = { if (editorLayers.size > 1) { editorLayers.removeAt(index); if (selectedLayerId == layer.id) selectedLayerId = editorLayers.last().id } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioPanel), shape = RoundedCornerShape(2.dp)) { Text("×", fontSize = 11.sp, color = StudioWhite) }
                                }
                            }
                            val selectedIndex = editorLayers.indexOfFirst { it.id == selectedLayerId }
                            if (selectedIndex >= 0) {
                                val selected = editorLayers[selectedIndex]
                                Text("PROPERTIES / ${selected.name.uppercase(Locale.US)}", color = StudioAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                val propertyCategories = listOf("GENERAL", "COLOR", "ADJUSTMENT", "BEHAVIOUR", "AUDIO / MEASURE", "ADVANCED")
                                propertyCategories.chunked(3).forEach { categoryRow ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        categoryRow.forEach { category ->
                                            Button(
                                                onClick = { selectedPropertyCategory = category },
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selectedPropertyCategory == category) StudioAccent else StudioPanel,
                                                    contentColor = if (selectedPropertyCategory == category) StudioBlack else StudioMuted
                                                ),
                                                shape = RoundedCornerShape(2.dp)
                                            ) { Text(category, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                                        }
                                    }
                                }
                                when (selectedPropertyCategory) {
                                    "GENERAL" -> {
                                        PropertySlider("Opacity", selected.opacity, { editorLayers[selectedIndex] = selected.copy(opacity = it) }, "0%", "100%", "${(selected.opacity * 100).toInt()}%", resetValue = 1f)
                                        PropertySlider("Position X", selected.x, { editorLayers[selectedIndex] = selected.copy(x = it) }, "LEFT", "RIGHT", "${(selected.x * 100).toInt()}%", resetValue = 0.5f)
                                        PropertySlider("Position Y", selected.y, { editorLayers[selectedIndex] = selected.copy(y = it) }, "TOP", "BOTTOM", "${(selected.y * 100).toInt()}%", resetValue = 0.5f)
                                        PropertySlider("Scale", ((selected.scale - 0.1f) / 2.9f).coerceIn(0f, 1f), { editorLayers[selectedIndex] = selected.copy(scale = 0.1f + it * 2.9f) }, "0.1×", "3×", String.format(Locale.US, "%.2f×", selected.scale), resetValue = (1f - 0.1f) / 2.9f)
                                        PropertySlider("Rotation", (selected.rotation + 180f) / 360f, { editorLayers[selectedIndex] = selected.copy(rotation = -180f + it * 360f) }, "-180°", "180°", "${selected.rotation.toInt()}°", resetValue = 0.5f)
                                        if (selected.type == "Text") {
                                            Text("TEXT CONTENT", color = StudioMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            BasicTextField(value = selected.textContent, onValueChange = { editorLayers[selectedIndex] = selected.copy(textContent = it) }, textStyle = androidx.compose.ui.text.TextStyle(color = StudioWhite, fontSize = 11.sp), modifier = Modifier.fillMaxWidth().background(StudioBlack, RoundedCornerShape(2.dp)).padding(8.dp))
                                            PropertySlider("Font size", ((selected.fontSize - 8f) / 88f).coerceIn(0f, 1f), { editorLayers[selectedIndex] = selected.copy(fontSize = 8f + it * 88f) }, "8 SP", "96 SP", "${selected.fontSize.toInt()} sp", resetValue = 0.25f)
                                        }
                                        if (selected.type == "Image" || selected.type == "Particles" || selected.type == "Composition") {
                                            Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = StudioBlack), shape = RoundedCornerShape(2.dp)) { Text(if (selected.imageUri == null) "PICK IMAGE / PNG" else "REPLACE IMAGE", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                            PropertyInfoRow("Image source", if (selected.imageUri == null) "No custom image selected" else "Custom image selected")
                                        }
                                        if (selected.type == "Image") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf("KEEP_ASPECT_RATIO", "CROP_TO_FIT").forEach { mode ->
                                                    Button(onClick = { editorLayers[selectedIndex] = selected.copy(imageFitMode = mode) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected.imageFitMode == mode) StudioAccent else StudioRaised, contentColor = if (selected.imageFitMode == mode) StudioBlack else StudioWhite), shape = RoundedCornerShape(2.dp)) { Text(if (mode == "KEEP_ASPECT_RATIO") "KEEP RATIO" else "CROP TO FIT", fontSize = 7.sp) }
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf("GENERATED_ALBUM_ART", "CUSTOM_IMAGE").forEach { mode ->
                                                    Button(onClick = { editorLayers[selectedIndex] = selected.copy(imageSourceMode = mode) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected.imageSourceMode == mode) StudioAccent else StudioRaised, contentColor = if (selected.imageSourceMode == mode) StudioBlack else StudioWhite), shape = RoundedCornerShape(2.dp)) { Text(if (mode == "GENERATED_ALBUM_ART") "ALBUM ART" else "CUSTOM IMAGE", fontSize = 7.sp) }
                                                }
                                            }
                                        }
                                        if (selected.type == "Text") {
                                            PropertyInfoRow("Tokens", "%title%  %artist%  %time%  %duration%")
                                            BasicTextField(value = selected.songTitle, onValueChange = { editorLayers[selectedIndex] = selected.copy(songTitle = it) }, textStyle = androidx.compose.ui.text.TextStyle(color = StudioWhite, fontSize = 10.sp), modifier = Modifier.fillMaxWidth().background(StudioBlack).padding(6.dp))
                                            BasicTextField(value = selected.artistName, onValueChange = { editorLayers[selectedIndex] = selected.copy(artistName = it) }, textStyle = androidx.compose.ui.text.TextStyle(color = StudioWhite, fontSize = 10.sp), modifier = Modifier.fillMaxWidth().background(StudioBlack).padding(6.dp))
                                        }
                                        if (selected.type == "Composition") {
                                            PropertyInfoRow("Composition index", "Creation order is immutable")
                                            PropertyInfoRow("Blend mode", selected.compositionBlendMode)
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                listOf("NORMAL", "ADD", "SCREEN", "MULTIPLY", "OVERLAY").forEach { mode ->
                                                    Button(onClick = { editorLayers[selectedIndex] = selected.copy(compositionBlendMode = mode) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected.compositionBlendMode == mode) StudioAccent else StudioRaised, contentColor = if (selected.compositionBlendMode == mode) StudioBlack else StudioWhite), shape = RoundedCornerShape(2.dp)) { Text(mode, fontSize = 6.sp) }
                                                }
                                            }
                                        }
                                    }
                                    "COLOR" -> {
                                        Text("TEXT COLOR PRESETS", color = StudioMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        if (selected.type == "Particles") {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                listOf(0xFFFFFFFF, 0xFFFF8A5B, 0xFF59D6FF, 0xFFB58CFF, 0xFF70E0A0, 0xFFFFD166).forEach { argb ->
                                                    Surface(modifier = Modifier.size(27.dp), color = Color(argb), shape = RoundedCornerShape(2.dp), border = BorderStroke(1.dp, StudioLine), onClick = { editorLayers[selectedIndex] = selected.copy(particleColor = argb) }) {}
                                                }
                                            }
                                            PropertyInfoRow("Particle color", "Preset palette")
                                        }
                                        if (selected.type == "Text") {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                listOf(0xFFFFFFFF, 0xFFFF8A5B, 0xFF59D6FF, 0xFFB58CFF, 0xFF70E0A0, 0xFFFFD166).forEach { argb ->
                                                    val swatch = Color(argb)
                                                    Surface(
                                                        modifier = Modifier.size(28.dp),
                                                        color = swatch,
                                                        shape = RoundedCornerShape(2.dp),
                                                        border = BorderStroke(if (selected.textColor == argb) 2.dp else 1.dp, if (selected.textColor == argb) StudioAccent else StudioMuted),
                                                        onClick = { editorLayers[selectedIndex] = selected.copy(textColor = argb) }
                                                    ) {}
                                                }
                                            }
                                            PropertyInfoRow("Color target", "Selected text layer")
                                        } else {
                                            PropertyInfoRow("Color controls", "This layer type is not color-mapped yet")
                                            PropertyInfoRow("Status", "Palette controls will activate with its renderer")
                                        }
                                    }
                                    "ADJUSTMENT" -> {
                                        if (selected.type == "Bars") {
                                            PropertySlider("Bar width", barWidth, { barWidth = it }, "THIN", "WIDE", "${(15 + barWidth * 85).toInt()}%", resetValue = 0.67f)
                                            PropertySlider("Bar height", barHeight, { barHeight = it }, "SHORT", "TALL", "${(10 + barHeight * 190).toInt()}%", resetValue = 0.47f)
                                            PropertySlider("Segments per bar", segmentCountValue, { segmentCountValue = it }, "1", "32", "${1 + (segmentCountValue * 31f).toInt()}", resetValue = 0f)
                                            PropertySlider("Segment spacing", segmentGapValue, { segmentGapValue = it }, "TIGHT", "WIDE", "${(segmentGapValue * 45).toInt()}%", resetValue = 0.40f)
                                        } else if (selected.type == "Particles") {
                                            PropertySlider("Particle count", particleCount, { particleCount = it }, "20", "400", "${(20 + particleCount * 380).toInt()}", resetValue = 0.30f)
                                            PropertySlider("Movement speed", particleSpeed, { particleSpeed = it }, "SLOW", "FAST", String.format(Locale.US, "%.1f×", 0.1f + particleSpeed * 3.9f), resetValue = 0.35f)
                                            PropertySlider("Particle size", particleSize, { particleSize = it }, "1 PX", "12 PX", "${(1f + particleSize * 11f).toInt()} px", resetValue = 0.25f)
                                            PropertySlider("Spread", particleSpread, { particleSpread = it }, "NARROW", "WIDE", "${((0.1f + particleSpread * 1.9f) * 100).toInt()}%", resetValue = 0.40f)
                                            PropertySlider("Vector angle", (selected.particleVectorAngle + 360f) / 720f, { editorLayers[selectedIndex] = selected.copy(particleVectorAngle = -360f + it * 720f) }, "-360°", "360°", "${selected.particleVectorAngle.toInt()}°", resetValue = 0.5f)
                                            PropertySlider("Transparency", selected.particleTransparency, { editorLayers[selectedIndex] = selected.copy(particleTransparency = it) }, "0%", "100%", "${(selected.particleTransparency * 100).toInt()}%", resetValue = 1f)
                                            if (selected.imageUri != null) PropertyInfoRow("Particle PNG", "Custom image selected; GL texture mapping pending")
                                        } else if (selected.type == "Blur Effect") {
                                            PropertySlider("Background softener", selected.blurIntensity / 0.65f, { editorLayers[selectedIndex] = selected.copy(blurIntensity = it * 0.65f) }, "OFF", "SAFE MAX", "${(selected.blurIntensity * 100).toInt()}%", resetValue = 0.15f / 0.65f)
                                            if (selected.blurIntensity > 0.45f) Text("WARNING: High blur can increase GPU memory and frame time.", color = StudioAccent, fontSize = 8.sp)
                                            PropertyInfoRow("Safety cap", "0.65 intensity")
                                        } else if (selected.type == "Motion Blur") {
                                            PropertySlider("Trailing amount", selected.motionBlurTrailing / 0.65f, { editorLayers[selectedIndex] = selected.copy(motionBlurTrailing = it * 0.65f) }, "LOW", "SAFE MAX", "${(selected.motionBlurTrailing * 100).toInt()}%", resetValue = 0.2f / 0.65f)
                                            if (selected.motionBlurTrailing > 0.45f) Text("WARNING: High trailing can increase GPU load.", color = StudioAccent, fontSize = 8.sp)
                                        } else if (selected.type == "Custom Shader") {
                                            Text("GLSL ES 2.0 SOURCE", color = StudioMuted, fontSize = 8.sp)
                                            BasicTextField(value = selected.shaderSource, onValueChange = { editorLayers[selectedIndex] = selected.copy(shaderSource = it) }, textStyle = androidx.compose.ui.text.TextStyle(color = StudioWhite, fontSize = 9.sp), modifier = Modifier.fillMaxWidth().height(120.dp).background(StudioBlack).padding(7.dp))
                                            PropertyInfoRow("Shader compiler", "Not connected yet")
                                        } else if (selected.type == "Bars") {
                                            PropertySlider("Speed", selected.barsSpeed / 4f, { editorLayers[selectedIndex] = selected.copy(barsSpeed = 0.1f + it * 3.9f) }, "SLOW", "FAST", String.format(Locale.US, "%.1f×", selected.barsSpeed), resetValue = 0.23f)
                                            PropertySlider("Beat amount", selected.beatAmount, { editorLayers[selectedIndex] = selected.copy(beatAmount = it) }, "SUBTLE", "STRONG", "${(selected.beatAmount * 100).toInt()}%", resetValue = 0.5f)
                                            if ((12 + barCount * 116).toInt() > 96 || (1 + (segmentCountValue * 31f).toInt()) > 24) Text("WARNING: High bars/segments count may reduce frame rate.", color = StudioAccent, fontSize = 8.sp)
                                        } else if (selected.type == "RGB Split") {
                                            PropertySlider("Channel separation", selected.rgbSplitAmount, { editorLayers[selectedIndex] = selected.copy(rgbSplitAmount = it) }, "0 PX", "MAX", "${(selected.rgbSplitAmount * 100).toInt()}%", resetValue = 0f)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = selected.rgbBeatTrigger, onCheckedChange = { editorLayers[selectedIndex] = selected.copy(rgbBeatTrigger = it) })
                                                Text("Trigger on heavy beats", color = StudioWhite, fontSize = 8.sp)
                                            }
                                            PropertyInfoRow("Effect scope", "Spectrum geometry only; full-frame pending")
                                        } else {
                                            PropertyInfoRow("Adjustment controls", "No type-specific adjustments available")
                                        }
                                    }
                                    "BEHAVIOUR" -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = selected.visible, onCheckedChange = { editorLayers[selectedIndex] = selected.copy(visible = it) })
                                            Text("Layer visible", color = StudioWhite, fontSize = 9.sp)
                                        }
                                        PropertyInfoRow("Layer behaviour", "Visibility and transform are active")
                                        if (selected.type == "Particles") PropertyInfoRow("Motion", "Procedural movement / live FFT energy")
                                        if (selected.type == "Mirror Effect") {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = mirrorEnabled, onCheckedChange = { mirrorEnabled = it })
                                                Text("Enable symmetry", color = StudioWhite, fontSize = 8.sp)
                                            }
                                            PropertyInfoRow("Target layer", "Selected layer: ${selected.name}")
                                            PropertyInfoRow("Mirror mode", "Horizontal / vertical architecture")
                                        }
                                        if (selected.type == "Image") PropertyInfoRow("Aspect ratio", "Fit without stretching")
                                    }
                                    "AUDIO / MEASURE" -> {
                                        if (selected.type == "Bars" || selected.type == "Particles") {
                                            MeasureDropdown("Position · Measure What", selected.positionMeasure) { editorLayers[selectedIndex] = selected.copy(positionMeasure = it) }
                                            MeasureDropdown("Scale · Measure What", selected.scaleMeasure) { editorLayers[selectedIndex] = selected.copy(scaleMeasure = it) }
                                            MeasureDropdown("Rotation · Measure What", selected.rotationMeasure) { editorLayers[selectedIndex] = selected.copy(rotationMeasure = it) }
                                            PropertySlider("Measure amount", selected.measureAmount, { editorLayers[selectedIndex] = selected.copy(measureAmount = it) }, "SUBTLE", "STRONG", "${(selected.measureAmount * 100).toInt()}%", resetValue = 0.5f)
                                            PropertyInfoRow("Beat detector", "Bass FFT onset / live")
                                        } else {
                                            PropertyInfoRow("Audio modulation", "Not connected for this layer type")
                                        }
                                    }
                                    else -> {
                                        PropertyInfoRow("Layer type", selected.type)
                                        PropertyInfoRow("Preview", if (selected.type == "Text" || selected.type == "Image" || selected.type == "Bars" || selected.type == "Particles") "Implemented for this layer" else "Not implemented")
                                        PropertyInfoRow("Export compositor", "Full layer export is still pending")
                                        PropertyInfoRow("Advanced schema", "Typed Avee-style property models are in the project")
                                    }
                                }
                                Text("Text, image and particle layers render in preview. Other effects remain pending.", color = StudioMuted, fontSize = 8.sp)
                            }
                        }
                        2 -> {
                            PropertySlider("Input gain", audioGain, { audioGain = it }, "QUIET", "LOUD", "${(audioGain * 100).toInt()}%", resetValue = 0.70f)
                            PropertySlider("Playback volume", volumeLevel, { volumeLevel = it; mediaController?.volume = it }, "MUTE", "100%", "${(volumeLevel * 100).toInt()}%", resetValue = 1f)
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
                            PropertyInfoRow("Interface", "Monochrome compact studio")
                            PropertyInfoRow("Preview layout", "40% preview / 60% controls")
                            PropertyInfoRow("Preview renderer", "OpenGL ES 2.0")
                            Text("VIDEO EXPORT", color = StudioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            PropertySlider("Output resolution", exportResolution, { exportResolution = it }, "720P", "4K", listOf("720P", "1080P", "1440P", "4K")[(exportResolution * 3).toInt().coerceIn(0,3)], resetValue = 0.33f)
                            PropertySlider("Frame rate", exportFps, { exportFps = it }, "25 FPS", "60 FPS", "${listOf(25,30,50,60)[(exportFps * 3).toInt().coerceIn(0,3)]} FPS", resetValue = 0.2f)
                            Button(
                                onClick = {
                                    if (selectedTrackUri != null) exportPicker.launch("Auralis-${System.currentTimeMillis()}.mp4")
                                    else exportStatus = "IMPORT AN AUDIO TRACK FIRST"
                                },
                                enabled = exportStatus != "RENDERING VIDEO",
                                colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = StudioBlack),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("EXPORT AUDIO-REACTIVE MP4", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { exportProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = StudioAccent,
                                trackColor = StudioBlack
                            )
                            PropertyInfoRow("Export status", exportStatus)
                            PropertyInfoRow("Encoder", "MediaCodec AVC + EGL / GPU")
                            PropertyInfoRow("Audio", "Original selected track / muxed")
                            PropertyInfoRow("Output", "MP4 · up to 4K · 25/30/50/60 FPS")
                            Text("This export renders the FFT-reactive spectrum bars and muxes the original audio. Text, image and other editor layers are not yet included in the export compositor.", color = StudioMuted, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
    }

    // A full-screen property route sits above the studio and keeps values keyed by layer ID.
    val activeModule = detailsModule
    if (activeModule != null && detailsLayerId > 0) {
        Dialog(onDismissRequest = { detailsModule = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            ModuleDetailsScreen(
                module = activeModule,
                layerId = detailsLayerId,
                values = modulePropertyValues,
                onBack = { detailsModule = null },
                onReset = { modulePropertyValues.keys.filter { it.startsWith("$detailsLayerId/") }.forEach { modulePropertyValues.remove(it) } },
                onPickImage = { field -> pendingImageField = field; imagePicker.launch(arrayOf("image/*")) },
                onPropertyChange = { group, field, value ->
                    val index = editorLayers.indexOfFirst { it.id == detailsLayerId }
                    if (index >= 0) {
                        val layer = editorLayers[index]
                        val number = value.toFloatOrNull()
                        val color = runCatching { android.graphics.Color.parseColor(value).toLong() }.getOrNull()
                        val updated = when {
                            group == "General" && field == "Visible" -> layer.copy(visible = value.toBoolean())
                            group == "General" && field == "Position X" -> layer.copy(x = (number ?: layer.x).coerceIn(0f, 1f))
                            group == "General" && field == "Position Y" -> layer.copy(y = (number ?: layer.y).coerceIn(0f, 1f))
                            group == "General" && field == "Scale X" -> layer.copy(scale = (number ?: layer.scale).coerceIn(0.1f, 3f))
                            group == "General" && field == "Scale Y" -> layer.copy(scale = (number ?: layer.scale).coerceIn(0.1f, 3f))
                            group == "General" && field == "Rotation" -> layer.copy(rotation = (number ?: layer.rotation).coerceIn(-360f, 360f))
                            group == "Appearance" && activeModule == "TEXT" && field == "Text" -> layer.copy(textContent = value)
                            group == "Appearance" && activeModule == "TEXT" && field == "Font Size" -> layer.copy(fontSize = (number ?: layer.fontSize).coerceIn(8f, 96f))
                            group == "Appearance" && activeModule == "TEXT" && field == "Color" && color != null -> layer.copy(textColor = color)
                            group == "Image" && activeModule == "IMAGE" && field == "Crop to Fit" -> layer.copy(imageFitMode = if (value.toBoolean()) "CROP_TO_FIT" else "KEEP_ASPECT_RATIO")
                            group == "Image" && activeModule == "IMAGE" && field == "Keep Aspect Ratio" -> layer.copy(imageFitMode = if (value.toBoolean()) "KEEP_ASPECT_RATIO" else "CROP_TO_FIT")
                            group == "Overall" && activeModule == "PARTICLES" && field == "Vector Angle" -> layer.copy(particleVectorAngle = number ?: layer.particleVectorAngle)
                            group == "Overall" && activeModule == "PARTICLES" && field == "Color" && color != null -> layer.copy(particleColor = color)
                            group == "Appearance" && activeModule == "PARTICLES" && field == "Color From" && color != null -> layer.copy(particleColor = color)
                            group == "Appearance" && activeModule == "RGBSPLITEFFECT" && field == "Split Multiplier" -> layer.copy(rgbSplitAmount = (number ?: 0f).coerceIn(0f, 1f))
                            group == "Motion Blur" && field == "Blur Amount Multiplier" -> layer.copy(motionBlurTrailing = (number ?: 0f).coerceIn(0f, 0.65f))
                            group == "Blur" && field == "Blur Radius" -> layer.copy(blurIntensity = (number ?: 0f).coerceIn(0f, 0.65f))
                            else -> layer
                        }
                        editorLayers[index] = updated
                        if (activeModule == "BARS" && group == "Bars" && field == "Bars Count") {
                            barCount = (((number ?: 64f) - 12f) / 116f).coerceIn(0f, 1f)
                        }
                    }
                }
            )
        }
    }
}

private fun moduleKeyForLayer(type: String): String = when(type) {
    "Composition" -> "COMPOSITION"
    "Text" -> "TEXT"
    "Bars" -> "BARS"
    "Particles" -> "PARTICLES"
    "Image" -> "IMAGE"
    "Audio Provider" -> "AUDIOPROVIDER"
    "Blur Effect" -> "BLUREFFECT"
    "RGB Split" -> "RGBSPLITEFFECT"
    "Motion Blur" -> "MOTIONBLUREFFECT"
    "Mirror Effect" -> "MIRROREFFECT"
    else -> "CUSTOMSHADER"
}

@Composable
private fun LayerImagePreview(
    uri: Uri,
    opacity: Float,
    x: Float,
    y: Float,
    scale: Float,
    rotation: Float,
    fitMode: String = "KEEP_ASPECT_RATIO",
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
            contentScale = if (fitMode == "CROP_TO_FIT") ContentScale.Crop else ContentScale.Fit,
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
private fun TimelineSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val fraction = if (dragFraction >= 0f) dragFraction else if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Canvas(
            modifier = Modifier.fillMaxWidth().height(22.dp)
                .pointerInput(durationMs, enabled) {
                    if (enabled && durationMs > 0L) detectTapGestures { point ->
                        onSeek((point.x / size.width).coerceIn(0f, 1f).times(durationMs).toLong())
                    }
                }
                .pointerInput(durationMs, enabled) {
                    if (enabled && durationMs > 0L) detectDragGestures(
                        onDragStart = { point -> dragFraction = (point.x / size.width).coerceIn(0f, 1f) },
                        onDrag = { change, _ -> change.consume(); dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { if (dragFraction >= 0f) onSeek((dragFraction * durationMs).toLong()); dragFraction = -1f },
                        onDragCancel = { dragFraction = -1f }
                    )
                }
        ) {
            val cy = size.height / 2f
            val inset = 6.dp.toPx()
            val end = size.width - inset
            val x = inset + (end - inset) * fraction
            drawLine(StudioLine, Offset(inset, cy), Offset(end, cy), strokeWidth = 3.dp.toPx())
            drawLine(if (enabled) StudioAccent else StudioMuted, Offset(inset, cy), Offset(x, cy), strokeWidth = 3.dp.toPx())
            drawCircle(StudioBlack, 6.dp.toPx(), Offset(x, cy))
            drawCircle(if (enabled) StudioAccent else StudioMuted, 5.dp.toPx(), Offset(x, cy), style = Stroke(width = 1.8.dp.toPx()))
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
    val normalized = value.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = StudioWhite, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = StudioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(resetValue) {
                    detectTapGestures(
                        onTap = { point ->
                            val next = (point.x / size.width).coerceIn(0f, 1f)
                            onValueChange(next)
                        },
                        onDoubleTap = { onValueChange(resetValue.coerceIn(0f, 1f)) }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            val cy = size.height / 2f
            val left = 6.dp.toPx()
            val right = size.width - 6.dp.toPx()
            val thumbX = left + (right - left) * normalized
            drawLine(StudioLine, Offset(left, cy), Offset(right, cy), strokeWidth = 3.dp.toPx())
            drawLine(StudioAccent.copy(alpha = 0.72f), Offset(left, cy), Offset(thumbX, cy), strokeWidth = 3.dp.toPx())
            drawCircle(StudioBlack, 6.dp.toPx(), Offset(thumbX, cy))
            drawCircle(StudioAccent, 5.3.dp.toPx(), Offset(thumbX, cy), style = Stroke(width = 1.8.dp.toPx()))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(startLabel, color = StudioMuted, fontSize = 7.sp)
            Spacer(Modifier.weight(1f))
            Text("DOUBLE-TAP TO RESET", color = StudioMuted.copy(alpha = 0.55f), fontSize = 6.sp)
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
    val rgbSplitAmount: Float = 0f,
    val imageFitMode: String = "KEEP_ASPECT_RATIO",
    val imageSourceMode: String = "GENERATED_ALBUM_ART",
    val blurIntensity: Float = 0.15f,
    val particleTransparency: Float = 1f,
    val particleColor: Long = 0xFFFFFFFF,
    val particleVectorAngle: Float = 0f,
    val barsSpeed: Float = 1f,
    val beatAmount: Float = 0.5f,
    val rgbBeatTrigger: Boolean = true,
    val motionBlurTrailing: Float = 0.2f,
    val compositionBlendMode: String = "NORMAL",
    val shaderSource: String = "// GLSL ES 2.0 fragment shader\nvoid main() { gl_FragColor = vec4(1.0); }",
    val songTitle: String = "%title%",
    val artistName: String = "%artist%"
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
