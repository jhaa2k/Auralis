package com.auralis.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration

private data class EditorNavItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val editorNavItems = listOf(
    EditorNavItem("Media", Icons.Outlined.FileOpen),
    EditorNavItem("Layers", Icons.Outlined.Layers),
    EditorNavItem("Presets", Icons.Outlined.Palette),
    EditorNavItem("Audio", Icons.Outlined.GraphicEq),
    EditorNavItem("Scene", Icons.Outlined.Tune)
)

private data class InspectorSection(val title: String, val description: String)

/**
 * Layout-only editor shell. Replace placeholder callbacks/content with feature state as each
 * module is connected. The screen deliberately owns no playback, rendering or export logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuralEditorScreen(
    projectTitle: String = "Project Title",
    selectedItem: String = "Spectrum Bars",
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onExport: () -> Unit = {},
    onNavigationSelected: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("Presets") }
    var sheetVisible by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sections = remember(selectedTab) {
        when (selectedTab) {
            "Media" -> listOf(
                InspectorSection("Queue", "Track list and playlist controls"),
                InspectorSection("Import Media", "Choose audio, video, or image files")
            )
            "Layers" -> listOf(
                InspectorSection("Transform", "Position, scale, rotation, opacity, and alignment"),
                InspectorSection("Audio Reactivity", "Sensitivity, attack, release, and response curve"),
                InspectorSection("Appearance", "Color, fill, stroke, and typography"),
                InspectorSection("Effects", "Glow, blur, blend mode, and compositing"),
                InspectorSection("Animation", "Keyframes, motion, repeat, and timing"),
                InspectorSection("Layer Type Settings", "Controls specific to the selected layer")
            )
            "Presets" -> listOf(
                InspectorSection("My Presets", "Saved project and layer presets"),
                InspectorSection("Preset Browser", "Browse available preset layouts")
            )
            "Audio" -> listOf(
                InspectorSection("Spectrum", "FFT size, frequency range, and smoothing"),
                InspectorSection("Frequency Bands", "Bass, midrange, and treble"),
                InspectorSection("Reactive Mapping", "Map audio values to layer properties")
            )
            else -> listOf(
                InspectorSection("Background", "Canvas size, color, and image"),
                InspectorSection("Scene Effects", "Global effects and composition settings"),
                InspectorSection("Guides", "Safe area, grid, and alignment guides")
            )
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        projectTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = "App icon")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onUndo) { Icon(Icons.Outlined.Undo, contentDescription = "Undo") }
                    IconButton(onClick = onRedo) { Icon(Icons.Outlined.Redo, contentDescription = "Redo") }
                    Button(
                        onClick = onExport,
                        modifier = Modifier.padding(end = 10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("Export") }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    editorNavItems.forEachIndexed { index, item ->
                        val selected = selectedTab == item.title
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedTab = item.title
                                    activeSection = null
                                    onNavigationSelected(item.title)
                                    if (!sheetVisible) sheetVisible = true
                                }
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.title,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                item.title,
                                fontSize = 10.sp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = screenHeight * 0.35f, max = screenHeight * 0.40f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF08090B))
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension * 0.16f
                        drawCircle(Color(0xFF343943), radius, center, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        drawCircle(Color(0xFF777F8D), radius * 0.62f, center, style = Stroke(1.dp.toPx()))
                        for (i in 0 until 48) {
                            val x = size.width * (i + 0.5f) / 48f
                            val h = size.height * (0.06f + ((i * 17) % 23) / 100f)
                            drawLine(
                                color = Color(0xFF9AA3B2).copy(alpha = 0.75f),
                                start = androidx.compose.ui.geometry.Offset(x, center.y - h / 2f),
                                end = androidx.compose.ui.geometry.Offset(x, center.y + h / 2f),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        shape = CircleShape,
                        color = Color(0xCC20242B)
                    ) {
                        Text(
                            "HUD INFO  ·  PREVIEW",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            color = Color.White,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        "CANVAS PREVIEW",
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        color = Color(0xFF858C98),
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text("EDITOR WORKSPACE", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Select a section below to edit the selected item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { sheetVisible = true }) { Text("Open Properties") }
                    }
                }
            }
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
            dragHandle = {
                Column(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = screenHeight * 0.60f)
                    .padding(horizontal = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Properties", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Edit selected layer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { sheetVisible = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close properties")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(selectedItem, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(selectedTab, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sections, key = { it.title }) { section ->
                        AccordionItem(
                            title = section.title,
                            expanded = activeSection == section.title,
                            onToggle = {
                                activeSection = if (activeSection == section.title) null else section.title
                            }
                        ) {
                            PlaceholderSectionControls(section.description)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccordionItem(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title"
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun PlaceholderSectionControls(description: String) {
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Text("Amount", style = MaterialTheme.typography.labelMedium)
    var amount by remember { mutableFloatStateOf(0.55f) }
    Slider(value = amount, onValueChange = { amount = it })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Enabled", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        var enabled by remember { mutableStateOf(true) }
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
    OutlinedTextField(
        value = "Default value",
        onValueChange = {},
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Placeholder field") },
        singleLine = true
    )
}
