package io.github.alirezajavan.permpilot.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            val composeVersion = libs.findVersionString("compose-multiplatform")
            val composeMaterial3Version = libs.findVersionString("compose-material3")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.apply {
                    commonMain.dependencies {
                        implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                        implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
                        implementation("org.jetbrains.compose.material3:material3:$composeMaterial3Version")
                        implementation("org.jetbrains.compose.ui:ui:$composeVersion")
                        implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
                        implementation("org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
                    }
                }
            }
        }
    }
}
