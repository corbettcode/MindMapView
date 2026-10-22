import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    jvm()

    // The app as a web site: `./gradlew :desktop:wasmJsBrowserDistribution` writes it to
    // build/dist/wasmJs/productionExecutable, to be served as static files.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "mindmapview"
        browser {
            commonWebpackConfig {
                outputFileName = "mindmapview.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.richeditor.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.flexmark)
            implementation(libs.flexmark.ext.gfm.strikethrough)
            implementation(libs.flexmark.html2md.converter)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.mindsync.mindmapview.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "mindmapview"
            packageVersion = "1.0.0"
            description = "Mind map editor"

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
                shortcut = true
                menuGroup = "Office"
            }
        }
    }
}
