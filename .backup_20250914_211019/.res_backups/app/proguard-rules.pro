# Keep ML Kit and CameraX classes as needed
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }
-dontwarn androidx.camera.**

# ZXing / JourneyApps
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.** { *; }
-dontwarn com.journeyapps.**

# CameraX cushion
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# App package
-keep class .** { *; }

# ZXing / JourneyApps
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.** { *; }
-dontwarn com.journeyapps.**

# CameraX cushion
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# App package
-keep class com.quantumqr.util.** { *; }
