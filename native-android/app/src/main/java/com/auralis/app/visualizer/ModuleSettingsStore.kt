package com.auralis.app.visualizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Persists every setting as JSON in app-private SharedPreferences.
 * Values are keyed by module ID + setting key, so new catalog fields receive their defaults
 * without invalidating projects saved by older versions.
 */
class ModuleSettingsStore(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("auralis_module_settings_v1", 0)
    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = preferences.getString("settings_json", "{}") ?: "{}"
            val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            val restored = buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.optString(key, ""))
                }
            }
            _values.value = restored
        }
    }

    fun value(moduleId: String, spec: SettingSpec): String =
        _values.value["$moduleId.${spec.key}"] ?: spec.defaultValue.ifBlank {
            when (spec.kind) {
                SettingKind.CHECKBOX -> "false"
                SettingKind.COLOR -> "#FFFFFFFF"
                SettingKind.DROPDOWN -> spec.options.firstOrNull().orEmpty()
                SettingKind.SLIDER, SettingKind.NUMBER -> spec.min.toString()
                else -> ""
            }
        }

    fun update(moduleId: String, spec: SettingSpec, value: String) {
        val key = "$moduleId.${spec.key}"
        val next = _values.value.toMutableMap().apply { put(key, value) }
        _values.value = next
        viewModelScope.launch(Dispatchers.IO) {
            val json = JSONObject()
            next.forEach { (k, v) -> json.put(k, v) }
            preferences.edit().putString("settings_json", json.toString()).apply()
        }
    }

    fun reset(moduleId: String, groups: List<SettingGroup>) {
        val next = _values.value.toMutableMap()
        groups.flatMap { it.settings }.forEach { next.remove("$moduleId.${it.key}") }
        _values.value = next
        viewModelScope.launch(Dispatchers.IO) {
            val json = JSONObject()
            next.forEach { (k, v) -> json.put(k, v) }
            preferences.edit().putString("settings_json", json.toString()).apply()
        }
    }

    fun exportJson(): String = JSONObject().apply {
        _values.value.forEach { (key, value) -> put(key, value) }
    }.toString(2)
}
