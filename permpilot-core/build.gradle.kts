@file:OptIn(kotlinx.validation.ExperimentalBCVApi::class)

plugins {
    id("permpilot.kmp.library")
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.binary.compatibility.validator)
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.permpilot.core"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.multiplatform.settings)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.health.connect.client)
        }

        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}

apiValidation {
    klib {
        enabled = true
    }
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish.KotlinMultiplatform(
            javadocJar =
                com.vanniktech.maven.publish.JavadocJar
                    .Dokka("dokkaGenerateHtml"),
            sourcesJar =
                com.vanniktech.maven.publish.SourcesJar
                    .Sources(),
            androidVariantsToPublish = listOf("release"),
        ),
    )
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set("PermPilot Core")
        description.set(
            "Kotlin Multiplatform (Android + iOS) expect/actual controller and full " +
                "Permission/PermissionState model for PermPilot -- no Compose dependency.",
        )
    }
}
