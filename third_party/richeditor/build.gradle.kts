@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Compose Rich Editor 1.2.1 (https://github.com/MohamedRejeb/compose-rich-editor), built from its published
// sources with one change; see README.md. Only the targets the desktop module has are built.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    jvm()

    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.material3)
            implementation(libs.ksoup.html)
            implementation(libs.ksoup.entities)
            implementation(libs.jetbrains.markdown)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
