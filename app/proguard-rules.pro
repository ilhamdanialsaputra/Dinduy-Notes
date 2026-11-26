-keep class dev.ffmpegkit.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Tesseract4Android (JNI)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# PDFBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
-dontwarn org.apache.commons.logging.**

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.dinduy.**$$serializer { *; }
-keepclassmembers class app.dinduy.** { *** Companion; }
-keepclasseswithmembers class app.dinduy.** { kotlinx.serialization.KSerializer serializer(...); }

-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
