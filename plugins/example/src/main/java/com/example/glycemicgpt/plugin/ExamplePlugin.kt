package com.example.glycemicgpt.plugin

import com.glycemicgpt.mobile.domain.plugin.PLUGIN_API_VERSION
import com.glycemicgpt.mobile.domain.plugin.Plugin
import com.glycemicgpt.mobile.domain.plugin.PluginCapability
import com.glycemicgpt.mobile.domain.plugin.PluginCapabilityInterface
import com.glycemicgpt.mobile.domain.plugin.PluginContext
import com.glycemicgpt.mobile.domain.plugin.PluginMetadata
import com.glycemicgpt.mobile.domain.plugin.ui.DashboardCardDescriptor
import com.glycemicgpt.mobile.domain.plugin.ui.SettingDescriptor
import com.glycemicgpt.mobile.domain.plugin.ui.PluginSettingsDescriptor
import com.glycemicgpt.mobile.domain.plugin.ui.PluginSettingsSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.reflect.KClass

/**
 * A minimal example plugin that demonstrates the runtime plugin lifecycle.
 *
 * This plugin declares the `DATA_SYNC` capability (multiple-instance, so it
 * won't conflict with other plugins) and simply logs activate/deactivate calls.
 */
class ExamplePlugin : Plugin {

    override val metadata = PluginMetadata(
        id = "com.example.glycemicgpt.example",
        name = "Example Plugin",
        version = "1.0.0",
        apiVersion = PLUGIN_API_VERSION,
        description = "A minimal reference plugin demonstrating the runtime plugin API.",
        author = "GlycemicGPT",
    )

    override val capabilities: Set<PluginCapability> = setOf(
        PluginCapability.DATA_SYNC,
    )

    private var context: PluginContext? = null

    override fun initialize(context: PluginContext) {
        this.context = context
        context.debugLogger.log("ExamplePlugin", "initialized with pluginId=${context.pluginId}")
    }

    override fun shutdown() {
        context?.debugLogger?.log("ExamplePlugin", "shutdown")
        context = null
    }

    override fun onActivated() {
        context?.debugLogger?.log("ExamplePlugin", "activated")
    }

    override fun onDeactivated() {
        context?.debugLogger?.log("ExamplePlugin", "deactivated")
    }

    override fun settingsDescriptor(): PluginSettingsDescriptor = PluginSettingsDescriptor(
        sections = listOf(
            PluginSettingsSection(
                title = "Example Settings",
                items = listOf(
                    SettingDescriptor.InfoText(
                        key = "info",
                        text = "This is an example plugin. It demonstrates the runtime plugin API.",
                    ),
                    SettingDescriptor.Toggle(
                        key = "debug_mode",
                        label = "Debug Mode",
                        description = "Enable extra logging for this plugin",
                    ),
                ),
            ),
        ),
    )

    override fun observeDashboardCards(): Flow<List<DashboardCardDescriptor>> =
        flowOf(emptyList())

    override fun <T : PluginCapabilityInterface> getCapability(type: KClass<T>): T? = null
}
