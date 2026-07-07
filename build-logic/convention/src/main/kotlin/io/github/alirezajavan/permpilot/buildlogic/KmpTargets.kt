package io.github.alirezajavan.permpilot.buildlogic

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun configureKmpTargets(extension: KotlinMultiplatformExtension) {
    extension.apply {
        // The Android target is configured in the KmpLibraryConventionPlugin
        // or here if we want to share the configuration.
        // With the new 'com.android.kotlin.multiplatform.library' plugin,
        // we use the 'android' block.

        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { target ->
            target.binaries.framework {
                baseName = project.name
                isStatic = true
            }
        }
    }
}
