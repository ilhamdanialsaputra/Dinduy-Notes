plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Penandatanganan hanya untuk CI milik developer (env KS_FILE). F-Droid membangun tanpa kunci ini dan menandatangani sendiri.
val ksFile: String? = System.getenv("KS_FILE")

android {
    namespace = "app.dinduy"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.dinduy"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.10"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Blob metadata dependensi bertanda tangan Google di dalam APK: tidak perlu, dan dimatikan sesuai saran F-Droid / IzzyOnDroid.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksFile != null) signingConfig = signingConfigs.getByName("ci")
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
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("io.github.oleksandrbalan:pagecurl:1.5.1")
    // OCR: Tesseract (Apache-2.0), pengganti Google ML Kit (closed-source)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // Transkripsi offline: whisper.cpp (MIT), Maven Central
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
