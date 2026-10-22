package com.mindsync.mindmapview.desktop.component

import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.SpanStyle

// The toggles do not take focus, so the editor keeps its selection while a style is applied.
@Composable
fun RichTextToggle(
    label: String,
    labelStyle: SpanStyle,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        modifier = Modifier.focusProperties { canFocus = false },
    ) {
        Text(label, style = LocalTextStyle.current.merge(labelStyle))
    }
}
