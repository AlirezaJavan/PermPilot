@file:OptIn(kotlinx.validation.ExperimentalBCVApi::class)

plugins {
    id("permpilot.kmp.library")
    id("permpilot.kmp.compose")
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.binary.compatibility.validator)
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.permpilot.compose"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":permpilot-core"))
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
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
        name.set("PermPilot Compose")
        description.set(
            "Compose Multiplatform UI layer for PermPilot -- PermissionGate and default " +
                "Material3 dialogs, built on permpilot-core.",
        )
    }
}
