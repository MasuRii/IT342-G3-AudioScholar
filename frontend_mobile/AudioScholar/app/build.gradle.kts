// app/build.gradle.kts (App module level)

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    kotlin("kapt") // Or id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.compose.compiler) // Apply Compose Compiler plugin
}

android {
    namespace = "edu.cit.audioscholar"
    compileSdk = 34

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
        // viewBinding = true // Can likely remove if not using any XML ViewBinding
    }
    // composeOptions {} block is removed (handled by plugin)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // Core & Jetpack
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose) // For Compose Activity

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose) // For collectAsStateWithLifecycle

    // UI - Compose
    implementation(platform(libs.androidx.compose.bom)) // Use Compose BOM
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview) // Preview support
    implementation(libs.androidx.material3)          // Material 3 components
    implementation(libs.androidx.material.icons.core) // Material Icons
    implementation(libs.androidx.material.icons.extended) // More Material Icons

    // Navigation
    implementation(libs.androidx.navigation.compose) // Compose Navigation

    // Hilt Dependencies
    implementation(libs.hilt.android)                 // Hilt Runtime
    kapt(libs.hilt.compiler)                          // Hilt Annotation Processor
    implementation(libs.androidx.hilt.navigation.compose) // Hilt ViewModel Injection for Compose

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Compose test BOM
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)           // Compose tooling for debug builds
    debugImplementation(libs.androidx.ui.test.manifest)

    // --- REMOVED Dependencies (Not needed for pure Compose approach) ---
    // implementation("androidx.appcompat:appcompat:1.7.0")
    // implementation("androidx.fragment:fragment-ktx:1.6.2") // Remove unless specifically needed elsewhere
    // implementation(libs.androidx.navigation.fragment.ktx) // Using compose navigation
    // implementation(libs.androidx.navigation.ui.ktx) // Using compose navigation
    // implementation("com.google.android.material:material:1.12.0") // Using Compose Material3
    // implementation("androidx.constraintlayout:constraintlayout:2.2.1") // Using Compose layouts
    // implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7") // Can remove if only using StateFlow in ViewModel
}

// KAPT configuration
kapt {
    correctErrorTypes = true
}