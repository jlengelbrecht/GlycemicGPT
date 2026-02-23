package com.glycemicgpt.mobile.domain.plugin

/**
 * Per-plugin key-value settings store. The platform provides an implementation
 * scoped to each plugin's ID namespace. Plugins read/write their settings
 * through this interface; the platform handles persistence and encryption.
 */
interface PluginSettingsStore {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getFloat(key: String, default: Float = 0f): Float
    fun putFloat(key: String, value: Float)
    fun remove(key: String)
}
