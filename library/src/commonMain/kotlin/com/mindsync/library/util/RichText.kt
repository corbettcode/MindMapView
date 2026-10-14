package com.mindsync.library.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * A link in rich text: the [text] it is shown as and the [url] it leads to.
 */
data class RichTextLink(
    val text: String
    , val url: String
)

/**
 * A node's description is rich text held as an HTML string. Plain text without markup is valid HTML,
 * but `<` and `&` must be escaped and line breaks need `<br>` or separate `<p>` paragraphs.
 */
object RichText {
    /** The tag of the string annotations [parse] puts on links; the annotation's item is the URL. */
    const val LINK_TAG = "com.mindsync.library.link"

    /**
     * Returns [html] as text to draw. Links are drawn in [linkColor], or blue when none is given, and
     * each carries a string annotation tagged [LINK_TAG] with its URL.
     */
    fun parse(
        html: String
        , linkColor: Color = Color.Unspecified
    ): AnnotatedString {
        val state = RichTextState()
        if (linkColor.isSpecified) state.config.linkColor = linkColor
        val richText = state.setHtml(html).annotatedString
        // List items come with a text indent, which makes a node's text wrap when it is measured at its own width.
        val text = AnnotatedString(
            text = richText.text,
            // Font sizes are read as absolute ones, which would not follow the size of a node's text.
            spanStyles = richText.spanStyles.map { span ->
                val scale = RichTextSize.scaleOf(span.item.fontSize)
                if (scale == null) span else span.copy(item = span.item.copy(fontSize = scale.em))
            },
            paragraphStyles = richText.paragraphStyles.map { it.copy(item = it.item.copy(textIndent = TextIndent.None)) },
        )
        val links = linkRanges(state)
        if (links.isEmpty()) return text
        return AnnotatedString.Builder(text).apply {
            links.forEach { (range, url) -> addStringAnnotation(LINK_TAG, url, range.start, range.end) }
        }.toAnnotatedString()
    }

    /** Returns [text] as rich text that shows it as it is, its line breaks included. */
    fun fromPlainText(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
    }

    /** Returns the links in [html], in the order they are in the text. */
    fun links(html: String): List<RichTextLink> {
        val state = RichTextState().setHtml(html)
        val text = state.annotatedString.text
        return linkRanges(state).map { (range, url) -> RichTextLink(text.substring(range.start, range.end), url) }
    }

    /** Returns the URL of the link the character at [offset] of text returned by [parse] is in, or null. */
    fun linkAt(
        text: AnnotatedString
        , offset: Int
    ): String? {
        if (offset !in text.indices) return null
        return text.getStringAnnotations(LINK_TAG, offset, offset + 1).firstOrNull()?.item
    }

    // The editor only tells what a given range of its text is, so the links are found a character at a time.
    private fun linkRanges(state: RichTextState): List<Pair<TextRange, String>> {
        val length = state.annotatedString.text.length
        val ranges = mutableListOf<Pair<TextRange, String>>()
        var start = 0
        var url: String? = null
        for (index in 0..length) {
            val current =
                if (index < length) (state.getRichSpanStyle(TextRange(index, index + 1)) as? RichSpanStyle.Link)?.url
                else null
            if (current != url) {
                url?.let { ranges.add(TextRange(start, index) to it) }
                start = index
                url = current
            }
        }
        return ranges
    }
}
