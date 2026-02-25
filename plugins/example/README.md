# Example Runtime Plugin

A minimal reference plugin demonstrating how to build a GlycemicGPT runtime plugin.

## What This Plugin Does

This plugin implements the `DATA_SYNC` capability and logs lifecycle events (initialize, activate, deactivate, shutdown). It serves as a template for building your own plugins.

## Building

### Prerequisites

1. Android SDK with `d8` tool (included in Android build tools)
2. The `pump-driver-api` module compiled as an AAR or JAR

### Steps

1. **Compile the plugin:**

   If building within the GlycemicGPT monorepo (for testing):
   ```bash
   cd plugins/example
   ../../gradlew jar
   ```

   If building standalone, first copy the pump-driver-api AAR to `libs/` and uncomment the `compileOnly(files(...))` line in `build.gradle.kts`.

2. **Convert to DEX bytecode:**

   Android cannot run standard JVM bytecode. Use `d8` to convert:
   ```bash
   d8 --output dex-out/ build/libs/example-plugin.jar
   ```

3. **Repackage DEX + manifest into the final JAR:**

   The `d8` tool outputs `classes.dex` to a directory. Combine it with the
   original JAR (which has the manifest):
   ```bash
   cp build/libs/example-plugin.jar example-plugin.jar
   jar uf example-plugin.jar -C dex-out classes.dex
   ```

4. **Install on device:**

   Option A -- Use the app's UI:
   - Copy `example-plugin.jar` to your phone
   - Open Settings > Plugins > Custom Plugins > Add Plugin
   - Select the JAR file

   Option B -- Use adb (debug builds only, non-rooted device):
   ```bash
   adb push example-plugin.jar /sdcard/example-plugin.jar
   adb shell run-as com.glycemicgpt.mobile.debug \
       mkdir -p files/plugins
   adb shell run-as com.glycemicgpt.mobile.debug \
       cp /sdcard/example-plugin.jar files/plugins/
   adb shell rm /sdcard/example-plugin.jar
   ```
   Then restart the app. Replace `com.glycemicgpt.mobile.debug` with the
   correct package name for your build variant.

## Plugin Manifest

Every runtime plugin JAR must contain `META-INF/plugin.json`:

```json
{
  "factoryClass": "com.example.glycemicgpt.plugin.ExamplePluginFactory",
  "apiVersion": 1,
  "id": "com.example.glycemicgpt.example",
  "name": "Example Plugin",
  "version": "1.0.0",
  "author": "GlycemicGPT",
  "description": "A minimal reference plugin."
}
```

### Required Fields

| Field | Description |
|-------|-------------|
| `factoryClass` | Fully-qualified class name of your `PluginFactory` implementation |
| `apiVersion` | Must match the host app's `PLUGIN_API_VERSION` (currently `1`) |
| `id` | Reverse-domain plugin ID (must match `^[a-zA-Z][a-zA-Z0-9._-]{1,127}$`) |
| `name` | Human-readable display name |
| `version` | Semantic version string |

### Optional Fields

| Field | Description |
|-------|-------------|
| `author` | Author name (displayed in Settings) |
| `description` | Short description |

## Security Restrictions

Runtime plugins run in a sandboxed context:

- **No Android system services** -- `getSystemService()` throws `SecurityException`
- **No activities or services** -- `startActivity()`, `startService()` throw `SecurityException`
- **No content providers** -- `getContentResolver()` throws `SecurityException`
- **No broadcasts** -- `sendBroadcast()` throws `SecurityException`
- **No pump credentials** -- `credentialProvider` throws `UnsupportedOperationException`

You DO have access to:
- `settingsStore` -- per-plugin key-value persistence
- `debugLogger` -- logging
- `eventBus` -- cross-plugin communication
- `safetyLimits` -- read-only safety constraints
- `androidContext.filesDir`, `cacheDir`, etc. -- file I/O

## Further Reading

See [docs/plugin-architecture.md](../../docs/plugin-architecture.md) for the full plugin API documentation.
