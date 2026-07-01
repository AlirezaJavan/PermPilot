package io.github.alirezajavan.permpilot.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                configureKmpTargets(this)

                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }

                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("android") {
                    compileSdk = libs.findVersionString("android-compileSdk").toInt()
                    minSdk = libs.findVersionString("android-minSdk").toInt()
                    // JVM-only unit tests (no emulator/device needed) for pure Android-source-set
                    // logic -- e.g. AndroidPermissionController's resolve-state functions.
                    withHostTest {}
                }

                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(libs.findLibrary("kotlinx-coroutines-core").get())
                    }
                    commonTest.dependencies {
                        implementation(kotlin("test"))
                        implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                    }
                }
            }
        }
    }
}
