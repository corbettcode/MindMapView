@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

group = "com.github.hegleB"
version = "1.0.0"

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.mindsync.library"
        compileSdk {
            version = release(37) {
                minorApiLevel = 0
            }
        }
        minSdk = 26

        androidResources {
            enable = true
        }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.richeditor.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = artifactId.replaceFirst(project.name, "MindMapView")
    }
}
