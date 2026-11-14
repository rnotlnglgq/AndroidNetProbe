import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningFile = rootProject.file("release-signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use { load(it) }
}

android {
    namespace = "com.rnotlnglgq.netprobe"
    compileSdk {
        version = release(37)
    }

    ndkVersion = "25.1.8937393"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }

    defaultConfig {
        applicationId = "com.rnotlnglgq.netprobe"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "0.1.0"

        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningFile.exists()) create("researchRelease") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            if (releaseSigningFile.exists()) signingConfig = signingConfigs.getByName("researchRelease")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}