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
    implementation(libs.flexmark)
    implementation(libs.flexmark.ext.gfm.strikethrough)
    implementation(libs.flexmark.html2md.converter)
    testImplementation(libs.kotlin.test)
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
                iconFile.set(project.file("src/main/resources/icon.png"))
                shortcut = true
                menuGroup = "Office"
            }
        }
    }
}
