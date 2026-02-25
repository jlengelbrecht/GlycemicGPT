package com.example.glycemicgpt.plugin

import com.glycemicgpt.mobile.domain.plugin.PLUGIN_API_VERSION
import com.glycemicgpt.mobile.domain.plugin.Plugin
import com.glycemicgpt.mobile.domain.plugin.PluginContext
import com.glycemicgpt.mobile.domain.plugin.PluginFactory
import com.glycemicgpt.mobile.domain.plugin.PluginMetadata

/**
 * Factory for the example runtime plugin.
 *
 * Runtime plugins require a **no-arg constructor** so the host app can
 * instantiate them via reflection from the JAR's DEX bytecode.
 */
class ExamplePluginFactory : PluginFactory {

    override val metadata = PluginMetadata(
        id = "com.example.glycemicgpt.example",
        name = "Example Plugin",
        version = "1.0.0",
        apiVersion = PLUGIN_API_VERSION,
        description = "A minimal reference plugin demonstrating the runtime plugin API.",
        author = "GlycemicGPT",
    )

    override fun create(context: PluginContext): Plugin {
        return ExamplePlugin()
    }
}
