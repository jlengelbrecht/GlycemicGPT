# tandem-pump-driver consumer ProGuard rules
# Applied automatically to consuming modules (e.g., :app) during R8/ProGuard.

# Keep BLE connection manager (uses reflection for removeBond via BluetoothDevice hidden API)
-keep class com.glycemicgpt.mobile.ble.connection.BleConnectionManager { *; }

# Keep Hilt DI module
-keep class com.glycemicgpt.mobile.di.TandemPumpModule { *; }

# BouncyCastle EC-JPAKE uses reflection internally
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
