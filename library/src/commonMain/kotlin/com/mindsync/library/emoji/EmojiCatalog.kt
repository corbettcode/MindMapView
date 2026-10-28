package com.mindsync.library.emoji

data class Emoji(
    val value: String
    , val name: String
    , val subgroup: String
    , val keywords: List<String>
    , val tones: List<String>
)

data class EmojiGroup(val name: String, val emojis: List<Emoji>)

object EmojiCatalog {
    val groups: List<EmojiGroup> by lazy { parse() }

    private val baseByValue: Map<String, Emoji> by lazy {
        groups.flatMap { it.emojis }.associateBy { it.value }
    }

    private val baseByAnyValue: Map<String, Emoji> by lazy {
        buildMap {
            baseByValue.values.forEach { emoji ->
                put(emoji.value, emoji)
                emoji.tones.forEach { put(it, emoji) }
            }
        }
    }

    private val extras: Set<String> by lazy {
        EMOJI_EXTRAS.joinToString("").lineSequence().filter { it.isNotEmpty() }.toSet()
    }

    private val skinTones = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

    internal val recognized: Set<String> by lazy { baseByAnyValue.keys + extras }

    private val groupByValue: Map<String, EmojiGroup> by lazy {
        groups.flatMap { group -> group.emojis.map { it.value to group } }.toMap()
    }

    private val longestEmoji: Int by lazy { recognized.maxOf { it.length } }

    private val searchWords: List<Pair<Emoji, List<String>>> by lazy {
        groups.flatMap { it.emojis }.map { emoji ->
            emoji to (emoji.name.split(' ') + emoji.keywords.flatMap { it.split(' ') }).map { it.lowercase() }
        }
    }

    fun find(emoji: String): Emoji? = baseByAnyValue[emoji] ?: emoji
        .takeIf { it in extras }
        ?.let { extra -> baseByValue[skinTones.fold(extra) { value, tone -> value.replace(tone, "") }] }

    fun groupOf(emoji: String): EmojiGroup? = find(emoji)?.let { groupByValue[it.value] }

    fun emojiAt(
        text: String
        , index: Int
    ): String? {
        if (index !in text.indices) return null
        val longest = minOf(longestEmoji, text.length - index)
        return (longest downTo 1)
            .map { text.substring(index, index + it) }
            .firstOrNull { it in recognized }
    }

    fun findEmojis(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 0
        while (index < text.length) {
            val emoji = emojiAt(text, index)
            if (emoji == null) {
                index++
            } else {
                ranges += index until index + emoji.length
                index += emoji.length
            }
        }
        return ranges
    }

    fun search(query: String): List<Emoji> {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (queryWords.isEmpty()) return emptyList()
        return searchWords
            .filter { (_, words) -> queryWords.all { queryWord -> words.any { it.startsWith(queryWord) } } }
            .map { it.first }
    }

    private fun parse(): List<EmojiGroup> {
        val byGroup = LinkedHashMap<String, MutableList<Emoji>>()
        EMOJI_DATA.joinToString("").lineSequence().filter { it.isNotEmpty() }.forEach { line ->
            val fields = line.split('\t')
            byGroup.getOrPut(fields[0]) { mutableListOf() } += Emoji(
                value = fields[2]
                , name = fields[3]
                , subgroup = fields[1]
                , keywords = fields[4].split('|').filter { it.isNotEmpty() }
                , tones = fields[5].split(' ').filter { it.isNotEmpty() }
            )
        }
        return byGroup.map { EmojiGroup(it.key, it.value) }
    }
}
