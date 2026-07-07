@file:OptIn(kotlinx.validation.ExperimentalBCVApi::class)

plugins {
    id("permpilot.kmp.library")
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.binary.compatibility.validator)
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.permpilot.history"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":permpilot-core"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }

        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}

sqldelight {
    databases {
        create("PermPilotHistoryDatabase") {
            packageName.set("io.github.alirezajavan.permpilot.history")
        }
    }
}

apiValidation {
    klib {
        enabled = true
    }
}

// permpilot-history is explicitly optional and independently versioned from permpilot-core/
// permpilot-compose (which share the root gradle.properties VERSION_NAME) -- it can ship bugfixes
// or add audit-log features on its own release cadence without forcing a core library bump.
version = "1.0.0"

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
        name.set("PermPilot History")
        description.set(
            "Optional SQLDelight-backed audit log for PermPilot -- wrap any PermissionController " +
                "with HistoryPermissionController to record every request/resolution/settings-open " +
                "event, queryable as a Flow.",
        )
    }
}
