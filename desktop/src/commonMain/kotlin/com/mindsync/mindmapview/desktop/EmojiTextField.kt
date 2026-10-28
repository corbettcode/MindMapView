package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.mindsync.library.util.withEmojiFont
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Shows the emojis typed into a text field in [fontFamily]. On the desktop the system's plain fonts have two-tone
 * emojis of their own, which are drawn before the color emoji font is fallen back to.
 */
class EmojiVisualTransformation(
    private val fontFamily: FontFamily
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(text.withEmojiFont(fontFamily), OffsetMapping.Identity)

    override fun equals(other: Any?): Boolean = other is EmojiVisualTransformation && other.fontFamily == fontFamily

    override fun hashCode(): Int = fontFamily.hashCode()
}

/** Shows typed emojis in the color emoji font once it has loaded. */
@Composable
fun emojiVisualTransformation(): VisualTransformation {
    val fontFamily = LocalEmojiFontFamily.current
    return remember(fontFamily) { fontFamily?.let { EmojiVisualTransformation(it) } ?: VisualTransformation.None }
}

/** Restyles text to show its emojis in [fontFamily], for a rich text editor's [RichTextState.displayStyle]. */
fun emojiDisplayStyle(
    fontFamily: FontFamily
): (AnnotatedString) -> AnnotatedString = { it.withEmojiFont(fontFamily) }

/** Shows the emojis in the editor of [state] in the color emoji font once it has loaded. */
@Composable
fun ShowEmojisInColor(state: RichTextState?) {
    val fontFamily = LocalEmojiFontFamily.current
    LaunchedEffect(state, fontFamily) {
        state?.displayStyle = fontFamily?.let { emojiDisplayStyle(it) }
    }
}
