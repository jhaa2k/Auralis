package com.auralis.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auralis.app.audio.AudioSessionBridge
import com.auralis.app.export.VideoExportConfig
import com.auralis.app.export.VisualizerVideoExporter
import com.auralis.app.playback.AuralisPlaybackService
import com.auralis.app.visualizer.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val EditorBackground = Color(0xFF101116)
private val EditorPanel = Color(0xFF181A21)
private val EditorRaised = Color(0xFF20232D)
private val EditorLine = Color(0xFF2B2E38)
private val EditorText = Color(0xFFF2F3F7)
private val EditorMuted = Color(0xFF9298A6)
private val EditorAccent = Color(0xFFFF9A6B)
private val EditorSuccess = Color(0xFF75C99B)

private enum class EditorTab(val label: String, val icon: ImageVector) {
    STUDIO("Studio", Icons.Outlined.GraphicEq),
    ELEMENTS("Layers", Icons.Outlined.Layers),
    AUDIO("Audio", Icons.Outlined.AudioFile),
    EXPORT("Export", Icons.Outlined.FileDownload)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleEditorScreen(store: ModuleSettingsStore = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(EditorTab.STUDIO) }
    var selectedModuleId by remember { mutableStateOf("BARS") }
    var layers by remember { mutableStateOf(listOf(SavedLayerRecord(1, "BARS", "Bars 1"))) }
    var layersLoaded by remember { mutableStateOf(false) }
    var selectedLayerId by remember { mutableIntStateOf(1) }
    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId } ?: layers.first()
    val layerSettingsKey = if (selectedLayer.id == 1 && selectedLayer.type == "BARS") "BARS"
        else "layer_${selectedLayer.id}_${selectedLayer.type}"
    val values by store.values.collectAsState()
    val module = ModuleCatalog.modules.firstOrNull { it.id == selectedLayer.type }
        ?: ModuleCatalog.modules.first()
    LaunchedEffect(context) {
        val restored = runCatching { restoreLayerRegistry(context) }.getOrDefault(emptyList())
        if (restored.isNotEmpty()) {
            layers = restored
            selectedLayerId = restored.first().id
            selectedModuleId = restored.first().type
        }
        layersLoaded = true
    }
    LaunchedEffect(layersLoaded, layers) {
        if (layersLoaded) runCatching { persistLayerRegistry(context, layers) }
    }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioName by remember { mutableStateOf("No audio selected") }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var audioStatus by remember { mutableStateOf("PLAYER READY") }
    var previewAspectRatio by remember { mutableStateOf("16:9") }
    var expandedGroup by remember(selectedModuleId) { mutableStateOf<String?>("General") }
    var showModuleInfo by remember { mutableStateOf(false) }

    var exportResolution by remember { mutableStateOf("1080p") }
    var exportFps by remember { mutableIntStateOf(30) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatus by remember { mutableStateOf("READY TO EXPORT") }
    var isExporting by remember { mutableStateOf(false) }
    var currentLayerSettings by remember { mutableStateOf(listOf(VisualizerSettings())) }

    val audioSessionId by AudioSessionBridge.sessionId.collectAsState()
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) audioStatus = "AUDIO VISUALIZER PERMISSION DENIED"
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedAudioUri = uri
            selectedAudioName = queryAudioName(context, uri)
            audioStatus = "LOADING AUDIO"
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { destination: Uri? ->
        val source = selectedAudioUri
        if (destination != null && source != null) {
            scope.launch {
                isExporting = true
                exportProgress = 0f
                exportStatus = "STARTING EXPORT"
                val dimensions = exportDimensions(exportResolution, previewAspectRatio)
                val result = runCatching {
                    VisualizerVideoExporter.export(
                        context = context,
                        audio = source,
                        destination = destination,
                        config = VideoExportConfig(
                            width = dimensions.first,
                            height = dimensions.second,
                            fps = exportFps,
                            includeAudio = true,
                            visualizerLayers = currentLayerSettings
                        )
                    ) { progress, message ->
                        scope.launch {
                            exportProgress = progress.coerceIn(0f, 1f)
                            exportStatus = message
                        }
                    }
                }
                isExporting = false
                result.onSuccess {
                    exportProgress = 1f
                    exportStatus = "MP4 SAVED SUCCESSFULLY"
                }.onFailure { error ->
                    exportStatus = "EXPORT FAILED: ${error.message ?: "UNKNOWN ERROR"}"
                }
            }
        }
    }

    DisposableEffect(context) {
        val token = SessionToken(
            context,
            ComponentName(context, AuralisPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        var active = true
        future.addListener(
            {
                if (active) {
                    runCatching { future.get() }
                        .onSuccess { mediaController = it }
                        .onFailure { audioStatus = "PLAYER CONNECTION FAILED" }
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

    LaunchedEffect(mediaController, selectedAudioUri) {
        val player = mediaController
        val uri = selectedAudioUri
        if (player != null && uri != null) {
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(selectedAudioName)
                        .build()
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.play()
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
                    audioStatus = if (playing) "PLAYING" else "PAUSED"
                }

                override fun onPlaybackStateChanged(state: Int) {
                    playbackDuration = player.duration.coerceAtLeast(0L)
                    if (state == Player.STATE_BUFFERING) audioStatus = "BUFFERING"
                    if (state == Player.STATE_READY && !player.isPlaying) audioStatus = "AUDIO READY"
                    if (state == Player.STATE_ENDED) audioStatus = "PLAYBACK COMPLETE"
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    isPlaying = false
                    audioStatus = "PLAYBACK ERROR: ${error.errorCodeName}"
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    LaunchedEffect(mediaController, isPlaying) {
        while (isPlaying && mediaController != null) {
            playbackPosition = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
            playbackDuration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            delay(250)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var previewSurface by remember { mutableStateOf<VisualizerSurface?>(null) }
    val capture = remember {
        VisualizerCapture(
            onSpectrum = { spectrum ->
                previewSurface?.submitSpectrum(spectrum)
            }
        )
    }

    LaunchedEffect(values, layers) {
        val provider = layers.firstOrNull { it.type == "AUDIOPROVIDER" }
        if (provider == null) {
            capture.configure(0.35f, 1f, 0f, 1f)
        } else {
            val key = "layer_${provider.id}_${provider.type}"
            fun read(name: String, fallback: Float) = (values["$key.$name"] ?: values["AUDIOPROVIDER.$name"])?.toFloatOrNull() ?: fallback
            val lowHz = read("lowerHz", 20f).coerceIn(20f, 19000f)
            val highHz = read("higherHz", 18000f).coerceIn(lowHz + 1f, 20000f)
            val frequencyRange = kotlin.math.ln(1000f)
            val lowFraction = (kotlin.math.ln(lowHz / 20f) / frequencyRange).coerceIn(0f, 0.95f)
            val highFraction = (kotlin.math.ln(highHz / 20f) / frequencyRange).coerceIn(lowFraction + 0.05f, 1f)
            capture.configure(
                smoothing = read("smooth", 0.35f).coerceIn(0f, 0.95f),
                gain = (read("volume", 1f) * read("preamp", 1f)).coerceIn(0.1f, 4f),
                lowFrequencyFraction = lowFraction,
                highFrequencyFraction = highFraction
            )
        }
    }
    LaunchedEffect(audioSessionId, hasAudioPermission) {
        capture.stop()
        if (hasAudioPermission && audioSessionId > 0) {
            capture.start(audioSessionId)
        }
    }
    DisposableEffect(capture) {
        onDispose { capture.stop() }
    }

    fun settingsForLayer(layer: SavedLayerRecord): VisualizerSettings? {
        val spec = ModuleCatalog.modules.firstOrNull { it.id == layer.type } ?: return null
        val key = if (layer.id == 1 && layer.type == "BARS") "BARS" else "layer_${layer.id}_${layer.type}"
        fun raw(name: String, fallback: String): String =
            values["$key.$name"] ?: values["${layer.type}.$name"]
                ?: spec.groups.flatMap { it.settings }.firstOrNull { it.key == name }?.defaultValue?.takeIf { it.isNotBlank() }
                ?: fallback
        fun number(name: String, fallback: Float): Float = raw(name, fallback.toString()).toFloatOrNull() ?: fallback
        fun flag(name: String, fallback: Boolean = false): Boolean = raw(name, fallback.toString()).toBooleanStrictOrNull() ?: fallback
        fun argb(name: String, fallback: Long): Long = runCatching {
            android.graphics.Color.parseColor(raw(name, String.format("#%08X", fallback))) .toLong() and 0xFFFFFFFFL
        }.getOrDefault(fallback)
        val visible = flag("visible", true)
        val measurePosition = MeasureWhat.fromLabel(raw("measurePos", "Nothing"))
        val measureScale = MeasureWhat.fromLabel(raw("measureScale", "Nothing"))
        val measureRotation = MeasureWhat.fromLabel(raw("measureRot", "Nothing"))
        val mirrorMode = layers.firstOrNull { it.type == "MIRROREREFFECT" }?.let { effect ->
            val effectKey = "layer_${effect.id}_${effect.type}"
            val enabled = (values["$effectKey.visible"] ?: "true").toBooleanStrictOrNull() ?: true
            if (enabled) values["$effectKey.mirrorMode"] ?: "Horizontal" else "None"
        } ?: "None"
        val globalMirrorX = mirrorMode in listOf("Vertical", "Both", "Kaleidoscope")
        val globalMirrorY = mirrorMode in listOf("Horizontal", "Both", "Kaleidoscope")
        val rgbAmount = layers.firstOrNull { it.type == "RGBSPLITEFFECT" }?.let { effect ->
            val effectKey = "layer_${effect.id}_${effect.type}"
            val enabled = (values["$effectKey.visible"] ?: "true").toBooleanStrictOrNull() ?: true
            if (enabled) (values["$effectKey.splitAmount"] ?: values["RGBSPLITEFFECT.splitAmount"])?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f else 0f
        } ?: 0f
        val blurAmount = layers.firstOrNull { it.type == "BLUREFFECT" }?.let { effect ->
            val effectKey = "layer_${effect.id}_${effect.type}"
            val enabled = (values["$effectKey.visible"] ?: "true").toBooleanStrictOrNull() ?: true
            if (enabled) {
                val radius = (values["$effectKey.blurRadius"] ?: "2").toFloatOrNull() ?: 2f
                val multiplier = (values["$effectKey.blurMultiplier"] ?: "4.1").toFloatOrNull() ?: 4.1f
                (radius * multiplier / 100f).coerceIn(0f, 1f)
            } else 0f
        } ?: 0f
        val motionBlurAmount = layers.firstOrNull { it.type == "MOTIONBLUREFFECT" }?.let { effect ->
            val effectKey = "layer_${effect.id}_${effect.type}"
            val enabled = (values["$effectKey.visible"] ?: "true").toBooleanStrictOrNull() ?: true
            if (enabled) (values["$effectKey.blurAmount"] ?: "0.2").toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f else 0f
        } ?: 0f
        return when (layer.type) {
            "BARS" -> {
                val segments = (1..3).count { flag("segment${it}Enabled", it == 1) }.coerceAtLeast(1)
                VisualizerSettings(
                    barCount = number("barCount", 64f).toInt().coerceIn(16, 128),
                    barWidth = number("barWidth", 0.72f).coerceIn(0.15f, 1f),
                    barHeight = number("heightScale", 1f).coerceIn(0.1f, 2f),
                    barMinHeight = number("minHeightScale", 0f).coerceIn(0f, 1f),
                    flipInput = flag("flipInput"),
                    barColorFrom = argb("segment1ColorFrom", 0xFFFFFFFFL),
                    barColorTo = argb("segment1ColorTo", 0xFF00FFFFL),
                    segmentCount = segments,
                    segmentGap = number("barSpacing", 0.18f).coerceIn(0f, 0.45f),
                    sensitivity = number("maxHeightScale", 1f).coerceIn(0.1f, 4f),
                    smoothing = number("softness", 0.35f).coerceIn(0f, 0.95f),
                    opacity = number("alpha", 1f).coerceIn(0f, 1f),
                    glow = blurAmount.coerceIn(0f, 1f),
                    motionBlurAmount = motionBlurAmount,
                    positionX = number("positionX", 0.5f),
                    positionY = number("positionY", 0.5f),
                    scale = ((number("scaleX", 1f) + number("scaleY", 1f)) / 2f).coerceIn(0.1f, 3f),
                    rotation = number("rotation", 0f),
                    mirror = flag("mirrorY") || globalMirrorY,
                    mirrorX = flag("mirrorX") || globalMirrorX,
                    positionMeasure = measurePosition,
                    scaleMeasure = measureScale,
                    rotationMeasure = measureRotation,
                    measureAmount = number("measureAmount", 0.5f),
                    beatSensitivity = number("beatRangeValueLower", 0.55f).coerceIn(0f, 1f),
                    rgbSplitAmount = rgbAmount,
                    barsEnabled = visible
                )
            }
            "PARTICLES" -> VisualizerSettings(
                positionX = number("positionX", 0.5f),
                positionY = number("positionY", 0.5f),
                particlesEnabled = visible,
                particleCount = number("countLimit", 120f).toInt().coerceIn(0, 400),
                particleSpeed = number("speed", 1f).coerceIn(0.1f, 4f),
                particleSize = number("startSize", 3f).coerceIn(1f, 12f),
                particleSpread = number("amountX", 0.8f).coerceIn(0.1f, 2f),
                particleMirrorX = flag("mirrorX") || globalMirrorX,
                particleMirrorY = flag("mirrorY") || globalMirrorY,
                particleRotation = number("vectorAngle", 0f),
                particleOpacity = number("opacityStrength", 1f).coerceIn(0f, 1f),
                particleColor = argb("color", 0xFFFFFFFFL),
                particlePositionX = number("positionX", 0.5f),
                particlePositionY = number("positionY", 0.5f),
                particleScale = number("particleScale", 1f).coerceIn(0.1f, 3f),
                particlePositionMeasure = measurePosition,
                particleScaleMeasure = measureScale,
                particleRotationMeasure = measureRotation,
                particleMeasureAmount = number("measureAmount", 0.5f),
                beatSensitivity = number("beatRangeValueLower", 0.55f).coerceIn(0f, 1f)
            )
            else -> null
        }
    }
    val liveLayerSettings = remember(values, layers, playbackPosition, playbackDuration) {
        layers.mapNotNull { settingsForLayer(it) }.map { config ->
            config.copy(trackPositionFraction = if (playbackDuration > 0L) {
                (playbackPosition.toFloat() / playbackDuration).coerceIn(0f, 1f)
            } else 0f).sanitized()
        }
    }
    val liveSettings = liveLayerSettings.firstOrNull() ?: VisualizerSettings(barsEnabled = false)
    SideEffect {
        currentLayerSettings = liveLayerSettings
        previewSurface?.updateLayers(liveLayerSettings)
    }
    val pageTitle = when (selectedTab) {
        EditorTab.STUDIO -> "LIVE VISUALIZER EDITOR"
        EditorTab.ELEMENTS -> "ADD OR EDIT ELEMENTS"
        EditorTab.AUDIO -> "MUSIC SOURCE & PLAYBACK"
        EditorTab.EXPORT -> "VIDEO OUTPUT SETTINGS"
    }

    Scaffold(
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            "AURALIS",
                            color = EditorText,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.6.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            pageTitle,
                            color = EditorMuted,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.65.sp
                        )
                    }
                },
                actions = {
                    if (selectedTab == EditorTab.STUDIO) {
                        IconButton(onClick = { store.reset(layerSettingsKey, module.groups) }) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Reset selected module",
                                tint = EditorText
                            )
                        }
                        IconButton(onClick = { showModuleInfo = true }) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Module information",
                                tint = EditorText
                            )
                        }
                    } else if (selectedTab == EditorTab.AUDIO) {
                        IconButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                            Icon(
                                Icons.Outlined.AudioFile,
                                contentDescription = "Import audio",
                                tint = EditorAccent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorBackground,
                    titleContentColor = EditorText,
                    actionIconContentColor = EditorText
                )
            )
        },
        bottomBar = {
            EditorBottomNavigation(
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .background(EditorBackground)
        ) {
            PreviewPane(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f),
                aspectRatio = previewAspectRatio,
                onAspectRatioChange = { previewAspectRatio = it },
                audioStatus = audioStatus,
                hasAudio = selectedAudioUri != null,
                audioSessionActive = audioSessionId > 0,
                previewSurface = { previewSurface = it },
                layerSettings = liveLayerSettings,
                layers = layers,
                values = values,
                trackName = selectedAudioName
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .background(EditorBackground)
            ) {
                if (selectedTab != EditorTab.AUDIO) {
                    PlaybackStrip(
                        trackName = selectedAudioName,
                        status = audioStatus,
                        position = playbackPosition,
                        duration = playbackDuration,
                        isPlaying = isPlaying,
                        hasTrack = selectedAudioUri != null,
                        onPlayPause = {
                            mediaController?.let { player ->
                                if (player.isPlaying) player.pause()
                                else if (player.mediaItemCount > 0) player.play()
                            }
                        },
                        onImport = { audioPicker.launch(arrayOf("audio/*")) }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        EditorTab.STUDIO -> StudioPropertiesPanel(
                            module = module,
                            settingsKey = layerSettingsKey,
                            store = store,
                            onReset = { store.reset(layerSettingsKey, module.groups) },
                            onAction = { action ->
                                when (action.key) {
                                    "compileApply" -> {
                                        val vertexSpec = module.groups.flatMap { it.settings }.firstOrNull { it.key == "vertexShader" }
                                        val fragmentSpec = module.groups.flatMap { it.settings }.firstOrNull { it.key == "fragmentShader" }
                                        if (vertexSpec != null && fragmentSpec != null && previewSurface != null) {
                                            previewSurface?.applyCustomShader(
                                                store.value(layerSettingsKey, vertexSpec),
                                                store.value(layerSettingsKey, fragmentSpec)
                                            ) { success, error ->
                                                audioStatus = if (success) "CUSTOM SHADER APPLIED" else "SHADER ERROR: ${error ?: "INVALID GLSL"}"
                                            }
                                        } else audioStatus = "PREVIEW NOT READY"
                                    }
                                    "addVariable" -> {
                                        val specs = module.groups.flatMap { it.settings }
                                        val typeSpec = specs.firstOrNull { it.key == "newVariable" }
                                        val mappingSpec = specs.firstOrNull { it.key == "variableMappings" }
                                        if (typeSpec != null && mappingSpec != null) {
                                            val type = store.value(layerSettingsKey, typeSpec)
                                            if (type != "None") {
                                                val current = store.value(layerSettingsKey, mappingSpec)
                                                val nextIndex = current.lineSequence().count { it.isNotBlank() } + 1
                                                store.update(layerSettingsKey, mappingSpec, (current + "\nvar$nextIndex:$type").trim())
                                            }
                                        }
                                    }
                                }
                            },
                            expandedGroup = expandedGroup,
                            onExpandedGroupChange = { expandedGroup = it }
                        )
                        EditorTab.ELEMENTS -> LayersPanel(
                            layers = layers,
                            selectedLayerId = selectedLayerId,
                            values = values,
                            onSelect = { layer ->
                                selectedLayerId = layer.id
                                selectedModuleId = layer.type
                                selectedTab = EditorTab.STUDIO
                            },
                            onAdd = { type ->
                                val spec = ModuleCatalog.modules.firstOrNull { it.id == type }
                                if (spec != null) {
                                    val nextId = (layers.maxOfOrNull { it.id } ?: 0) + 1
                                    val sameTypeCount = layers.count { it.type == type } + 1
                                    val newLayer = SavedLayerRecord(nextId, type, "${spec.title} $sameTypeCount")
                                    layers = layers + newLayer
                                    selectedLayerId = nextId
                                    selectedModuleId = type
                                    selectedTab = EditorTab.STUDIO
                                }
                            },
                            onMove = { layerId, direction ->
                                val index = layers.indexOfFirst { it.id == layerId }
                                val target = index + direction
                                if (index >= 0 && target in layers.indices) {
                                    val reordered = layers.toMutableList()
                                    val item = reordered.removeAt(index)
                                    reordered.add(target, item)
                                    layers = reordered
                                }
                            },
                            onToggleVisibility = { layerId ->
                                val layer = layers.firstOrNull { it.id == layerId }
                                if (layer != null) {
                                    val key = if (layer.id == 1 && layer.type == "BARS") "BARS" else "layer_${layer.id}_${layer.type}"
                                    val visible = values["$key.visible"]?.toBooleanStrictOrNull() ?: true
                                    store.update(key, SettingSpec("visible", "Visible", SettingKind.CHECKBOX, defaultValue = "true"), (!visible).toString())
                                }
                            },
                            onRemove = { layerId ->
                                if (layers.size > 1) {
                                    val updated = layers.filterNot { it.id == layerId }
                                    layers = updated
                                    if (selectedLayerId == layerId) {
                                        selectedLayerId = updated.first().id
                                        selectedModuleId = updated.first().type
                                    }
                                }
                            }
                        )
                        EditorTab.AUDIO -> AudioPanel(
                            trackName = selectedAudioName,
                            status = audioStatus,
                            position = playbackPosition,
                            duration = playbackDuration,
                            isPlaying = isPlaying,
                            hasTrack = selectedAudioUri != null,
                            onImport = { audioPicker.launch(arrayOf("audio/*")) },
                            onPlayPause = {
                                mediaController?.let { player ->
                                    if (player.isPlaying) player.pause()
                                    else if (player.mediaItemCount > 0) player.play()
                                }
                            },
                            onSeek = { fraction ->
                                val target = (playbackDuration * fraction).toLong()
                                mediaController?.seekTo(target)
                                playbackPosition = target
                            }
                        )
                        EditorTab.EXPORT -> ExportPanel(
                            aspectRatio = previewAspectRatio,
                            onAspectRatioChange = { previewAspectRatio = it },
                            resolution = exportResolution,
                            onResolutionChange = { exportResolution = it },
                            fps = exportFps,
                            onFpsChange = { exportFps = it },
                            progress = exportProgress,
                            status = exportStatus,
                            isExporting = isExporting,
                            hasAudio = selectedAudioUri != null,
                            onExport = {
                                if (selectedAudioUri == null) {
                                    exportStatus = "IMPORT AN AUDIO TRACK FIRST"
                                } else if (!isExporting) {
                                    exportPicker.launch("Auralis-Visualizer.mp4")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showModuleInfo) {
        AlertDialog(
            onDismissRequest = { showModuleInfo = false },
            title = { Text(module.title) },
            text = {
                Text(
                    "This inspector exposes the module's full settings catalog. " +
                        "Settings are saved locally. Only controls connected to the active OpenGL renderer " +
                        "affect the live canvas; advanced effect controls remain stored until their renderer is integrated."
                )
            },
            confirmButton = {
                TextButton(onClick = { showModuleInfo = false }) { Text("GOT IT") }
            },
            containerColor = EditorPanel,
            titleContentColor = EditorText,
            textContentColor = EditorMuted
        )
    }
}

@Composable
private fun EditorBottomNavigation(
    selected: EditorTab,
    onSelect: (EditorTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorPanel)
            .navigationBarsPadding()
            .height(62.dp)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        EditorTab.values().forEach { tab ->
            val active = selected == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(tab) }
                    .padding(top = 4.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = if (active) EditorAccent.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(width = 54.dp, height = 30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (active) EditorAccent else EditorMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Text(
                    tab.label.uppercase(),
                    color = if (active) EditorAccent else EditorMuted,
                    fontSize = 8.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

@Composable
private fun PreviewPane(
    modifier: Modifier,
    aspectRatio: String,
    onAspectRatioChange: (String) -> Unit,
    audioStatus: String,
    hasAudio: Boolean,
    audioSessionActive: Boolean,
    previewSurface: (VisualizerSurface) -> Unit,
    layerSettings: List<VisualizerSettings>,
    layers: List<SavedLayerRecord>,
    values: Map<String, String>,
    trackName: String
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0B0D12))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(23.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (audioSessionActive) EditorSuccess else EditorMuted)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "CANVAS",
                color = EditorText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                audioStatus.uppercase(),
                color = if (audioSessionActive) EditorSuccess else EditorMuted,
                fontSize = 8.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(7.dp))
            AspectRatioMenu(aspectRatio, onAspectRatioChange)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val ratio = aspectRatio.toAspectRatio()
            val stageWidth = minOf(maxWidth, maxHeight * ratio)
            val stageHeight = stageWidth / ratio

            Box(
                modifier = Modifier
                    .width(stageWidth)
                    .height(stageHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { viewContext ->
                        VisualizerSurface(viewContext).also {
                            previewSurface(it)
                            it.updateLayers(layerSettings)
                        }
                    },
                    update = {
                        previewSurface(it)
                        it.updateLayers(layerSettings)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                layers.forEach { layer ->
                    when (layer.type) {
                        "TEXT" -> TextLayerOverlay(layer, values, trackName)
                        "IMAGE" -> ImageLayerOverlay(layer, values)
                    }
                }

                if (!hasAudio) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = EditorAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "YOUR VISUALIZER CANVAS",
                            color = EditorText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            "Import a track to activate audio reaction",
                            color = EditorMuted,
                            fontSize = 8.sp
                        )
                    }
                } else if (!audioSessionActive) {
                    Text(
                        "WAITING FOR AUDIO SESSION",
                        color = EditorMuted,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TextLayerOverlay(layer: SavedLayerRecord, values: Map<String, String>, trackName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = "layer_${layer.id}_${layer.type}"
    fun value(name: String, fallback: String) = values["$key.$name"] ?: values["TEXT.$name"] ?: fallback
    if (value("visible", "true").toBooleanStrictOrNull() == false) return
    val fontUri = value("fontFamily", "")
    val typeface by produceState<android.graphics.Typeface?>(initialValue = null, fontUri) {
        value = if (fontUri.isBlank()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.contentResolver.openFileDescriptor(Uri.parse(fontUri), "r")?.use { android.graphics.Typeface.Builder(it.fileDescriptor).build() } }.getOrNull()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val x = value("positionX", "0.5").toFloatOrNull() ?: 0.5f
        val y = value("positionY", "0.5").toFloatOrNull() ?: 0.5f
        val sx = value("scaleX", "1").toFloatOrNull() ?: 1f
        val sy = value("scaleY", "1").toFloatOrNull() ?: 1f
        val rotation = value("rotation", "0").toFloatOrNull() ?: 0f
        val size = value("fontSize", "30").toFloatOrNull()?.coerceIn(6f, 300f) ?: 30f
        val color = runCatching { Color(android.graphics.Color.parseColor(value("textColor", "#FFFFFFFF"))) }.getOrDefault(Color.White)
        val opacity = (value("opacityStrength", "1").toFloatOrNull() ?: 1f).coerceIn(0f, 1f)
        val text = value("textInput", "{ArtistOrTitle}").replace("{ArtistOrTitle}", trackName.substringBeforeLast('.', trackName)).replace("%title%", trackName)
        val alignment = when (value("alignment", "Center")) {
            "Left" -> TextAlign.Start
            "Right" -> TextAlign.End
            "Justify" -> TextAlign.Justify
            else -> TextAlign.Center
        }
        Box(
            modifier = Modifier.align(Alignment.Center).offset {
                IntOffset(((x - 0.5f) * constraints.maxWidth).toInt(), ((y - 0.5f) * constraints.maxHeight).toInt())
            }.graphicsLayer(scaleX = sx.coerceIn(0.05f, 8f), scaleY = sy.coerceIn(0.05f, 8f), rotationZ = rotation, alpha = opacity),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text, color = color, fontSize = size.sp,
                fontFamily = typeface?.let { FontFamily(it) } ?: FontFamily.Default,
                textAlign = alignment,
                letterSpacing = (value("letterSpacing", "0").toFloatOrNull() ?: 0f).sp,
                fontWeight = FontWeight(value("fontWeight", "400").toIntOrNull() ?: 400)
            )
        }
    }
}
@Composable
private fun ImageLayerOverlay(layer: SavedLayerRecord, values: Map<String, String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = "layer_${layer.id}_${layer.type}"
    val uriString = values["$key.pickImage"] ?: values["IMAGE.pickImage"] ?: values["$key.fallbackImage"]
    val visible = (values["$key.visible"] ?: values["IMAGE.visible"] ?: "true").toBooleanStrictOrNull() ?: true
    if (!visible || uriString.isNullOrBlank()) return
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uriString) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(Uri.parse(uriString))?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    if (bitmap == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val x = (values["$key.positionX"] ?: values["IMAGE.positionX"])?.toFloatOrNull() ?: 0.5f
        val y = (values["$key.positionY"] ?: values["IMAGE.positionY"])?.toFloatOrNull() ?: 0.5f
        val sx = (values["$key.scaleX"] ?: values["IMAGE.scaleX"])?.toFloatOrNull() ?: 1f
        val sy = (values["$key.scaleY"] ?: values["IMAGE.scaleY"])?.toFloatOrNull() ?: 1f
        val rotation = (values["$key.rotation"] ?: values["IMAGE.rotation"])?.toFloatOrNull() ?: 0f
        val opacity = (values["$key.opacityStrength"] ?: values["IMAGE.opacityStrength"])?.toFloatOrNull() ?: 1f
        val brightness = (values["$key.brightness"] ?: values["IMAGE.brightness"])?.toFloatOrNull() ?: 0f
        val contrast = ((values["$key.contrast"] ?: values["IMAGE.contrast"])?.toFloatOrNull() ?: 1f).coerceIn(0f, 3f)
        val saturation = ((values["$key.saturation"] ?: values["IMAGE.saturation"])?.toFloatOrNull() ?: 1f).coerceIn(0f, 3f)
        val offset = 0.5f * (1f - contrast) + brightness
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast * (0.213f + 0.787f * saturation), contrast * (0.715f - 0.715f * saturation), contrast * (0.072f - 0.072f * saturation), 0f, offset,
            contrast * (0.213f - 0.213f * saturation), contrast * (0.715f + 0.285f * saturation), contrast * (0.072f - 0.072f * saturation), 0f, offset,
            contrast * (0.213f - 0.213f * saturation), contrast * (0.715f - 0.715f * saturation), contrast * (0.072f + 0.928f * saturation), 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        Box(
            modifier = Modifier.align(Alignment.Center).offset {
                IntOffset(((x - 0.5f) * constraints.maxWidth).toInt(), ((y - 0.5f) * constraints.maxHeight).toInt())
            }.size(width = maxWidth * 0.42f, height = maxHeight * 0.42f)
                .graphicsLayer(scaleX = sx.coerceIn(0.05f, 8f), scaleY = sy.coerceIn(0.05f, 8f), rotationZ = rotation, alpha = opacity.coerceIn(0f, 1f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = layer.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = if ((values["$key.keepAspect"] ?: "true").toBooleanStrictOrNull() != false) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(colorMatrix)
            )
        }
    }
}
@Composable
private fun AspectRatioMenu(
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
        ) {
            Text(
                selected,
                color = EditorAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = EditorPanel
        ) {
            listOf("16:9", "9:16", "1:1", "4:3").forEach { ratio ->
                DropdownMenuItem(
                    text = { Text(ratio, color = EditorText, fontSize = 12.sp) },
                    onClick = {
                        onSelect(ratio)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaybackStrip(
    trackName: String,
    status: String,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    hasTrack: Boolean,
    onPlayPause: () -> Unit,
    onImport: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = EditorPanel,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, EditorLine)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.AudioFile,
                contentDescription = null,
                tint = EditorAccent,
                modifier = Modifier.size(19.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    trackName,
                    color = EditorText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (hasTrack) "${formatEditorTime(position)} / ${formatEditorTime(duration)} · ${status.uppercase()}"
                    else "IMPORT AUDIO TO START",
                    color = EditorMuted,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
            FilledTonalIconButton(
                onClick = onPlayPause,
                enabled = hasTrack,
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = EditorRaised,
                    contentColor = EditorAccent
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(
                onClick = onImport,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Outlined.AudioFile,
                    contentDescription = "Import audio",
                    tint = EditorText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StudioPropertiesPanel(
    module: ModuleSpec,
    settingsKey: String,
    store: ModuleSettingsStore,
    onReset: () -> Unit,
    onAction: (SettingSpec) -> Unit,
    expandedGroup: String?,
    onExpandedGroupChange: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(39.dp)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "PROPERTY INSPECTOR",
                    color = EditorMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
                Text(
                    module.title,
                    color = EditorText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("RESET MODULE", color = EditorAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(module.groups, key = { it.title }) { group ->
                val expanded = expandedGroup == group.title
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EditorPanel,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, EditorLine)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(49.dp)
                                .clickable {
                                    onExpandedGroupChange(
                                        if (expanded) null else group.title
                                    )
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    group.title,
                                    color = EditorText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${group.settings.size} CONTROLS",
                                    color = EditorMuted,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                if (expanded) "−" else "+",
                                color = EditorAccent,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                        if (expanded) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(EditorLine))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                group.settings.forEach { spec ->
                                    SettingControl(
                                        spec = spec,
                                        value = store.value(settingsKey, spec),
                                        onValueChange = {
                                            store.update(settingsKey, spec, it)
                                        },
                                        onAction = { onAction(spec) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersPanel(
    layers: List<SavedLayerRecord>,
    selectedLayerId: Int,
    values: Map<String, String>,
    onSelect: (SavedLayerRecord) -> Unit,
    onAdd: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(EditorBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("LAYERS", color = EditorText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text("${layers.size} COMPOSITION LAYERS", color = EditorMuted, fontSize = 8.sp)
            }
            Box {
                OutlinedButton(
                    onClick = { addMenuExpanded = true },
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp),
                    border = BorderStroke(1.dp, EditorAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EditorAccent)
                ) {
                    Icon(Icons.Outlined.Layers, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("ADD ELEMENT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                    modifier = Modifier.background(EditorPanel)
                ) {
                    ModuleCatalog.modules.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.title, color = EditorText, fontSize = 11.sp) },
                            onClick = {
                                addMenuExpanded = false
                                onAdd(item.id)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Layers, null, tint = EditorAccent, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(layers, key = { it.id }) { layer ->
                val spec = ModuleCatalog.modules.firstOrNull { it.id == layer.type }
                val active = layer.id == selectedLayerId
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(layer) },
                    color = if (active) EditorAccent.copy(alpha = 0.10f) else EditorPanel,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (active) EditorAccent else EditorLine)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Layers, null, tint = if (active) EditorAccent else EditorMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(layer.name, color = if (active) EditorAccent else EditorText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(spec?.title ?: layer.type, color = EditorMuted, fontSize = 8.sp, letterSpacing = 0.4.sp)
                        }
                        val layerKey = if (layer.id == 1 && layer.type == "BARS") "BARS" else "layer_${layer.id}_${layer.type}"
                        val visible = (values["$layerKey.visible"] ?: "true").toBooleanStrictOrNull() ?: true
                        TextButton(onClick = { onToggleVisibility(layer.id) }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                            Text(if (visible) "SHOW" else "HIDDEN", color = if (visible) EditorSuccess else EditorMuted, fontSize = 7.sp)
                        }
                        TextButton(onClick = { onMove(layer.id, -1) }, enabled = layers.indexOf(layer) > 0, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) { Text("↑", color = EditorText) }
                        TextButton(onClick = { onMove(layer.id, 1) }, enabled = layers.indexOf(layer) < layers.lastIndex, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) { Text("↓", color = EditorText) }
                        if (layers.size > 1) {
                            TextButton(
                                onClick = { onRemove(layer.id) },
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                            ) {
                                Text("REMOVE", color = EditorMuted, fontSize = 7.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun AudioPanel(
    trackName: String,
    status: String,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    hasTrack: Boolean,
    onImport: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "AUDIO SOURCE",
            color = EditorText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Text(
            "Use a track from your device. Auralis analyses the audio from its own player session.",
            color = EditorMuted,
            fontSize = 9.sp
        )
        Spacer(Modifier.height(9.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = EditorPanel,
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(1.dp, EditorLine)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        color = EditorRaised,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.AudioFile,
                                contentDescription = null,
                                tint = EditorAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            trackName,
                            color = EditorText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            status.uppercase(),
                            color = if (isPlaying) EditorSuccess else EditorMuted,
                            fontSize = 8.sp,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                ThinValueSlider(
                    value = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    valueRange = 0f..1f,
                    onValueChange = onSeek,
                    enabled = hasTrack && duration > 0L
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatEditorTime(position), color = EditorMuted, fontSize = 8.sp, modifier = Modifier.weight(1f))
                    Text(formatEditorTime(duration), color = EditorMuted, fontSize = 8.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, EditorLine),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EditorText),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Outlined.AudioFile, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasTrack) "CHANGE TRACK" else "IMPORT AUDIO", fontSize = 9.sp)
                    }
                    FilledIconButton(
                        onClick = onPlayPause,
                        enabled = hasTrack,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = EditorAccent,
                            contentColor = EditorBackground
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "AUDIO ANALYSIS",
            color = EditorText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(5.dp))
        AudioInfoRow("Capture source", "Auralis Media3 player session")
        AudioInfoRow("Analysis data", "Live FFT spectrum")
        AudioInfoRow("Current visualizer", "OpenGL ES spectrum renderer")
        AudioInfoRow("Background playback", "MediaSession service")
    }
}

@Composable
private fun AudioInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = EditorMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text(value, color = EditorText, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(EditorLine.copy(alpha = 0.7f)))
}

@Composable
private fun ExportPanel(
    aspectRatio: String,
    onAspectRatioChange: (String) -> Unit,
    resolution: String,
    onResolutionChange: (String) -> Unit,
    fps: Int,
    onFpsChange: (Int) -> Unit,
    progress: Float,
    status: String,
    isExporting: Boolean,
    hasAudio: Boolean,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(EditorPanel)
                .padding(12.dp)
                .then(Modifier),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "VIDEO EXPORT",
                color = EditorText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
            Text(
                "Choose a frame shape, output size and frame rate.",
                color = EditorMuted,
                fontSize = 9.sp
            )

            ExportChoiceRow(
                title = "ASPECT RATIO",
                options = listOf("16:9", "9:16", "1:1", "4:3"),
                selected = aspectRatio,
                onSelect = onAspectRatioChange
            )
            ExportChoiceRow(
                title = "RESOLUTION",
                options = listOf("720p", "1080p", "1440p", "2160p"),
                selected = resolution,
                onSelect = onResolutionChange
            )
            ExportChoiceRow(
                title = "FRAME RATE",
                options = listOf("25", "30", "50", "60"),
                selected = fps.toString(),
                onSelect = { onFpsChange(it.toInt()) },
                suffix = " FPS"
            )

            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SOURCE AUDIO", color = EditorMuted, fontSize = 8.sp, modifier = Modifier.weight(1f))
                Text(
                    if (hasAudio) "READY" else "NOT SELECTED",
                    color = if (hasAudio) EditorSuccess else EditorAccent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = EditorAccent,
                trackColor = EditorLine
            )
            Text(status, color = EditorMuted, fontSize = 8.sp, maxLines = 2)

            Button(
                onClick = onExport,
                enabled = hasAudio && !isExporting,
                modifier = Modifier.fillMaxWidth().height(43.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EditorAccent,
                    contentColor = EditorBackground,
                    disabledContainerColor = EditorRaised,
                    disabledContentColor = EditorMuted
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = EditorBackground,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    if (isExporting) "RENDERING VIDEO" else "RENDER MP4",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
            }
            Text(
                "Export currently renders the active spectrum-video pipeline with the selected audio track.",
                color = EditorMuted,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun ExportChoiceRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    suffix: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            color = EditorMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            options.forEach { option ->
                val active = option == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(31.dp)
                        .clickable { onSelect(option) },
                    shape = RoundedCornerShape(4.dp),
                    color = if (active) EditorAccent.copy(alpha = 0.16f) else EditorRaised,
                    border = BorderStroke(
                        1.dp,
                        if (active) EditorAccent else EditorLine
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            option + suffix,
                            color = if (active) EditorAccent else EditorText,
                            fontSize = 8.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingControl(
    spec: SettingSpec,
    value: String,
    onValueChange: (String) -> Unit,
    onAction: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onValueChange(uri.toString())
        }
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onValueChange(uri.toString())
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when (spec.kind) {
            SettingKind.CHECKBOX -> {
                Row(
                    modifier = Modifier.heightIn(min = 38.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        spec.label,
                        modifier = Modifier.weight(1f),
                        color = EditorText,
                        fontSize = 10.sp
                    )
                    Switch(
                        checked = value.toBooleanStrictOrNull() ?: false,
                        onCheckedChange = { onValueChange(it.toString()) },
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            SettingKind.SLIDER -> {
                val min = minOf(spec.min, spec.max)
                val max = maxOf(spec.min, spec.max).let { if (it == min) min + 1f else it }
                val numeric = value.toFloatOrNull()?.coerceIn(min, max) ?: min
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spec.label,
                        modifier = Modifier.weight(1f),
                        color = EditorText,
                        fontSize = 10.sp
                    )
                    Text(
                        formatSettingNumber(numeric),
                        color = EditorAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                ThinValueSlider(
                    value = numeric,
                    valueRange = min..max,
                    onValueChange = { onValueChange(it.toString()) }
                )
            }

            SettingKind.DROPDOWN -> {
                var expanded by remember(spec.key) { mutableStateOf(false) }
                Text(spec.label, color = EditorText, fontSize = 10.sp)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = value.ifBlank { spec.options.firstOrNull().orEmpty() },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        spec.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontSize = 11.sp) },
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            SettingKind.COLOR -> {
                Text(spec.label, color = EditorText, fontSize = 10.sp)
                OutlinedTextField(
                    value = value,
                    onValueChange = { typed ->
                        if (typed.matches(Regex("#?[0-9A-Fa-f]{0,8}"))) {
                            onValueChange(if (typed.startsWith("#")) typed else "#$typed")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("ARGB hex · #AARRGGBB", fontSize = 8.sp)
                    },
                    leadingIcon = {
                        val parsed = remember(value) {
                            runCatching {
                                val hex = value.removePrefix("#")
                                if (hex.length == 8) {
                                    // Catalog defaults use Android ARGB notation: #AARRGGBB.
                                    Color(
                                        alpha = hex.substring(0, 2).toInt(16) / 255f,
                                        red = hex.substring(2, 4).toInt(16) / 255f,
                                        green = hex.substring(4, 6).toInt(16) / 255f,
                                        blue = hex.substring(6, 8).toInt(16) / 255f
                                    )
                                } else {
                                    Color.Transparent
                                }
                            }.getOrDefault(Color.Transparent)
                        }
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(parsed)
                        )
                    }
                )
            }

            SettingKind.IMAGE_PICKER -> {
                Text(spec.label, color = EditorText, fontSize = 10.sp)
                OutlinedButton(
                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth().height(37.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, EditorLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EditorText),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(if (value.isBlank()) "CHOOSE IMAGE" else value.substringAfterLast('/').take(42), fontSize = 9.sp)
                }
            }

            SettingKind.FONT_PICKER -> {
                Text(spec.label, color = EditorText, fontSize = 10.sp)
                OutlinedButton(
                    onClick = { fontPicker.launch(arrayOf("font/*", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth().height(37.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, EditorLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EditorText),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(value.substringAfterLast('/').ifBlank { "CHOOSE FONT" }, fontSize = 9.sp)
                }
            }

            SettingKind.CODE, SettingKind.TEXT -> {
                Text(spec.label, color = EditorText, fontSize = 10.sp)
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (spec.kind == SettingKind.CODE) 5 else 1,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            SettingKind.NUMBER -> {
                Text(spec.label, color = EditorText, fontSize = 10.sp)
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.toFloatOrNull() != null) {
                            onValueChange(input)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            SettingKind.ACTION -> {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().height(37.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, EditorLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EditorAccent),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(spec.label.uppercase(), fontSize = 9.sp)
                }
            }
        }
    }
}


@Composable
private fun ThinValueSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val minValue = valueRange.start
    val maxValue = valueRange.endInclusive
    val currentValue = value.coerceIn(minValue, maxValue)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val insetPx = with(density) { 5.dp.toPx() }

    fun updateFromX(x: Float, width: Int) {
        val usableWidth = width.toFloat() - insetPx * 2f
        if (usableWidth <= 0f || maxValue <= minValue) return
        val fraction = ((x - insetPx) / usableWidth).coerceIn(0f, 1f)
        onValueChange(minValue + (maxValue - minValue) * fraction)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(27.dp)
            .pointerInput(enabled, minValue, maxValue, insetPx) {
                if (enabled) detectTapGestures { updateFromX(it.x, size.width) }
            }
            .pointerInput(enabled, minValue, maxValue, insetPx) {
                if (enabled) detectDragGestures(
                    onDragStart = { updateFromX(it.x, size.width) },
                    onDrag = { change, _ ->
                        updateFromX(change.position.x, size.width)
                        change.consume()
                    }
                )
            }
    ) {
        val inset = 5.dp.toPx()
        val trackStart = inset
        val trackEnd = size.width - inset
        val centerY = size.height / 2f
        val fraction = ((currentValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        val thumbX = trackStart + (trackEnd - trackStart) * fraction
        drawLine(
            color = if (enabled) EditorLine else EditorLine.copy(alpha = 0.45f),
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (enabled) EditorAccent else EditorMuted,
            start = Offset(trackStart, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = if (enabled) EditorAccent else EditorMuted,
            radius = 4.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}

private fun queryAudioName(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(0)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    } ?: "Selected audio"
}

private fun String.toAspectRatio(): Float = when (this) {
    "9:16" -> 9f / 16f
    "1:1" -> 1f
    "4:3" -> 4f / 3f
    else -> 16f / 9f
}

private fun exportDimensions(resolution: String, aspectRatio: String): Pair<Int, Int> {
    val longEdge = when (resolution) {
        "720p" -> 1280
        "1080p" -> 1920
        "1440p" -> 2560
        else -> 3840
    }
    val raw = when (aspectRatio) {
        "9:16" -> (longEdge * 9f / 16f).roundToInt() to longEdge
        "1:1" -> longEdge to longEdge
        "4:3" -> longEdge to (longEdge * 3f / 4f).roundToInt()
        else -> longEdge to (longEdge * 9f / 16f).roundToInt()
    }
    val upScale = maxOf(640f / raw.first, 480f / raw.second, 1f)
    val scaledWidth = (raw.first * upScale).roundToInt()
    val scaledHeight = (raw.second * upScale).roundToInt()
    val downScale = minOf(3840f / scaledWidth, 2160f / scaledHeight, 1f)
    return (scaledWidth * downScale).roundToInt() to
        (scaledHeight * downScale).roundToInt()
}

private fun formatSettingNumber(value: Float): String {
    return if (value == value.toInt().toFloat()) value.toInt().toString()
    else "%.2f".format(value)
}

private fun formatEditorTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
