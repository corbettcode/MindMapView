package com.mindsync.mindmapview.desktop

import com.mohamedrejeb.richeditor.model.RichTextState

// The editor writes a size of 0 for an image that was given none, which would hide it when it is read again.
private val UNSIZED_IMAGE = Regex(""" width="0\.0" height="0\.0"""")

/**
 * Returns the note in [state] as HTML, or none for an editor that was emptied: that still holds an empty
 * paragraph. An image is a note, though it has no text.
 */
internal fun noteHtml(state: RichTextState): String {
    if (state.annotatedString.text.isBlank()) return ""
    return state.toHtml().replace(UNSIZED_IMAGE, "")
}

/** Returns [html] in a new editor once its images are loaded: one that is loaded later does not show. */
internal suspend fun noteWithImages(html: String): RichTextState {
    UrlImageLoader.preload(html)
    return RichTextState().setHtml(html)
}

/** Returns the note that [source], HTML typed as it is, is read as: its markup takes effect. */
internal fun noteFromSource(source: String): String = noteHtml(RichTextState().setHtml(source))
