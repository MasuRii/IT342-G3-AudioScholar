plugins {
    id("com.android.application")
    alias(libs.plugins.jetbrainsKotlinAndroid)
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.compose.compiler)
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

android {
    namespace = "edu.cit.audioscholar"
    compileSdk = 35

    defaultConfig {
        applicationId = "edu.cit.audioscholar"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("com.github.jeziellago:compose-markdown:0.5.7")

    val room_version = "2.7.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.datastore:datastore-preferences:1.1.4")

    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation(libs.firebase.auth.ktx)

    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation(libs.google.firebase.analytics)

    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("com.google.accompanist:accompanist-permissions:0.31.5-beta")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.google.android.material)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.androidx.ui.text.google.fonts)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.analytics.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Ktor Client Core
    implementation("io.ktor:ktor-client-core:2.3.12")

    // Ktor Client Engine (OkHttp)
    implementation("io.ktor:ktor-client-okhttp:2.3.12")

    // Ktor Content Negotiation
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")

    // Ktor Kotlinx Serialization JSON
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // Ktor Logging
    implementation("io.ktor:ktor-client-logging:2.3.12")

    // Ktor Auth (For Bearer Token)
    implementation("io.ktor:ktor-client-auth:2.3.12")

    // Kotlinx Serialization Core
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

kapt {
    correctErrorTypes = true
}