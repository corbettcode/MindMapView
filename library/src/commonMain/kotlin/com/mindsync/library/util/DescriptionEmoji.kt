package com.mindsync.library.util

import com.mindsync.library.emoji.EmojiCatalog

/**
 * Keeps an emoji in front of a node's description. The emoji is part of the description, at the start of
 * its first paragraph and followed by a space, so it is drawn, measured, and saved with the text.
 */
object DescriptionEmoji {
    /**
     * A predefined list of commonly used emojis.
     *
     * This list contains various emojis representing emotions, gestures, objects, symbols, and more.
     * It can be used for purposes such as messaging, UI components, or custom reactions.
     */
    @Deprecated("Use EmojiCatalog", ReplaceWith("EmojiCatalog.groups"))
    val EMOJIS = listOf(
        "🤩", "🤣", "🙂", "🥰", "🤠", "🤔", "🥺", "🤬",
        "🤙", "🤞", "👏", "🙏", "💪", "👀", "🧠", "💬",
        "❤️", "⭐", "🔥", "✨", "🎉", "🎯", "🏆", "🎁",
        "✅", "❌", "⚠️", "❓", "❗", "💡", "🔒", "🔑",
        "📌", "📎", "📅", "📈", "📝", "📚", "📞", "📧",
        "💰", "🏠", "🚀", "⏰", "🔧", "💊", "🩺", "🍎",
        "🌟", "🌈", "☀️", "🌙", "🌱", "🌍", "🐶", "🐱",
        "☕", "🍕", "🎵", "🎨", "⚽", "🚗", "✈️", "💻",
        "🤗", "🥲", "🥱", "🤯", "🥳", "🫣", "🤝", "🤚",
        "👋", "🙌", "💯", "💔", "💎", "🔔", "🔍", "🔗",
        "📊", "📉", "📋", "📁", "🗂️", "🛒", "🎓", "🏁",
        "🌳", "🌸", "🌊", "⚡", "❄️", "🍔", "🏃", "🎮",
    )

    private val paragraphStart = Regex("^\\s*<p(?: [^>]*)?>")
    private const val EMPTY_PARAGRAPH = "<p></p>"

    /**
     * Returns the emoji in front of [description], or null when there is none, and the description
     * without it.
     */
    fun split(description: String): Pair<String?, String> {
        val start = paragraphStart.find(description)?.value.orEmpty()
        val text = description.substring(start.length)
        val emoji = EmojiCatalog.emojiAt(text, 0) ?: return null to description
        val rest = text.substring(emoji.length).removePrefix(" ")
        return emoji to (start + rest).removePrefix(EMPTY_PARAGRAPH)
    }

    /**
     * Returns [description] with [emoji] in front of it. An emoji that is already there is replaced, and
     * removed when [emoji] is null.
     */
    fun prepend(
        emoji: String?
        , description: String
    ): String {
        val text = split(description).second
        if (emoji == null) return text

        val start = paragraphStart.find(text)?.value

        return when {
            start != null -> start + emoji + " " + text.substring(start.length)
            text.trimStart().startsWith("<") -> "<p>$emoji</p>$text"
            else -> "$emoji $text"
        }
    }
}
