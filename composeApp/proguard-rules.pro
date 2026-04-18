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

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# SQLDelight generated
-keep class com.dmzs.datawatchclient.db.** { *; }

# Keep crash-line metadata (we don't use crashlytics — this is for local retention only)
-keepattributes SourceFile,LineNumberTable
