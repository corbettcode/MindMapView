package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindsync.mindmapview.desktop.emojiVisualTransformation
import com.mindsync.mindmapview.desktop.parseHexColor
import com.mindsync.mindmapview.desktop.toHex

/** The width of the hex field of a [ColorEditor], and of the buttons below it. */
val COLOR_EDITOR_BUTTON_WIDTH = 140.dp

/**
 * Lets the user change [color] with the color picker or its hex value, with [buttons] below the hex value for
 * what else there is to do with it.
 */
@Composable
fun ColorEditor(
    color: Color
    , onColorChange: (Color) -> Unit
    , modifier: Modifier = Modifier
    , buttons: @Composable ColumnScope.() -> Unit = {}
) {
    // The text is kept apart from the color, so a hex value that is still being typed is not lost.
    var hexText by remember(color) { mutableStateOf(color.toHex()) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
        , modifier = modifier
    ) {
        HsvColorPicker(
            color = color
            , onColorChange = onColorChange
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hexText
                , visualTransformation = emojiVisualTransformation()
                , onValueChange = { text ->
                    hexText = text
                    parseHexColor(text)?.let(onColorChange)
                }
                , label = { Text("Hex") }
                , isError = parseHexColor(hexText) == null
                , singleLine = true
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            )
            buttons()
        }
    }
}
