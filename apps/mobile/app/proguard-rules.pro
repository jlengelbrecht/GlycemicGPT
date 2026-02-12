# GlycemicGPT Android App ProGuard Rules

# Keep BLE protocol classes (needed for runtime reflection in message parsing)
-keep class com.glycemicgpt.mobile.ble.** { *; }

# Keep Moshi JSON adapters
-keep class com.glycemicgpt.mobile.data.** { *; }
-keepclassmembers class com.glycemicgpt.mobile.data.** {
    <init>(...);
}

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Strip debug logs in release builds
-assumenosideeffects class timber.log.Timber {
    public static void d(...);
    public static void v(...);
}
