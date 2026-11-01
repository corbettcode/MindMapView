package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeBorderStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.mindmapview.desktop.dialog.ChoicePicker

private val BORDER_STYLE_LABELS = mapOf(
    NodeBorderStyle.NONE to "None"
    , NodeBorderStyle.LINE to "Line"
)

/**
 * Lets the user choose a node's border: its type, its width and its color, which is that of the node's text
 * unless another is chosen.
 *
 * @param border The node's border.
 * @param colors The colors offered for the border, besides that of the text.
 * @param onChange A callback receiving the border as changed.
 * @param modifier The modifier for the settings, one above the other with a line between each.
 */
@Composable
fun BorderSettings(
    border: NodeBorder
    , colors: List<Color>
    , onChange: (NodeBorder) -> Unit
    , modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
        , modifier = modifier
    ) {
        ChoicePicker(
            label = "Type"
            , choices = NodeBorderStyle.entries
            , choiceLabel = { BORDER_STYLE_LABELS.getValue(it) }
            , selectedChoice = border.style
            , onSelect = { onChange(border.copy(style = it)) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ChoicePicker(
            label = "Width"
            , choices = NodeLineWidth.entries
            , choiceLabel = { width -> width.label.replaceFirstChar { it.uppercaseChar() } }
            , selectedChoice = border.width
            , onSelect = { onChange(border.copy(width = it)) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        OptionalColorPicker(
            noColorLabel = "Text"
            , colors = colors
            , selectedColor = border.color
            , onSelect = { onChange(border.copy(color = it)) }
        )
    }
}
