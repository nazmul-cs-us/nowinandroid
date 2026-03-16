# Repackage classes into the default package to reduce the size of descriptors.
-repackageclasses

# ============================================================================
# General Android / Kotlin rules
# ============================================================================

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Kotlin Serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.starception.submission.**$$serializer { *; }
-keepclassmembers class com.starception.submission.** {
    *** Companion;
}
-keepclasseswithmembers class com.starception.submission.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# Hilt / Dagger
# ============================================================================
-keepclasseswithmembernames class * {
    @dagger.* <fields>;
}
-keepclasseswithmembernames class * {
    @dagger.* <methods>;
}
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ============================================================================
# Room Database
# ============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================================================
# Sherpa-ONNX (JNI native library)
# ============================================================================
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    native <methods>;
    *;
}

# ============================================================================
# NASA WorldWind (3D Globe)
# ============================================================================
-keep class gov.nasa.worldwind.** { *; }
-dontwarn gov.nasa.worldwind.**

# ============================================================================
# LibGDX (3D Visualization)
# ============================================================================
-keep class com.badlogic.gdx.** { *; }
-keepclassmembers class com.badlogic.gdx.** {
    native <methods>;
}
-dontwarn com.badlogic.gdx.**

# ============================================================================
# TensorFlow Lite (ML Inference)
# ============================================================================
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** {
    native <methods>;
}
-dontwarn org.tensorflow.lite.**

# ============================================================================
# Retrofit / OkHttp (if used via network module)
# ============================================================================
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================================
# Compose
# ============================================================================
-dontwarn androidx.compose.**

# ============================================================================
# AndroidX Car App (Android Auto)
# ============================================================================
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# ============================================================================
# Google Play Services Location
# ============================================================================
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ============================================================================
# Firebase
# ============================================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ============================================================================
# Coil (Image Loading)
# ============================================================================
-dontwarn coil.**

# ============================================================================
# Proto DataStore
# ============================================================================
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ============================================================================
# AndroidX WorkManager + Hilt Worker
# ============================================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.hilt.work.** { *; }

# ============================================================================
# Android Liquid Glass (Backdrop library)
# ============================================================================
-keep class com.kyant.backdrop.** { *; }
-dontwarn com.kyant.backdrop.**

# ============================================================================
# Accompanist Permissions
# ============================================================================
-dontwarn com.google.accompanist.**

# ============================================================================
# ExoPlayer / Media3
# ============================================================================
-dontwarn androidx.media3.**

# ============================================================================
# Suppress warnings for optional dependencies
# ============================================================================
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn sun.misc.Cleaner
