package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * A bar to find nodes by their text, as a browser finds text in a page. Each time [focusRequest] changes, the
 * field takes the focus and its text is selected, to type over it. Enter goes to the next match and
 * Shift+Enter to the previous one, and Esc closes the bar.
 */
@Composable
internal fun FindBar(
    text: String,
    onTextChange: (String) -> Unit,
    countText: String,
    hasMatches: Boolean,
    focusRequest: Int,
    onFocusChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        value = value.copy(selection = TextRange(0, value.text.length))
        focusRequester.requestFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = value,
            visualTransformation = emojiVisualTransformation(),
            onValueChange = { changed ->
                value = changed
                if (changed.text != text) onTextChange(changed.text)
            },
            label = { Text("Find") },
            singleLine = true,
            isError = text.isNotBlank() && !hasMatches,
            modifier = Modifier
                .width(320.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) }
                // Taken before the window gets it, where Esc would clear the selection of the map.
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                            if (event.isShiftPressed) onPrevious() else onNext()
                            true
                        }
                        event.key == Key.Escape -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                },
        )
        Text(
            if (text.isBlank()) "" else countText,
            color = if (text.isNotBlank() && !hasMatches) MaterialTheme.colorScheme.error else Color.Unspecified,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp).width(96.dp),
        )
        TextButton(onClick = onPrevious, enabled = hasMatches) { Text("▲") }
        TextButton(onClick = onNext, enabled = hasMatches) { Text("▼") }
        TextButton(onClick = onClose) { Text("✕") }
    }
}
