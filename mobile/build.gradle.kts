plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Telefoon-app die de stand van het horloge laat zien. Zelfde applicationId en sleutel als
// de horloge-app: dat is nodig om via de Wear Data Layer met elkaar te praten.
android {
    namespace = "nl.micha.padelscore.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.micha.padelscore"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

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
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
}
