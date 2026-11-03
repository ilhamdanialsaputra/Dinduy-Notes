# ---- Dinduy: aturan R8 ----
# Whisper (JNI): jangan diubah namanya / dibuang
-keep class dev.ffmpegkit.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# kotlinx.serialization (model data catatan & cadangan)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.dinduy.**$$serializer { *; }
-keepclassmembers class app.dinduy.** { *** Companion; }
-keepclasseswithmembers class app.dinduy.** { kotlinx.serialization.KSerializer serializer(...); }

# jsoup: dependensi opsional yang tidak dipakai
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
