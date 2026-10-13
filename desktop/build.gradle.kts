import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":library"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.richeditor.compose)
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
                shortcut = true
                menuGroup = "Office"
            }
        }
    }
}
