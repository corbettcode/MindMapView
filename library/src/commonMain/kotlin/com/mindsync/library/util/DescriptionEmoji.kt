package com.mindsync.library.util

/**
 * Keeps an emoji in front of a node's description. The emoji is part of the description, at the start of
 * its first paragraph and followed by a space, so it is drawn, measured and saved with the text.
 */
object DescriptionEmoji {
    val EMOJIS = listOf(
        "😀", "😂", "🙂", "😍", "😎", "🤔", "😢", "😡",
        "👍", "👎", "👏", "🙏", "💪", "👀", "🧠", "💬",
        "❤️", "⭐", "🔥", "✨", "🎉", "🎯", "🏆", "🎁",
        "✅", "❌", "⚠️", "❓", "❗", "💡", "🔒", "🔑",
        "📌", "📎", "📅", "📈", "📝", "📚", "📞", "📧",
        "💰", "🏠", "🚀", "⏰", "🔧", "💊", "🩺", "🍎",
        "🌟", "🌈", "☀️", "🌙", "🌱", "🌍", "🐶", "🐱",
        "☕", "🍕", "🎵", "🎨", "⚽", "🚗", "✈️", "💻",
    )

    // An emoji that starts with another one has to be looked for first.
    private val longestFirst = EMOJIS.sortedByDescending { it.length }
    private val paragraphStart = Regex("^\\s*<p(?: [^>]*)?>")
    private const val EMPTY_PARAGRAPH = "<p></p>"

    /**
     * Returns the emoji in front of [description], or null when there is none, and the description
     * without it.
     */
    fun split(description: String): Pair<String?, String> {
        val start = paragraphStart.find(description)?.value.orEmpty()
        val text = description.substring(start.length)
        val emoji = longestFirst.firstOrNull { text.startsWith(it) } ?: return null to description
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
