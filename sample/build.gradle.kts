import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Put your real key in local.properties to test against fewchurs.com:
//   fewchurs.apiKey=fr_live_xxx
//   fewchurs.baseUrl=https://www.fewchurs.com/api/v1
// Without it the sample talks to the local mock server (tools/mock-server).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val apiKey = localProps.getProperty("fewchurs.apiKey") ?: "fr_test_demo"
val baseUrl = localProps.getProperty("fewchurs.baseUrl") ?: "http://10.0.2.2:4010/api/v1"

android {
    namespace = "com.fewchurs.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fewchurs.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "FEWCHURS_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "FEWCHURS_BASE_URL", "\"$baseUrl\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(project(":fewchurs-android"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
