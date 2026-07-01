plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.alirezajavan.permpilot.sample.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.alirezajavan.permpilot.sample.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric tests that inflate Compose UI (rememberPermissionController) need the
            // AAR resources of compose-ui's transitive deps on the unit-test classpath.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":sample:composeApp"))
    implementation(libs.androidx.activity.compose)

    testImplementation(project(":permpilot-core"))
    testImplementation(project(":permpilot-compose"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
