plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ksFile: String? = System.getenv("KS_FILE")

val extraRules = layout.buildDirectory.file("generated/extra-rules.pro").get().asFile.also {
    it.parentFile.mkdirs()
    it.writeText(
        "-keep class com.tom_roush.pdfbox.** { *; }\n" +
            "-keep class com.tom_roush.fontbox.** { *; }\n" +
            "-dontwarn com.tom_roush.pdfbox.**\n" +
            "-dontwarn com.tom_roush.fontbox.**\n" +
            "-dontwarn com.gemalto.jp2.**\n" +
            "-dontwarn org.bouncycastle.**\n" +
            "-dontwarn javax.xml.**\n" +
            "-dontwarn org.apache.commons.logging.**\n"
    )
}

val fontAssets = layout.buildDirectory.dir("generated/font-assets")
val fetchFonts by tasks.registering {
    val root = fontAssets.get().asFile
    outputs.dir(root)
    doLast {
        val out = File(root, "fonts").apply { mkdirs() }
        val base = "https://raw.githubusercontent.com/google/fonts/main/ofl"
        val gsf = "GoogleSansFlex%5BROND%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf"
        val gsfGrad = "GoogleSansFlex%5BGRAD%2CROND%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf"
        val cdn = "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl"
        val files = listOf(
            // Google Sans Flex (OFL, variabel: wght 1-1000, wdth 25-151, ROND 0-100, opsz 1-144). Dicoba beberapa alamat berurutan.
            "google_sans_flex.ttf" to listOf(
                "$base/googlesansflex/$gsf", "$cdn/googlesansflex/$gsf",
                "$base/googlesansflex/$gsfGrad", "$cdn/googlesansflex/$gsfGrad"
            ),
            "tinos_regular.ttf" to listOf("$base/tinos/Tinos-Regular.ttf"),
            "tinos_bold.ttf" to listOf("$base/tinos/Tinos-Bold.ttf"),
            "tinos_italic.ttf" to listOf("$base/tinos/Tinos-Italic.ttf"),
            "tinos_bolditalic.ttf" to listOf("$base/tinos/Tinos-BoldItalic.ttf")
        )
        for ((name, urls) in files) {
            val f = File(out, name)
            if (f.exists() && f.length() > 20_000) continue
            var ok = false
            for (url in urls) {
                try {
                    uri(url).toURL().openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    if (f.length() > 20_000) { ok = true; break }
                } catch (e: Exception) {
                    logger.warn("Font $name failed from $url: ${e.message}")
                }
                f.delete()
            }
            if (!ok) logger.warn("Font $name could not be downloaded (app falls back to a system font)")
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchFonts) }

android {
    namespace = "app.dinduy"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.dinduy"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.8"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (ksFile != null) {
            create("ci") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KS_PASS")
                keyAlias = System.getenv("KS_ALIAS")
                keyPassword = System.getenv("KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", extraRules)
            signingConfig = if (ksFile != null) signingConfigs.getByName("ci") else signingConfigs.getByName("debug")
        }
        debug {
            if (ksFile != null) signingConfig = signingConfigs.getByName("ci")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets.getByName("main").assets.srcDir(fetchFonts)
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("io.github.oleksandrbalan:pagecurl:1.5.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
