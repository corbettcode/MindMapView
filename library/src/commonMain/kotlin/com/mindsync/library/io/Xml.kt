package com.mindsync.library.io

internal class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    val rawContent: String?,
    // The text of the element itself, without that of its children.
    val text: String = "",
)

private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

internal fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    return ENTITY.replace(text) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x") -> entity.substring(2).toIntOrNull(16)?.let(::codePointToString)
            entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::codePointToString)
            entity == "amp" -> "&"
            entity == "lt" -> "<"
            entity == "gt" -> ">"
            entity == "quot" -> "\""
            entity == "apos" -> "'"
            entity == "nbsp" -> "\u00A0"
            else -> null
        } ?: match.value
    }
}

private fun codePointToString(codePoint: Int): String? {
    return when {
        codePoint in 0..0xFFFF -> codePoint.toChar().toString()
        codePoint <= 0x10FFFF -> {
            val offset = codePoint - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
        }
        else -> null
    }
}

/**
 * Just enough of an XML parser for mind map files: elements, their attributes and their text. The content of a
 * FreeMind <richcontent> element is HTML, so it is kept as it is written instead of being parsed.
 */
internal class XmlParser(private val source: String) {
    private var position = 0

    fun parse(): List<XmlElement> = parseElements()

    private fun parseElements(text: StringBuilder? = null): List<XmlElement> {
        val elements = mutableListOf<XmlElement>()
        while (true) {
            val next = source.indexOf('<', position)
            text?.append(decodeEntities(source.substring(position, if (next == -1) source.length else next)))
            if (next == -1) {
                position = source.length
                return elements
            }
            position = next
            when {
                source.startsWith("<!--", position) -> skipPast("-->")
                source.startsWith(CDATA_START, position) -> {
                    val end = source.indexOf(CDATA_END, position)
                    if (end == -1) throw malformed()
                    text?.append(source, position + CDATA_START.length, end)
                    position = end + CDATA_END.length
                }
                source.startsWith("<?", position) -> skipPast("?>")
                source.startsWith("<!", position) -> skipPast(">")
                source.startsWith("</", position) -> {
                    skipPast(">")
                    return elements
                }
                else -> elements.add(parseElement())
            }
        }
    }

    private fun parseElement(): XmlElement {
        position++
        val name = readWhile { !it.isWhitespace() && it != '/' && it != '>' }
        val attributes = mutableMapOf<String, String>()
        while (true) {
            skipWhitespace()
            when (peek()) {
                '>' -> {
                    position++
                    break
                }
                '/' -> {
                    skipPast(">")
                    return XmlElement(name, attributes, emptyList(), null)
                }
                else -> {
                    val attributeName = readWhile { !it.isWhitespace() && it != '=' && it != '/' && it != '>' }
                    skipWhitespace()
                    if (peek() != '=') continue
                    position++
                    skipWhitespace()
                    val quote = peek()
                    if (quote != '"' && quote != '\'') throw malformed()
                    position++
                    val end = source.indexOf(quote, position)
                    if (end == -1) throw malformed()
                    attributes[attributeName] = decodeEntities(source.substring(position, end))
                    position = end + 1
                }
            }
        }
        if (name == "richcontent") {
            val end = source.indexOf("</richcontent>", position)
            if (end == -1) throw malformed()
            val rawContent = source.substring(position, end)
            position = end + "</richcontent>".length
            return XmlElement(name, attributes, emptyList(), rawContent)
        }
        val text = StringBuilder()
        val children = parseElements(text)
        return XmlElement(name, attributes, children, null, text.toString())
    }

    private fun peek(): Char = if (position < source.length) source[position] else throw malformed()

    private fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun readWhile(predicate: (Char) -> Boolean): String {
        val start = position
        while (position < source.length && predicate(source[position])) position++
        if (position == start) throw malformed()
        return source.substring(start, position)
    }

    private fun skipPast(terminator: String) {
        val end = source.indexOf(terminator, position)
        if (end == -1) throw malformed()
        position = end + terminator.length
    }

    private fun malformed() = IllegalArgumentException("Malformed XML near character $position")

    private companion object {
        const val CDATA_START = "<![CDATA["
        const val CDATA_END = "]]>"
    }
}
