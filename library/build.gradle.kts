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

val generateEmojiData by tasks.registering {
    val emojiTest = file("emoji/emoji-test.txt")
    val annotations = file("emoji/annotations-en.xml")
    val outputDir = layout.buildDirectory.dir("generated/emojiData/kotlin")
    inputs.files(emojiTest, annotations)
    outputs.dir(outputDir)
    doLast {
        val keywordsByEmoji = Regex("""<annotation cp="([^"]+)">([^<]*)</annotation>""")
            .findAll(annotations.readText())
            .associate { match ->
                match.groupValues[1].replace("\uFE0F", "") to match.groupValues[2]
                    .split(" | ")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
            }
        val toneOrder = listOf("light", "medium-light", "medium", "medium-dark", "dark")
        val toneName = Regex("""^(.*): (light|medium-light|medium-dark|medium|dark) skin tone$""")
        val entryLine = Regex("""^[0-9A-F ]+; fully-qualified\s+# (\S+) E\S+ (.*)$""")

        class Entry(
            val group: String
            , val subgroup: String
            , val emoji: String
            , val name: String
            , val tones: MutableMap<String, String> = mutableMapOf()
        )

        val entries = LinkedHashMap<String, Entry>()
        var group = ""
        var subgroup = ""
        val toned = mutableListOf<Triple<String, String, String>>()
        emojiTest.readLines().forEach { line ->
            when {
                line.startsWith("# group: ") -> group = line.removePrefix("# group: ").trim()
                line.startsWith("# subgroup: ") -> subgroup = line.removePrefix("# subgroup: ").trim()
                group != "Component" -> entryLine.matchEntire(line)?.let { match ->
                    val emoji = match.groupValues[1]
                    val name = match.groupValues[2]
                    val tone = toneName.matchEntire(name)
                    when {
                        tone != null -> toned += Triple(tone.groupValues[1], tone.groupValues[2], emoji)
                        name.contains("skin tone") -> Unit
                        else -> entries[name] = Entry(group, subgroup, emoji, name)
                    }
                }
            }
        }
        toned.forEach { (baseName, tone, emoji) -> entries[baseName]?.tones?.put(tone, emoji) }

        val lines = entries.values.map { entry ->
            val keywords = keywordsByEmoji[entry.emoji.replace("\uFE0F", "")].orEmpty().joinToString("|")
            val tones = if (entry.tones.size == toneOrder.size) {
                toneOrder.joinToString(" ") { entry.tones.getValue(it) }
            } else {
                ""
            }
            listOf(entry.group, entry.subgroup, entry.emoji, entry.name, keywords, tones).joinToString("\t")
        }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        lines.forEach { line ->
            if (current.isNotEmpty() && current.length + line.length + 1 > 8000) {
                chunks += current.toString()
                current.clear()
            }
            current.append(line).append('\n')
        }
        if (current.isNotEmpty()) chunks += current.toString()

        fun literal(text: String) = buildString {
            text.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
        val file = outputDir.get().file("com/mindsync/library/emoji/EmojiData.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.mindsync.library.emoji\n\n" +
                "internal val EMOJI_DATA: List<String> = listOf(\n" +
                chunks.joinToString(",\n") { "    \"${literal(it)}\"" } +
                "\n)\n"
        )
    }
}

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
        commonMain {
            kotlin.srcDir(generateEmojiData)
        }
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
