plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kinocast.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kinocast.app"
        minSdk = 26 // Android 8.0+ dan boshlab barcha telefonlarni qo'llab-quvvatlaydi (Paket xatoligini yo'qotadi)
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0"

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            v1SigningEnabled = true
            v2SigningEnabled = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.multidex:multidex:2.0.1")

    // Lightweight WebSocket server
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
}
