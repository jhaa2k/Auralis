package com.auralis.app.ui

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets

private val Context.modulePropertiesStore by preferencesDataStore(name = "auralis_layer_properties")
private const val LAYER_REGISTRY_KEY = "__auralis_layer_registry"

/** Persist the complete per-layer property dictionary. Keys are layerId/category/property. */
internal suspend fun restoreModuleProperties(context: Context): Map<String, String> {
    val preferences = context.modulePropertiesStore.data.first()
    return preferences.asMap().mapNotNull { (key, value) ->
        if (key is Preferences.Key<*> && key.name != LAYER_REGISTRY_KEY) key.name to value.toString() else null
    }.toMap()
}

internal suspend fun persistModuleProperties(context: Context, values: Map<String, String>) {
    context.modulePropertiesStore.edit { preferences ->
        preferences.asMap().keys.filter { it.name != LAYER_REGISTRY_KEY }.forEach { preferences.remove(it) }
        values.forEach { (key, value) -> preferences[stringPreferencesKey(key)] = value }
    }
}

internal data class SavedLayerRecord(val id: Int, val type: String, val name: String)

internal suspend fun restoreLayerRegistry(context: Context): List<SavedLayerRecord> {
    val raw = context.modulePropertiesStore.data.first()[stringPreferencesKey(LAYER_REGISTRY_KEY)].orEmpty()
    return raw.lineSequence().mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size != 3) null else runCatching {
            SavedLayerRecord(
                parts[0].toInt(),
                String(Base64.decode(parts[1], Base64.NO_WRAP), StandardCharsets.UTF_8),
                String(Base64.decode(parts[2], Base64.NO_WRAP), StandardCharsets.UTF_8)
            )
        }.getOrNull()
    }.toList()
}

internal suspend fun persistLayerRegistry(context: Context, layers: List<SavedLayerRecord>) {
    val encoded = layers.joinToString("\n") { layer ->
        val type = Base64.encodeToString(layer.type.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val name = Base64.encodeToString(layer.name.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        "${layer.id}|${type}|${name}"
    }
    context.modulePropertiesStore.edit { it[stringPreferencesKey(LAYER_REGISTRY_KEY)] = encoded }
}
