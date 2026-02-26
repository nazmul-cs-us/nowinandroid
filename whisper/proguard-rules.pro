# Whisper TFLite Module ProGuard Rules

# Keep TensorFlow Lite classes
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Keep Whisper engine and ASR classes
-keep class com.whispertflite.** { *; }
