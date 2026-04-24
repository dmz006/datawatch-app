# datawatch (mobile client) proguard rules
# Per AGENT.md security rules: strip Log.* in release builds (bearer tokens must not leak).

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# SecurityLog category is exempt (documented in security-model.md) — keep it intact.
-keep class com.dmzs.datawatchclient.SecurityLog { *; }

# Kotlin / Coroutines
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Ktor's IntellijIdeaDebugDetector references JVM-only java.lang.management classes
# that don't exist on Android. R8 full-mode treats these as errors under AGP 8.5+;
# silence them since the detector is a no-op at runtime on Android.
-dontwarn java.lang.management.**
# Ktor + coroutines pull in slf4j-api; we don't bundle the impl because Android
# uses its own logging. R8 full-mode flags the missing binder — silence it.
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# SQLDelight generated
-keep class com.dmzs.datawatchclient.db.** { *; }

# Keep crash-line metadata (we don't use crashlytics — this is for local retention only)
-keepattributes SourceFile,LineNumberTable
