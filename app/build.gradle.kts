plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// ---------------------------------------------------------------------------
// Advertising: development vs production ad units.
// DEBUG builds always use Google's OFFICIAL TEST units (never live ads in
// development, never fake production impressions). RELEASE builds read the
// real AdMob IDs from gradle properties (-PADMOB_APP_ID=... etc.); until
// those are configured the safe test units remain — ads serve, revenue
// doesn't, policy is never violated. See README monetization section.
// ---------------------------------------------------------------------------
val TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347571713"
val TEST_ADMOB_UNIT_BANNER = "ca-app-pub-3940256099942544/6300978111"
val TEST_ADMOB_UNIT_INTERSTITIAL = "ca-app-pub-3940256099942544/1035306687"
val TEST_ADMOB_UNIT_REWARDED = "ca-app-pub-3940256099942544/5224354957"
val TEST_ADMOB_UNIT_NATIVE = "ca-app-pub-3940256099942544/2247696110"

android {
    namespace = "com.veltravia.marketscopeai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.veltravia.marketscopeai"
        minSdk = 24
        targetSdk = 34
        versionCode = 35
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Google Sign-In: Web OAuth client ID (Google Cloud Console). Injected via
        // -PGOOGLE_WEB_CLIENT_ID=... at build time or gradle.properties; empty until configured.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${project.findProperty("GOOGLE_WEB_CLIENT_ID") ?: ""}\""
        )

        // Ad units: release property values (documented) with safe test defaults.
        buildConfigField("String", "ADMOB_UNIT_BANNER", "\"${project.findProperty("ADMOB_UNIT_BANNER") ?: TEST_ADMOB_UNIT_BANNER}\"")
        buildConfigField("String", "ADMOB_UNIT_INTERSTITIAL", "\"${project.findProperty("ADMOB_UNIT_INTERSTITIAL") ?: TEST_ADMOB_UNIT_INTERSTITIAL}\"")
        buildConfigField("String", "ADMOB_UNIT_REWARDED", "\"${project.findProperty("ADMOB_UNIT_REWARDED") ?: TEST_ADMOB_UNIT_REWARDED}\"")
        buildConfigField("String", "ADMOB_UNIT_NATIVE", "\"${project.findProperty("ADMOB_UNIT_NATIVE") ?: TEST_ADMOB_UNIT_NATIVE}\"")
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = file("marketscopeai-debug.keystore")
            storePassword = "MarketAi2026!"
            keyAlias = "marketai"
            keyPassword = "MarketAi2026!"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
            // Development ALWAYS serves Google's official test ads.
            manifestPlaceholders["ADMOB_APP_ID"] = TEST_ADMOB_APP_ID
            buildConfigField("String", "ADMOB_UNIT_BANNER", "\"$TEST_ADMOB_UNIT_BANNER\"")
            buildConfigField("String", "ADMOB_UNIT_INTERSTITIAL", "\"$TEST_ADMOB_UNIT_INTERSTITIAL\"")
            buildConfigField("String", "ADMOB_UNIT_REWARDED", "\"$TEST_ADMOB_UNIT_REWARDED\"")
            buildConfigField("String", "ADMOB_UNIT_NATIVE", "\"$TEST_ADMOB_UNIT_NATIVE\"")
        }
        release {
            isMinifyEnabled = false
            // Production: real AdMob IDs come from -PADMOB_APP_ID / -PADMOB_UNIT_*.
            manifestPlaceholders["ADMOB_APP_ID"] = (project.findProperty("ADMOB_APP_ID") ?: TEST_ADMOB_APP_ID).toString()
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Google Sign-In (Credential Manager + Google ID)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // --- Google Play Billing (Play-Store-native subscription checkout) ---
    implementation("com.android.billingclient:billing-ktx:6.2.1")

    // --- Advertising (official SDKs only — no fake ads anywhere) ---
    // Google AdMob: primary network, loaded through the mediation abstraction.
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    // Google UMP: the official consent flow required before requesting ads.
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    // Firebase Cloud Messaging (push notifications)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
