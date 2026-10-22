package com.mindsync.mindmapview.desktop

import com.mohamedrejeb.richeditor.model.RichTextState

// The rich text editor's own Markdown, as the converter the desktop has runs only there.
internal actual fun noteToMarkdown(html: String): String =
    if (html.isBlank()) "" else RichTextState().setHtml(html).toMarkdown().trim()

internal actual fun noteFromMarkdown(markdown: String): String =
    if (markdown.isBlank()) "" else noteHtml(RichTextState().setMarkdown(markdown))
