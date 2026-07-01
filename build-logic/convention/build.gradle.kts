plugins {
    `kotlin-dsl`
}

group = "io.github.alirezajavan.permpilot.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "permpilot.kmp.library"
            implementationClass = "io.github.alirezajavan.permpilot.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "permpilot.kmp.compose"
            implementationClass = "io.github.alirezajavan.permpilot.buildlogic.KmpComposeConventionPlugin"
        }
    }
}
