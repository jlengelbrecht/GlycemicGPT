# tandem-pump-driver consumer ProGuard rules
# Applied automatically to consuming modules (e.g., :app) during R8/ProGuard.

# Keep Hilt DI module
-keep class com.glycemicgpt.mobile.di.TandemPumpModule { *; }

# BouncyCastle EC-JPAKE: only keep the crypto packages actually used by Tandem auth.
# Narrowed from blanket org.bouncycastle.** to reduce APK size.
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.math.** { *; }
-keep class org.bouncycastle.util.** { *; }
-dontwarn org.bouncycastle.**
