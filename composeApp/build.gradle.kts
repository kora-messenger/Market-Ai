import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:2.3.12")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-cio:2.3.12")
            implementation("androidx.activity:activity-compose:1.9.0")
            implementation("androidx.core:core-ktx:1.13.1")

            // Google Sign-In (Credential Manager + Google ID)
            implementation("androidx.credentials:credentials:1.3.0")
            implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
            implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:2.3.12")
        }
    }
}

android {
    namespace = "com.veltravia.marketai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.veltravia.marketai"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.0"

        // Google Sign-In: Web OAuth client ID (Google Cloud Console).
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${project.findProperty("GOOGLE_WEB_CLIENT_ID") ?: ""}\""
        )
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = file("marketai-debug.keystore")
            storePassword = "MarketAi2026!"
            keyAlias = "marketai"
            keyPassword = "MarketAi2026!"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
