package com.auralis.app.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auralis.app.audio.AudioSessionBridge
import com.auralis.app.playback.AuralisPlaybackService
import com.auralis.app.visualizer.*
import kotlinx.coroutines.delay

private val EditorBackground = Color(0xFF090B0E)
private val EditorPanel = Color(0xFF14181E)
private val EditorLine = Color(0xFF252B33)
private val EditorText = Color(0xFFF3F5F7)
private val EditorMuted = Color(0xFF929BA6)
private val EditorAccent = Color(0xFF53D8E8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleEditorScreen(store: ModuleSettingsStore = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedModuleId by remember { mutableStateOf<String?>(null) }
    val values by store.values.collectAsState()
    val module = ModuleCatalog.modules.firstOrNull { it.id == selectedModuleId }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var selectedAudioName by remember { mutableStateOf("No audio selected") }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var audioStatus by remember { mutableStateOf("PREVIEW READY") }
    val audioSessionId by AudioSessionBridge.sessionId.collectAsState()
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (!granted) audioStatus = "AUDIO VISUALIZER PERMISSION DENIED"
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedAudioName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "Selected audio"
            } ?: "Selected audio"
            mediaController?.apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                play()
            }
            audioStatus = "LOADING AUDIO"
        }
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, AuralisPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var active = true
        future.addListener({
            if (active) runCatching { future.get() }.onSuccess { mediaController = it }
                .onFailure { audioStatus = "PLAYER CONNECTION FAILED" }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            MediaController.releaseFuture(future)
            mediaController = null
        }
    }
    DisposableEffect(mediaController) {
        val player = mediaController
        if (player == null) onDispose { } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    audioStatus = if (playing) "LIVE AUDIO PREVIEW" else "PREVIEW PAUSED"
                }
                override fun onPlaybackStateChanged(state: Int) {
                    playbackDuration = player.duration.coerceAtLeast(0L)
                    if (state == Player.STATE_READY && !player.isPlaying) audioStatus = "AUDIO READY"
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
            delay(300)
        }
    }

    var previewSurface by remember { mutableStateOf<VisualizerSurface?>(null) }
    val capture = remember {
        VisualizerCapture(onSpectrum = { spectrum -> previewSurface?.submitSpectrum(spectrum) })
    }
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(audioSessionId, hasAudioPermission) {
        capture.stop()
        if (hasAudioPermission && audioSessionId > 0) capture.start(audioSessionId)
    }
    DisposableEffect(capture) { onDispose { capture.stop() } }

    val bars = ModuleCatalog.modules.first { it.id == "BARS" }
    fun setting(key: String, fallback: String): String =
        values["BARS.$key"] ?: bars.groups.flatMap { it.settings }.firstOrNull { it.key == key }?.defaultValue?.takeIf { it.isNotBlank() } ?: fallback
    val liveSettings = remember(values) {
        VisualizerSettings(
            barCount = setting("barCount", "64").toFloatOrNull()?.toInt()?.coerceIn(16, 128) ?: 64,
            barWidth = setting("barWidth", "0.72").toFloatOrNull()?.coerceIn(0.15f, 1f) ?: 0.72f,
            barHeight = setting("heightScale", "1").toFloatOrNull()?.coerceIn(0.1f, 2f) ?: 1f,
            segmentCount = (1..3).count { setting("segment${it}Enabled", if (it == 1) "true" else "false").toBoolean() }.coerceAtLeast(1),
            sensitivity = setting("maxHeightScale", "1").toFloatOrNull()?.coerceIn(0.1f, 4f) ?: 1f,
            particlesEnabled = values["PARTICLES.visible"]?.toBoolean() ?: false
        ).sanitized()
    }
    SideEffect { previewSurface?.updateSettings(liveSettings) }

    Scaffold(
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(module?.title ?: "AURALIS STUDIO", color = EditorText, fontWeight = FontWeight.Bold)
                        Text("LIVE EDITOR", color = EditorMuted, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    if (module != null) IconButton(onClick = { selectedModuleId = null }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = EditorText)
                    }
                },
                actions = {
                    if (module != null) {
                        IconButton(onClick = { store.reset(module.id, module.groups) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Reset module", tint = EditorText)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.Info, contentDescription = "Module information", tint = EditorText)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(EditorBackground)
        ) {
            // Fixed preview zone: it never scrolls and contains no floating property controls.
            Column(
                modifier = Modifier.fillMaxWidth().weight(0.34f).background(Color(0xFF050607)).padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE PREVIEW", color = EditorText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(audioStatus, color = EditorAccent, style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 5.dp).clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF0B1016))
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            VisualizerSurface(viewContext).also {
                                previewSurface = it
                                it.updateSettings(liveSettings)
                            }
                        },
                        update = {
                            previewSurface = it
                            it.updateSettings(liveSettings)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (audioSessionId <= 0) {
                        Text(
                            "IMPORT AUDIO TO START LIVE VISUALIZATION",
                            modifier = Modifier.align(Alignment.Center).padding(12.dp),
                            color = EditorMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = EditorAccent, modifier = Modifier.size(18.dp))
                    Text(selectedAudioName, color = EditorMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(formatEditorTime(playbackPosition) + " / " + formatEditorTime(playbackDuration), color = EditorMuted, style = MaterialTheme.typography.labelSmall)
                    FilledTonalIconButton(
                        onClick = { if (mediaController?.isPlaying == true) mediaController?.pause() else mediaController?.play() },
                        enabled = mediaController != null,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = "Play or pause")
                    }
                    TextButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text("IMPORT", color = EditorAccent) }
                }
            }

            // Independent bottom scroll region: only this area moves when browsing properties.
            Column(
                modifier = Modifier.fillMaxWidth().weight(0.66f).background(EditorBackground)
            ) {
                if (module == null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text("MODULE LIBRARY", color = EditorAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Select a module. Preview stays fixed above while these controls scroll.", color = EditorMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        items(ModuleCatalog.modules.chunked(2)) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { item ->
                                    Surface(
                                        modifier = Modifier.weight(1f).height(88.dp).clickable { selectedModuleId = item.id },
                                        shape = RoundedCornerShape(5.dp),
                                        color = EditorPanel,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EditorLine)
                                    ) {
                                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                            Text(item.title, color = EditorText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${item.groups.size} PROPERTY GROUPS", color = EditorMuted, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    var expandedGroup by remember(module.id) { mutableStateOf<String?>(null) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text("PROPERTY CONTROLS", color = EditorAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Scroll here to edit. Preview remains locked above.", color = EditorMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        items(module.groups, key = { it.title }) { group ->
                            val expanded = expandedGroup == group.title
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = EditorPanel,
                                border = androidx.compose.foundation.BorderStroke(1.dp, EditorLine),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { expandedGroup = if (expanded) null else group.title }
                                            .padding(horizontal = 14.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(group.title, color = EditorText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text("${group.settings.size} SETTINGS", color = EditorMuted, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text(if (expanded) "−" else "+", color = EditorAccent, style = MaterialTheme.typography.titleLarge)
                                    }
                                    if (expanded) {
                                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                            group.settings.forEach { spec ->
                                                SettingControl(
                                                    spec = spec,
                                                    value = store.value(module.id, spec),
                                                    onValueChange = { store.update(module.id, spec, it) }
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
        }
    }
}

private fun formatEditorTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingControl(spec: SettingSpec, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        when (spec.kind) {
            SettingKind.CHECKBOX -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spec.label, modifier = Modifier.weight(1f), color = EditorText, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = value.toBooleanStrictOrNull() ?: false, onCheckedChange = { onValueChange(it.toString()) })
                }
            }
            SettingKind.SLIDER -> {
                val numeric = value.toFloatOrNull()?.coerceIn(spec.min, spec.max) ?: spec.min
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spec.label, modifier = Modifier.weight(1f), color = EditorText, style = MaterialTheme.typography.bodyMedium)
                    Text("%.3f".format(numeric), color = EditorAccent, style = MaterialTheme.typography.labelSmall)
                }
                Slider(value = numeric, onValueChange = { onValueChange(it.toString()) }, valueRange = spec.min..spec.max)
            }
            SettingKind.DROPDOWN -> {
                var expanded by remember(spec.key) { mutableStateOf(false) }
                Text(spec.label, color = EditorText, style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = value.ifBlank { spec.options.firstOrNull().orEmpty() },
                        onValueChange = {}, readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        spec.options.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false })
                        }
                    }
                }
            }
            SettingKind.COLOR -> {
                Text(spec.label, color = EditorText, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = value,
                    onValueChange = { typed -> if (typed.matches(Regex("#?[0-9A-Fa-f]{0,8}"))) onValueChange(if (typed.startsWith("#")) typed else "#$typed") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    supportingText = { Text("RGBA hex: #RRGGBBAA") },
                    leadingIcon = {
                        val parsed = remember(value) {
                            runCatching {
                                val hex = value.removePrefix("#")
                                if (hex.length == 8) {
                                    val r = hex.substring(0,2).toInt(16) / 255f
                                    val g = hex.substring(2,4).toInt(16) / 255f
                                    val b = hex.substring(4,6).toInt(16) / 255f
                                    val a = hex.substring(6,8).toInt(16) / 255f
                                    Color(r,g,b,a)
                                } else Color.Transparent
                            }.getOrDefault(Color.Transparent)
                        }
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(parsed))
                    }
                )
            }
            SettingKind.IMAGE_PICKER -> {
                Text(spec.label, color = EditorText, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { /* Image picker wiring is a separate follow-up. */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (value.isBlank()) "Choose image" else value.substringAfterLast('/'))
                }
            }
            SettingKind.FONT_PICKER -> {
                Text(spec.label, color = EditorText, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { /* Font picker wiring is a separate follow-up. */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(value.ifBlank { "Choose font" })
                }
            }
            SettingKind.CODE, SettingKind.TEXT -> {
                Text(spec.label, color = EditorText, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), minLines = if (spec.kind == SettingKind.CODE) 5 else 1)
            }
            SettingKind.NUMBER -> {
                Text(spec.label, color = EditorText, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = value, onValueChange = { input -> if (input.isEmpty() || input.toFloatOrNull() != null) onValueChange(input) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            SettingKind.ACTION -> {
                OutlinedButton(onClick = { /* Hook to shader compiler / variable registry. */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(spec.label)
                }
            }
        }
    }
}
