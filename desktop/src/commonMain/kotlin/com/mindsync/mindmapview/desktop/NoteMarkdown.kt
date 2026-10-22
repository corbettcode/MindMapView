package com.mindsync.mindmapview.desktop

/** Returns the note [html] as Markdown, to be typed in. */
internal expect fun noteToMarkdown(html: String): String

/** Returns the note that [markdown] is read as. */
internal expect fun noteFromMarkdown(markdown: String): String
