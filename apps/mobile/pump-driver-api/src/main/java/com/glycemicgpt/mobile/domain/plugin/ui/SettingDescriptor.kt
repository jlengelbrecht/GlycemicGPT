package com.glycemicgpt.mobile.domain.plugin.ui

/**
 * Declarative description of a single plugin setting field.
 * The platform renders these using Material 3 components in the settings screen.
 */
sealed class SettingDescriptor {
    abstract val key: String

    data class TextInput(
        override val key: String,
        val label: String,
        val hint: String = "",
        /** If true, the field is masked in the UI (password-style). */
        val sensitive: Boolean = false,
    ) : SettingDescriptor()

    data class Toggle(
        override val key: String,
        val label: String,
        val description: String = "",
    ) : SettingDescriptor()

    data class Slider(
        override val key: String,
        val label: String,
        val min: Float,
        val max: Float,
        val step: Float = 1f,
        val unit: String = "",
    ) : SettingDescriptor()

    data class Dropdown(
        override val key: String,
        val label: String,
        val options: List<DropdownOption>,
    ) : SettingDescriptor()

    data class ActionButton(
        override val key: String,
        val label: String,
        val style: ButtonStyle = ButtonStyle.DEFAULT,
    ) : SettingDescriptor()

    data class InfoText(
        override val key: String,
        val text: String,
    ) : SettingDescriptor()
}

data class DropdownOption(val value: String, val label: String)

enum class ButtonStyle { DEFAULT, PRIMARY, DESTRUCTIVE }

/**
 * A named section of plugin settings, rendered as a group with a header.
 */
data class PluginSettingsSection(
    val title: String,
    val items: List<SettingDescriptor>,
)

/**
 * Complete settings descriptor for a plugin, composed of sections.
 */
data class PluginSettingsDescriptor(
    val sections: List<PluginSettingsSection>,
)
