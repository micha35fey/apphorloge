plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Gedeelde code voor horloge en telefoon: scoretelling, updater, synchronisatie en het icoon.
android {
    namespace = "nl.micha.padelscore.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("com.google.android.gms:play-services-wearable:18.2.0")
    api("androidx.core:core-ktx:1.13.1")
    api("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
}
