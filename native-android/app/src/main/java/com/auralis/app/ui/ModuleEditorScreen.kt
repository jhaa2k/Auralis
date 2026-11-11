package com.auralis.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auralis.app.visualizer.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleEditorScreen(
    store: ModuleSettingsStore = viewModel()
) {
    var selectedModuleId by remember { mutableStateOf<String?>(null) }
    val values by store.values.collectAsState()
    val module = ModuleCatalog.modules.firstOrNull { it.id == selectedModuleId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(module?.title ?: "VISUALIZER MODULES", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (module != null) IconButton(onClick = { selectedModuleId = null }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (module != null) {
                        IconButton(onClick = { store.reset(module.id, module.groups) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Reset module")
                        }
                        IconButton(onClick = {}) { Icon(Icons.Outlined.Info, contentDescription = "Module information") }
                    }
                }
            )
        }
    ) { padding ->
        if (module == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("MODULE LIBRARY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Choose a module to inspect and edit its properties.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                val rows = ModuleCatalog.modules.chunked(2)
                items(rows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            Surface(
                                modifier = Modifier.weight(1f).height(112.dp).clickable { selectedModuleId = item.id },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 1.dp
                            ) {
                                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${item.groups.size} CATEGORIES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Text("Settings are saved automatically on this device as JSON.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            var expandedGroup by remember(module.id) { mutableStateOf<String?>(null) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text(module.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("MODULE PROPERTIES  •  AUTO-SAVED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                }
                items(module.groups, key = { it.title }) { group ->
                    val expanded = expandedGroup == group.title
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    expandedGroup = if (expanded) null else group.title
                                }.padding(horizontal = 15.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${group.settings.size} SETTINGS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingControl(spec: SettingSpec, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        when (spec.kind) {
            SettingKind.CHECKBOX -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spec.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = value.toBooleanStrictOrNull() ?: false, onCheckedChange = { onValueChange(it.toString()) })
                }
            }
            SettingKind.SLIDER -> {
                val numeric = value.toFloatOrNull()?.coerceIn(spec.min, spec.max) ?: spec.min
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spec.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("%.3f".format(numeric), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = numeric,
                    onValueChange = { onValueChange(it.toString()) },
                    valueRange = spec.min..spec.max
                )
            }
            SettingKind.DROPDOWN -> {
                var expanded by remember(spec.key) { mutableStateOf(false) }
                Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = value.ifBlank { spec.options.firstOrNull().orEmpty() },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        spec.options.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                onValueChange(option)
                                expanded = false
                            })
                        }
                    }
                }
            }
            SettingKind.COLOR -> {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = value,
                    onValueChange = { typed -> if (typed.matches(Regex("#?[0-9A-Fa-f]{0,8}"))) onValueChange(if (typed.startsWith("#")) typed else "#$typed") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("RGBA hex: #RRGGBBAA") },
                    leadingIcon = {
                        val parsed = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Transparent)
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(parsed))
                    }
                )
            }
            SettingKind.IMAGE_PICKER -> {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { /* Connect Android Photo Picker / SAF for this setting key. */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (value.isBlank()) "Choose image" else value.substringAfterLast('/'))
                }
            }
            SettingKind.FONT_PICKER -> {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { /* Connect downloadable/local font browser. */ }, modifier = Modifier.fillMaxWidth()) { Text(value.ifBlank { "Choose font" }) }
            }
            SettingKind.CODE, SettingKind.TEXT -> {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), minLines = if (spec.kind == SettingKind.CODE) 5 else 1)
            }
            SettingKind.NUMBER -> {
                Text(spec.label, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = value, onValueChange = { input -> if (input.isEmpty() || input.toFloatOrNull() != null) onValueChange(input) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            SettingKind.ACTION -> {
                OutlinedButton(onClick = { /* Hook to shader compiler / variable registry. */ }, modifier = Modifier.fillMaxWidth()) { Text(spec.label) }
            }
        }
    }
}
