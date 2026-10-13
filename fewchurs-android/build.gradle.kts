plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.fewchurs.sdk.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Every public resource name starts with fewchurs_ so it can't clash with the host app.
    resourcePrefix = "fewchurs_"
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "com.fewchurs.sdk.FewchursInternalApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

mavenPublishing {
    pom {
        name.set("Fewchurs Android")
        description.set("Ready-made Jetpack Compose feature-request board for Fewchurs.")
    }
}

dependencies {
    api(project(":fewchurs-core"))

    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.core.ktx)
}
