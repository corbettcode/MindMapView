package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A [label] beside a button that shows the [selectedChoice] and opens a menu of the [choices].
 *
 * @param choiceFontFamily The font a choice's label is drawn in, or null for the usual one.
 */
@Composable
fun <T> DropdownPicker(
    label: String
    , choices: List<T>
    , choiceLabel: (T) -> String
    , selectedChoice: T
    , onSelect: (T) -> Unit
    , labelWidth: Dp = PICKER_LABEL_WIDTH
    , choiceFontFamily: (T) -> FontFamily? = { null }
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
        , verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, maxLines = 1, softWrap = false, modifier = Modifier.width(labelWidth))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("${choiceLabel(selectedChoice)} ▾", fontFamily = choiceFontFamily(selectedChoice))
            }
            DropdownMenu(
                expanded = expanded
                , onDismissRequest = { expanded = false }
            ) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choiceLabel(choice), fontFamily = choiceFontFamily(choice)) }
                        , onClick = {
                            expanded = false
                            onSelect(choice)
                        }
                    )
                }
            }
        }
    }
}
