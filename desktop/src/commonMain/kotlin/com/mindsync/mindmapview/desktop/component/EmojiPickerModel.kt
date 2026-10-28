package com.mindsync.mindmapview.desktop.component

import com.mindsync.library.data.Tree
import com.mindsync.library.emoji.Emoji
import com.mindsync.library.emoji.EmojiCatalog
import com.mindsync.library.emoji.EmojiGroup
import com.mindsync.library.util.DescriptionEmoji

fun emojisInMap(tree: Tree<*>): List<String> {
    val emojis = linkedSetOf<String>()
    tree.doPreorderTraversal { node ->
        DescriptionEmoji.split(node.description).first?.let { emojis += it }
    }
    return emojis.toList()
}

fun Emoji.shown(emojiTones: Map<String, String>): String = emojiTones[value] ?: value

fun startGroup(selectedEmoji: String?): EmojiGroup =
    selectedEmoji?.let { EmojiCatalog.groupOf(it) } ?: EmojiCatalog.groups.first()

val GROUP_ICONS: Map<String, String> = mapOf(
    "Smileys & Emotion" to "😀"
    , "People & Body" to "👋"
    , "Animals & Nature" to "🐶"
    , "Food & Drink" to "🍎"
    , "Travel & Places" to "🚗"
    , "Activities" to "⚽"
    , "Objects" to "💡"
    , "Symbols" to "❤️"
    , "Flags" to "🏁"
)
