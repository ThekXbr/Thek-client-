plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.thek.cliente"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.thek.cliente"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("thek") {
            storeFile = file("../keystore/thek.jks")
            storePassword = "thekpass123"
            keyAlias = "thek"
            keyPassword = "thekpass123"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("thek")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
