# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt

# =============================================================================
# Security Rules - Prevent leakage of sensitive information
# =============================================================================

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep logging for warnings and errors (for crash analysis)
-keepclassmembers class android.util.Log {
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# =============================================================================
# Kotlin Serialization
# =============================================================================

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep model classes
-keep class com.nars.maplibre.data.model.** { *; }
-keep class com.geoman.maplibre.geoman.types.geojson.** { *; }

# =============================================================================
# Security - EncryptedSharedPreferences
# =============================================================================

-keep class androidx.security.crypto.** { *; }
-keep class androidx.startup.** { *; }

# Keep Android Keystore classes
-keep class javax.crypto.** { *; }
-dontwarn javax.crypto.**
-keep class java.security.** { *; }
-dontwarn java.security.**

# =============================================================================
# MapLibre
# =============================================================================

-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Keep GeoJSON models
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# =============================================================================
# Network & API
# =============================================================================

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep API client
-keep class com.nars.maplibre.data.api.** { *; }

# =============================================================================
# Compose
# =============================================================================

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose compiler generated classes
-keepclassmembers,allowobfuscation class * implements androidx.compose.runtime.Composer {
    void <init>(...);
}

# =============================================================================
# Coroutines
# =============================================================================

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# =============================================================================
# ViewModel & Lifecycle
# =============================================================================

-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# =============================================================================
# Prevent obfuscation of classes used in reflection
# =============================================================================

-keep class com.nars.maplibre.security.** { *; }
-keep class com.nars.maplibre.utils.NarsLogger { *; }
