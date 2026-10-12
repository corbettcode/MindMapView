package com.mindsync.library.util

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlin.math.abs

/**
 * The sizes text in a description can have, each as a scale of the normal size of a node's text.
 */
enum class RichTextSize(val scale: Float) {
    SMALL(0.8f),
    NORMAL(1f),
    LARGE(1.4f),
    ;

    /** The style that gives text this size. Normal text has no size of its own. */
    val spanStyle: SpanStyle
        get() = if (this == NORMAL) SpanStyle() else SpanStyle(fontSize = scale.em)

    companion object {
        // A size in a description is read as CSS pixels, of which a normal size has 16.
        private const val NORMAL_CSS_PIXELS = 16f

        /** The scale of [fontSize] against the normal size, or null when it has none. */
        fun scaleOf(fontSize: TextUnit): Float? = when {
            fontSize.isEm -> fontSize.value
            fontSize.isSp -> fontSize.value / NORMAL_CSS_PIXELS
            else -> null
        }

        /** The size nearest to [fontSize]. */
        fun of(fontSize: TextUnit): RichTextSize {
            val scale = scaleOf(fontSize) ?: return NORMAL
            return entries.minBy { abs(it.scale - scale) }
        }
    }
}
