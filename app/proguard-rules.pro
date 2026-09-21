# DynabloxL ProGuard/R8 rules.
# Minification is disabled for the MVP; these rules are kept ready so that turning R8 on
# (release.isMinifyEnabled = true) does not silently break privileged integrations.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# --- Shizuku (binder based API) ---
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn android.os.**
-dontwarn hidden.**

# --- Our own entry points referenced from AndroidManifest / reflection-free services ---
-keep class com.dynablox.launcher.service.** { *; }
-keep class com.dynablox.launcher.accessibility.** { *; }
-keep class com.dynablox.launcher.design.widgets.** { *; }

# --- Kotlin coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
