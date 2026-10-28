package com.mindsync.library.util

import com.mindsync.library.emoji.EmojiCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

class DescriptionEmojiTest {
    @Test fun anyEmojiIsRecognized() = assertEquals("🫠" to "<p>hi</p>", DescriptionEmoji.split("<p>🫠 hi</p>"))

    @Test fun aToneVariantIsRecognized() = assertEquals("👍🏽" to "ok", DescriptionEmoji.split("👍🏽 ok"))

    @Test fun oldListEmojisAreStillRecognized() = listOf(
        "😀", "😂", "😍", "😎", "😢", "😡", "👍", "👎", "❤️", "⭐"
    ).forEach { assertEquals(it, DescriptionEmoji.split(DescriptionEmoji.prepend(it, "<p>x</p>")).first) }

    @Test fun textSymbolIsNotSplitOff() =
        assertEquals(null to "<p>© 2024</p>", DescriptionEmoji.split("<p>© 2024</p>"))

    @Test fun aToneBeforeAQualifierIsRecognized() = assertEquals("👨🏻‍🦰" to "x", DescriptionEmoji.split("👨🏻‍🦰 x"))

    @Test fun aMixedToneEmojiIsRecognized() =
        assertEquals("🧑🏻‍🤝‍🧑🏼" to "x", DescriptionEmoji.split("🧑🏻‍🤝‍🧑🏼 x"))

    @Test fun aFamilyIsRecognized() = assertEquals("👨‍👩‍👧" to "x", DescriptionEmoji.split("👨‍👩‍👧 x"))

    @Test fun everyRecognizedEmojiSurvivesARoundTrip() {
        val broken = EmojiCatalog.recognized.filter {
            DescriptionEmoji.split(DescriptionEmoji.prepend(it, "<p>x</p>")).first != it
        }
        assertEquals(emptyList(), broken)
    }
}
