import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val appVersion = "1.1.0"

// The README and version are shown from the Help menu. They are put in the code, as there are no resources the
// web app could read.
val generateAppInfo by tasks.registering {
    val readme = rootProject.file("README.md")
    val version = appVersion
    val outputDir = layout.buildDirectory.dir("generated/appInfo/kotlin")
    inputs.file(readme)
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val text = readme.readText()
        val literal = buildString {
            text.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
        val file = outputDir.get().file("com/mindsync/mindmapview/desktop/AppInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.mindsync.mindmapview.desktop\n\n" +
                "internal const val APP_VERSION = \"$version\"\n\n" +
                "internal const val README_MARKDOWN = \"$literal\"\n"
        )
    }
}

// The app uses the patched rich text editor in third_party/richeditor in place of the published one, also where
// the library brings that in.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.mohamedrejeb.richeditor:richeditor-compose")).using(project(":richeditor"))
    }
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
        commonMain {
            kotlin.srcDir(generateAppInfo)
        }
        commonMain.dependencies {
            implementation(project(":library"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.richeditor.compose)
        }
        // The emoji font is on the JVM classpath at /NotoColorEmoji.ttf and served next to index.html on the web.
        jvmMain {
            resources.srcDir("fonts")
        }
        wasmJsMain {
            resources.srcDir("fonts")
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
            packageVersion = appVersion
            description = "Mind map editor"

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
                shortcut = true
                menuGroup = "Office"
            }
        }
    }
}
