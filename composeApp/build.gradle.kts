plugins {
    alias(libs.plugins.androidApplication)  // ← Changed from androidLibrary
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "uz.ictschool.sketchchain"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.ictschool.sketchchain"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Add this line
        manifestPlaceholders["usesCleartextTraffic"] = "true"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}
dependencies {

    implementation("io.ktor:ktor-client-logging:2.3.12")
    implementation(project(":shared"))
}
