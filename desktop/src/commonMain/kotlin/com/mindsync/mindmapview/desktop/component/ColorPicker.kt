package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.collections.forEach

val PICKER_LABEL_WIDTH = 56.dp

/**
 * A composable function that displays a color picker with a list of color options.
 *
 * @param label The label to display alongside the color picker.
 * @param colors A list of colors available for selection.
 * @param selectedColor The currently selected color.
 * @param onSelect A callback function that gets invoked when a color is selected. The selected color is passed as a parameter.
 * @param labelWidth The width of the label displayed alongside the color picker. It defaults to `PICKER_LABEL_WIDTH`.
 */
@Composable
fun ColorPicker(
    label: String
    , colors: List<Color>
    , selectedColor: Color
    , onSelect: (Color) -> Unit
    , labelWidth: Dp = PICKER_LABEL_WIDTH
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
        , verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(labelWidth))
        colors.forEach { color ->
            val borderColor =
                if (color == selectedColor) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (color == selectedColor) 3.dp else 1.dp, borderColor, CircleShape)
                    .clickable { onSelect(color) },
            )
        }
    }
}
