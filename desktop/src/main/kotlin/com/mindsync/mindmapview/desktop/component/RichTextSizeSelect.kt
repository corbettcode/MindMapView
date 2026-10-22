package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.window.PopupProperties
import com.mindsync.library.util.RichTextSize
import com.mindsync.mindmapview.desktop.label

// Like the toggles, the select does not take focus, so the editor keeps its selection while a size is chosen.
@Composable
fun RichTextSizeSelect(
    selectedSize: RichTextSize
    , onSelect: (RichTextSize) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.focusProperties { canFocus = false },
        ) {
            Text("${selectedSize.label} \u25BE")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
        ) {
            RichTextSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(size.label, style = LocalTextStyle.current.merge(size.spanStyle)) },
                    onClick = {
                        expanded = false
                        onSelect(size)
                    },
                )
            }
        }
    }
}
