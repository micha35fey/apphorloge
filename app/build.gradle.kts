plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Op GitHub Actions is het buildnummer de versie, zodat elke build hoger is dan de vorige.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "nl.micha.padelscore"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.micha.padelscore"
        minSdk = 30
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    // Vaste sleutel, zodat een nieuwe versie over de oude heen geïnstalleerd kan worden.
    signingConfigs {
        create("padel") {
            storeFile = rootProject.file("padelscore.keystore")
            storePassword = "padelscore"
            keyAlias = "padelscore"
            keyPassword = "padelscore"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("padel")
        }
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation(project(":core"))
}
