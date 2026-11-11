package com.auralis.app.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.modulePropertiesStore by preferencesDataStore(name = "auralis_layer_properties")

/** Persist the complete per-layer property dictionary. Keys are layerId/category/property. */
internal suspend fun restoreModuleProperties(context: Context): Map<String, String> {
    val preferences = context.modulePropertiesStore.data.first()
    return preferences.asMap().mapNotNull { (key, value) ->
        if (key is Preferences.Key<*>) key.name to value.toString() else null
    }.toMap()
}

internal suspend fun persistModuleProperties(context: Context, values: Map<String, String>) {
    context.modulePropertiesStore.edit { preferences ->
        preferences.clear()
        values.forEach { (key, value) -> preferences[stringPreferencesKey(key)] = value }
    }
}
