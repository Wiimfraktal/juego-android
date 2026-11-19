plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cristal.abismo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cristal.abismo"
        minSdk = 24
        targetSdk = 34
        val build = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.$build"
    }

    signingConfigs {
        // Clave fija de depuración: permite actualizar el APK sin desinstalar.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
