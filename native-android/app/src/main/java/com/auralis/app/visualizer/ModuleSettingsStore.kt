package com.auralis.app.visualizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
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
    private var persistJob: Job? = null

    init {
        // SharedPreferences.getString is a memory read. Restore synchronously so the first
        // Compose frame never sees empty defaults and overwrites a user's saved values.
        val raw = preferences.getString("settings_json", "{}") ?: "{}"
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val restored = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            restored[key] = json.optString(key, "")
        }
        _values.value = restored
    }

    private fun persistDebounced(snapshot: Map<String, String>) {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(180)
            persist(snapshot)
        }
    }

    private fun persist(snapshot: Map<String, String>) {
        val json = JSONObject()
        snapshot.forEach { (key, value) -> json.put(key, value) }
        // apply() updates the in-memory preference immediately and writes on the disk thread.
        preferences.edit().putString("settings_json", json.toString()).apply()
    }

    fun value(moduleId: String, spec: SettingSpec): String =
        _values.value["$moduleId.${spec.key}"] ?: spec.defaultValue.ifBlank {
            when (spec.key) {
                "visible" -> "true"
                "positionX", "positionY", "alignmentX", "alignmentY", "pivotX", "pivotY" -> "0.5"
                "scaleX", "scaleY", "particleScale", "opacity", "opacityStrength", "alpha" -> "1"
                "rotation", "splitAmount", "brightness" -> "0"
                "blurRadius" -> "2"
                "blurMultiplier" -> "4.1"
                "blurAmount" -> "0.2"
                "contrast", "saturation", "lineHeight" -> "1"
                "barCount" -> "64"
                "heightScale", "maxHeightScale" -> "1"
                "barWidth" -> "0.72"
                "barSpacing" -> "0.18"
                "countLimit" -> "120"
                "startSize" -> "3"
                "fontSize" -> "30"
                "speed", "volume", "preamp" -> "1"
                "lowerHz" -> "20"
                "higherHz" -> "18000"
                "amountX", "amountY" -> "0.8"
                else -> when (spec.kind) {
                    SettingKind.CHECKBOX -> "false"
                    SettingKind.COLOR -> "#FFFFFFFF"
                    SettingKind.DROPDOWN -> spec.options.firstOrNull().orEmpty()
                    SettingKind.SLIDER, SettingKind.NUMBER -> spec.min.toString()
                    else -> ""
                }
            }
        }

    fun update(moduleId: String, spec: SettingSpec, value: String) {
        val key = "$moduleId.${spec.key}"
        val next = _values.value.toMutableMap().apply { put(key, value) }
        _values.value = next
        persistDebounced(next)
    }

    fun reset(moduleId: String, groups: List<SettingGroup>) {
        val next = _values.value.toMutableMap()
        groups.flatMap { it.settings }.forEach { next.remove("$moduleId.${it.key}") }
        _values.value = next
        persistDebounced(next)
    }

    override fun onCleared() {
        persistJob?.cancel()
        persist(_values.value)
        super.onCleared()
    }

    fun exportJson(): String = JSONObject().apply {
        _values.value.forEach { (key, value) -> put(key, value) }
    }.toString(2)
}
