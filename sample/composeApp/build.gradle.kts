plugins {
    id("permpilot.kmp.library")
    id("permpilot.kmp.compose")
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.permpilot.sample.compose"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":permpilot-compose"))
            implementation(project(":permpilot-history"))
            implementation(libs.androidx.lifecycle.viewmodel)
        }
    }
}
