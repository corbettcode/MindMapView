package com.mindsync.library.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiCatalogTest {
    @Test fun groupsFollowUnicodeOrderWithoutComponent() = assertEquals(
        listOf("Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink", "Travel & Places"
            , "Activities", "Objects", "Symbols", "Flags")
        , EmojiCatalog.groups.map { it.name }
    )

    @Test fun everyFaceIsThere() = assertTrue(EmojiCatalog.groups[0].emojis.size >= 160)

    @Test fun thumbsUpHasFiveTones() =
        assertEquals(listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), EmojiCatalog.find("👍")!!.tones)

    @Test fun aToneVariantFindsItsBase() = assertEquals("👍", EmojiCatalog.find("👍🏽")!!.value)

    @Test fun twoPersonEmojiOffersOnlySingleTones() =
        assertEquals("🧑🏻‍🤝‍🧑🏻", EmojiCatalog.find("🧑‍🤝‍🧑")!!.tones.first())

    @Test fun facesHaveNoTones() = assertEquals(emptyList(), EmojiCatalog.find("😀")!!.tones)

    @Test fun keywordsComeFromCldr() = assertTrue("grin" in EmojiCatalog.find("😀")!!.keywords)

    @Test fun gesturesAreInPeopleAndBody() = assertEquals("People & Body", EmojiCatalog.groupOf("👋🏽")?.name)

    @Test fun emojiAtTakesTheLongest() {
        assertEquals("👨‍👩‍👧", EmojiCatalog.emojiAt("👨‍👩‍👧 x", 0))
        assertEquals("👍🏽", EmojiCatalog.emojiAt("a👍🏽", 1))
    }

    @Test fun findEmojisGivesRanges() = assertEquals(listOf(2..5, 9..10), EmojiCatalog.findEmojis("a 👍🏽 b ❤️"))

    @Test fun textSymbolsAreNotEmojis() {
        assertEquals(emptyList(), EmojiCatalog.findEmojis("© 2024 #1 1. step"))
        assertNull(EmojiCatalog.emojiAt("🏽", 0))
    }

    @Test fun searchMatchesNamesAndKeywords() {
        val thumbs = EmojiCatalog.search("thumbs").map { it.value }
        assertTrue("👍" in thumbs && "👎" in thumbs)
        assertTrue(EmojiCatalog.search("happy").isNotEmpty())
        assertEquals(emptyList(), EmojiCatalog.search("   "))
    }

    @Test fun searchIgnoresCaseAndSpacing() =
        assertEquals(EmojiCatalog.search("thumbs up"), EmojiCatalog.search("  Thumbs   UP "))
}
