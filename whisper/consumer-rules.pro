# Keep TensorFlow Lite classes
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Keep Whisper classes
-keep class com.whispertflite.** { *; }

# Keep whisper-cpp native bridge classes
-keep class com.whispercpp.whisper.** { *; }
