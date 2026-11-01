package com.mindsync.library.io

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mindsync.library.util.RichText

/**
 * Turns rich text into Markdown that [Markdown.read] reads back as the same text: bold, italic, struck-through text
 * and links are kept, other styles are dropped. The importer has no backslash escapes, but keeps the text of a code
 * span as it is, so characters it would read as markup are written in code spans. A backtick cannot be written so,
 * and is written as an apostrophe.
 */
internal object MarkdownText {
    private val MARKUP = Regex("""[*_~\[\]#<>|]+""")
    private val CELL_MARKUP = Regex("""[*_~\[\]#<>]+""")
    private val LINE_START_MARKUP = Regex("""^(\s*)([-+]|\d+[.)])""")
    private val IMAGE = Regex("""<img\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val IMAGE_SOURCE = Regex("""\ssrc\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val IMAGE_ALT = Regex("""\salt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val IMAGE_MARK = Regex("""(\d+)""")

    private data class Style(
        val isBold: Boolean = false
        , val isItalic: Boolean = false
        , val isStruck: Boolean = false
    ) {
        val isPlain: Boolean get() = !isBold && !isItalic && !isStruck
    }

    private class Segment(
        val text: String
        , val style: Style
        , val url: String?
    )

    /** Returns [html] as Markdown on one line, as a heading or list item is: its line breaks become spaces. */
    fun inline(html: String): String = paragraphsOf(html, isLeadKeptBold = true).joinToString(" ")

    /**
     * Returns [html] as Markdown paragraphs, one for each of its lines. A paragraph that starts with bold text would
     * be read as a node of its own, so that text is written without bold.
     */
    fun paragraphs(html: String): List<String> = paragraphsOf(html, isLeadKeptBold = false)

    /** Returns plain [text] as the Markdown of a table cell. */
    fun cell(text: String): String = escape(text.replace("`", "'"), CELL_MARKUP, isLineStart = false)
        .replace("|", "\\|")
        .replace("\n", " ")

    private fun paragraphsOf(
        html: String
        , isLeadKeptBold: Boolean
    ): List<String> {
        val images = mutableListOf<String>()
        // Images are not text, so each one is held in the text by a mark and written as an image at the end.
        val marked = IMAGE.replace(html) { match ->
            images.add(image(match.value))
            "${images.lastIndex}"
        }
        return RichText.lines(marked).map { line ->
            val segments = segmentsOf(line).toMutableList()
            if (!isLeadKeptBold && segments.firstOrNull()?.style?.isBold == true) {
                val lead = segments.first()
                segments[0] = Segment(lead.text, lead.style.copy(isBold = false), lead.url)
            }
            IMAGE_MARK.replace(write(segments).trim()) { images[it.groupValues[1].toInt()] }
        }
    }

    private fun image(tag: String): String {
        val source = IMAGE_SOURCE.find(tag)?.groupValues?.get(1).orEmpty()
        val alt = IMAGE_ALT.find(tag)?.groupValues?.get(1).orEmpty().replace(Regex("""[\[\]`]"""), "")
        return "![$alt](${url(decodeEntities(source))})"
    }

    // The text cut wherever its style or link changes.
    private fun segmentsOf(line: AnnotatedString): List<Segment> {
        val text = line.text
        val links = line.getStringAnnotations(RichText.LINK_TAG, 0, text.length)
        val boundaries = (
            listOf(0, text.length) +
                line.spanStyles.flatMap { listOf(it.start, it.end) } +
                links.flatMap { listOf(it.start, it.end) }
            )
            .filter { it in 0..text.length }
            .distinct()
            .sorted()
        return boundaries.zipWithNext().filter { (start, end) -> end > start }.map { (start, end) ->
            val spans = line.spanStyles.filter { it.start <= start && it.end >= end }.map { it.item }
            val style = Style(
                isBold = spans.any { (it.fontWeight?.weight ?: 0) >= FontWeight.Bold.weight }
                , isItalic = spans.any { it.fontStyle == FontStyle.Italic }
                , isStruck = spans.any { it.textDecoration?.contains(TextDecoration.LineThrough) == true }
            )
            val url = links.firstOrNull { it.start <= start && it.end >= end }?.item
            Segment(text.substring(start, end), style, url)
        }
    }

    private fun write(segments: List<Segment>): String = buildString {
        var index = 0
        while (index < segments.size) {
            val url = segments[index].url
            val group = mutableListOf(segments[index++])
            while (index < segments.size && url != null && segments[index].url == url) group.add(segments[index++])
            if (url == null) {
                append(styled(group, isLineStart = isEmpty(), isLinkLabel = false))
            } else {
                append("[")
                append(styled(group, isLineStart = false, isLinkLabel = true))
                append("](${url(url)})")
            }
        }
    }

    private fun styled(
        segments: List<Segment>
        , isLineStart: Boolean
        , isLinkLabel: Boolean
    ): String = buildString {
        var open = Style()
        var italicMark = "_"
        fun close() {
            if (open.isStruck) append("~~")
            if (open.isItalic) append(italicMark)
            if (open.isBold) append("**")
            open = Style()
        }
        segments.forEachIndexed { index, segment ->
            val core = segment.text.trim()
            val lead = segment.text.substring(0, segment.text.indexOf(core.firstOrNull() ?: ' ').coerceAtLeast(0))
            val trail = segment.text.substring(lead.length + core.length)
            val text =
                if (isLinkLabel) linkLabel(core)
                else escape(core.replace("`", "'"), MARKUP, isLineStart && isEmpty())
            // A code span would split the markup around it, so text that needs one is written without style.
            val style = if (text != core) Style() else segment.style
            if (core.isEmpty()) {
                close()
                append(segment.text)
                return@forEachIndexed
            }
            if (style != open) {
                close()
                append(lead)
                val before = lastOrNull()
                val after = segments.getOrNull(index + 1)?.text?.firstOrNull()
                // The importer only reads _italic_ when it is not inside a word.
                italicMark = if (before?.isLetterOrDigit() == true || after?.isLetterOrDigit() == true) "*" else "_"
                if (style.isBold) append("**")
                if (style.isItalic) append(italicMark)
                if (style.isStruck) append("~~")
                open = style
            } else {
                append(lead)
            }
            append(text)
            if (trail.isNotEmpty()) {
                close()
                append(trail)
            }
        }
        close()
    }

    // A code span or a bracket would end the link, so markup in its label is written as it is, brackets aside.
    private fun linkLabel(text: String): String = text
        .replace("[", "(")
        .replace("]", ")")
        .replace("`", "'")

    private fun url(url: String): String = url
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")

    private fun escape(
        text: String
        , markup: Regex
        , isLineStart: Boolean
    ): String {
        val start = if (isLineStart) LINE_START_MARKUP.find(text) else null
        val head = start?.let { "${it.groupValues[1]}`${it.groupValues[2]}`" }.orEmpty()
        val rest = text.substring(start?.range?.last?.plus(1) ?: 0)
        return head + markup.replace(rest) { "`${it.value}`" }
    }
}
