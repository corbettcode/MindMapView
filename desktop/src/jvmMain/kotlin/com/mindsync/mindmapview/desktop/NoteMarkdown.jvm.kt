package com.mindsync.mindmapview.desktop

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.html2md.converter.HtmlMarkdownWriter
import com.vladsch.flexmark.html2md.converter.HtmlNodeConverterContext
import com.vladsch.flexmark.html2md.converter.HtmlNodeRenderer
import com.vladsch.flexmark.html2md.converter.HtmlNodeRendererHandler
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.jsoup.nodes.Element

private val MARKDOWN_OPTIONS = MutableDataSet().apply {
    set(Parser.EXTENSIONS, listOf(StrikethroughExtension.create()))
    // A note may hold _ around a word as it is, which the Markdown of the note does not escape.
    set(Parser.UNDERSCORE_DELIMITER_PROCESSOR, false)
}
private val MARKDOWN_PARSER = Parser.builder(MARKDOWN_OPTIONS).build()
private val HTML_RENDERER = HtmlRenderer.builder(MARKDOWN_OPTIONS).build()

private val HTML_CONVERTER = FlexmarkHtmlConverter.builder().htmlNodeRendererFactory { NoteNodeRenderer }.build()

private val STRIKETHROUGH_TAG = Regex("<(/?)s>")
private val MARKDOWN_DESTINATION_CHARACTERS = Regex("[\\s()<>]")
private val MARKDOWN_ALT_CHARACTERS = Regex("([\\\\\\[\\]])")

internal actual fun noteToMarkdown(html: String): String {
    if (html.isBlank()) return ""
    // The note is written as the editor writes it, and the converter only knows strikethrough by its other tag.
    return HTML_CONVERTER.convert(noteFromSource(html).replace(STRIKETHROUGH_TAG, "<$1del>")).trim()
}

internal actual fun noteFromMarkdown(markdown: String): String {
    if (markdown.isBlank()) return ""
    return noteFromSource(HTML_RENDERER.render(MARKDOWN_PARSER.parse(markdown)))
}

/**
 * Writes what the converter would get wrong in a note. Markdown has no underline or text size, so those are
 * kept as the HTML they are, which Markdown takes too. An image whose file is named as an emoji would be
 * written as that emoji, and one with a size is kept as HTML, as Markdown has no size for one.
 */
private object NoteNodeRenderer : HtmlNodeRenderer {
    override fun getHtmlNodeRendererHandlers(): Set<HtmlNodeRendererHandler<*>> = setOf(
        HtmlNodeRendererHandler("u", Element::class.java, ::renderAsHtml),
        HtmlNodeRendererHandler("span", Element::class.java, ::renderAsHtml),
        HtmlNodeRendererHandler("img", Element::class.java) { image, _, markdown ->
            if (image.hasAttr("width") || image.hasAttr("height")) {
                markdown.append(image.outerHtml())
            }
            else {
                val source = image.attr("src")
                val destination = if (MARKDOWN_DESTINATION_CHARACTERS.containsMatchIn(source)) "<$source>" else source
                markdown.append("![").append(image.attr("alt").replace(MARKDOWN_ALT_CHARACTERS, "\\\\$1"))
                    .append("](").append(destination).append(")")
            }
        },
    )

    // The tag is written as it is, around its content as Markdown.
    private fun renderAsHtml(
        element: Element,
        context: HtmlNodeConverterContext,
        markdown: HtmlMarkdownWriter,
    ) {
        markdown.append("<").append(element.tagName()).append(element.attributes().html()).append(">")
        context.renderChildren(element, false, null)
        markdown.append("</").append(element.tagName()).append(">")
    }
}
