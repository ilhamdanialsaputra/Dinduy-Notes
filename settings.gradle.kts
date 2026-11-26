pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android hanya dipublikasikan lewat JitPack (repo tepercaya F-Droid).
        // Dibatasi ke satu grup supaya tidak ada dependensi lain yang diambil dari sini.
        maven("https://jitpack.io") {
            content { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}
rootProject.name = "Dinduy"
include(":app")
