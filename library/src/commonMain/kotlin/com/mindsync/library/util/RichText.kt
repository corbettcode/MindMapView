package com.mindsync.library.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * A node's description is rich text held as an HTML string. Plain text without markup is valid HTML,
 * but `<` and `&` must be escaped and line breaks need `<br>` or separate `<p>` paragraphs.
 */
object RichText {
    fun parse(html: String): AnnotatedString {
        val richText = RichTextState().setHtml(html).annotatedString
        // List items come with a text indent, which makes a node's text wrap when it is measured at its own width.
        return AnnotatedString(
            text = richText.text,
            // Font sizes are read as absolute ones, which would not follow the size of a node's text.
            spanStyles = richText.spanStyles.map { span ->
                val scale = RichTextSize.scaleOf(span.item.fontSize)
                if (scale == null) span else span.copy(item = span.item.copy(fontSize = scale.em))
            },
            paragraphStyles = richText.paragraphStyles.map { it.copy(item = it.item.copy(textIndent = TextIndent.None)) },
        )
    }
}
