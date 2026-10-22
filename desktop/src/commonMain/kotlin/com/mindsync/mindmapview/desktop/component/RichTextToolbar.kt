package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mindsync.library.util.RichTextSize
import com.mindsync.mindmapview.desktop.dialog.LinkDialog
import com.mindsync.mindmapview.desktop.setLink
import com.mindsync.mindmapview.desktop.setSize
import com.mohamedrejeb.richeditor.model.RichTextState

@Composable
fun RichTextToolbar(state: RichTextState) {
    val spanStyle = state.currentSpanStyle
    val decoration = spanStyle.textDecoration ?: TextDecoration.None
    var linkDialogOpen by remember { mutableStateOf(false) }

    if (linkDialogOpen) {
        LinkDialog(
            url = state.selectedLinkUrl,
            onSubmit = { state.setLink(it) },
            onRemove = { state.removeLink() },
            onDismiss = { linkDialogOpen = false },
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RichTextToggle(
            label = "B",
            labelStyle = SpanStyle(fontWeight = FontWeight.Bold),
            checked = spanStyle.fontWeight == FontWeight.Bold,
            onToggle = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
        )
        RichTextToggle(
            label = "I",
            labelStyle = SpanStyle(fontStyle = FontStyle.Italic),
            checked = spanStyle.fontStyle == FontStyle.Italic,
            onToggle = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
        )
        RichTextToggle(
            label = "U",
            labelStyle = SpanStyle(textDecoration = TextDecoration.Underline),
            checked = TextDecoration.Underline in decoration,
            onToggle = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
        )
        RichTextToggle(
            label = "S",
            labelStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
            checked = TextDecoration.LineThrough in decoration,
            onToggle = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) },
        )
        RichTextToggle(
            label = "\u2022",
            labelStyle = SpanStyle(),
            checked = state.isUnorderedList,
            onToggle = { state.toggleUnorderedList() },
        )
        RichTextToggle(
            label = "1.",
            labelStyle = SpanStyle(),
            checked = state.isOrderedList,
            onToggle = { state.toggleOrderedList() },
        )
        RichTextToggle(
            label = "\uD83D\uDD17",
            labelStyle = SpanStyle(),
            checked = state.isLink,
            onToggle = { linkDialogOpen = true },
        )
        RichTextSizeSelect(
            selectedSize = RichTextSize.of(spanStyle.fontSize),
            onSelect = { state.setSize(it) },
        )
    }
}
