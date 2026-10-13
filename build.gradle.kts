import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.maven.publish) apply false
}

// JitPack builds straight from a git tag and serves the artifacts under its own group.
// The POM that links fewchurs-android to fewchurs-core must use that group as well.
val onJitPack = System.getenv("JITPACK") == "true"

allprojects {
    group = if (onJitPack) "com.github.fewchurs.fewchurs-kotlin" else "shop.fewchurs"
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = false)

            // Sign only when a key is set up, so publishToMavenLocal and JitPack work without one.
            // Maven Central rejects unsigned uploads, so a release without a key fails there instead.
            val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
                providers.gradleProperty("signing.keyId").isPresent
            if (hasSigningKey) signAllPublications()

            pom {
                url.set("https://github.com/fewchurs/fewchurs-kotlin")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("Fewchurs SDK License")
                        url.set("https://github.com/fewchurs/fewchurs-kotlin/blob/main/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("fewchurs")
                        name.set("Fewchurs")
                        url.set("https://www.fewchurs.shop")
                    }
                }
                scm {
                    url.set("https://github.com/fewchurs/fewchurs-kotlin")
                    connection.set("scm:git:git://github.com/fewchurs/fewchurs-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/fewchurs/fewchurs-kotlin.git")
                }
            }
        }
    }
}
