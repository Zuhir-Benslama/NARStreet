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

# Keep Kotlin serialization models (serializers + companion objects)
-keep class com.nars.maplibre.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.nars.maplibre.data.model.**$Companion { *; }
-keep class com.geoman.maplibre.geoman.types.geojson.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# =============================================================================
# Security - EncryptedSharedPreferences
# =============================================================================

-keep class androidx.security.crypto.** { *; }
-keep class androidx.startup.** { *; }

# Keep only the specific crypto classes used via reflection
-keep class javax.crypto.KeyGenerator { *; }
-keep class javax.crypto.SecretKey { *; }
-keep class javax.crypto.spec.SecretKeySpec { *; }
-keep class java.security.KeyStore { *; }

# =============================================================================
# MapLibre - rely on the AAR's consumer ProGuard rules
# =============================================================================

-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.**

# =============================================================================
# Network & API
# =============================================================================

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep API models used for serialization
-keep class com.nars.maplibre.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.nars.maplibre.data.api.**$Companion { *; }

# =============================================================================
# Compose
# =============================================================================

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
# ViewModels are resolved by Koin via class literals (no string reflection), so
# R8 already retains the classes and their constructors. keepnames is a safety
# net for crash-report readability without blocking member shrinking.
-keepnames class com.nars.maplibre.MapViewModel
-keepnames class com.nars.maplibre.SettingsViewModel

# =============================================================================
# Prevent obfuscation of classes used in reflection
# =============================================================================

-keep class com.nars.maplibre.security.** { *; }
-keep class com.nars.maplibre.utils.NarsLogger { *; }
