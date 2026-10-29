package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.mindmapview.desktop.dialog.ChoicePicker

private const val INHERITED = "Inherited"

internal val LINE_STYLE_LABELS: Map<NodeLineStyle?, String> = mapOf(
    null to INHERITED
    , NodeLineStyle.LINEAR to "Linear"
    , NodeLineStyle.BEZIER to "Bezier"
    , NodeLineStyle.SHARP_LINEAR to "Sharp linear"
    , NodeLineStyle.SHARP_BEZIER to "Sharp bezier"
)

internal val LINE_WIDTH_LABELS: Map<NodeLineWidth?, String> =
    mapOf<NodeLineWidth?, String>(null to INHERITED) + NodeLineWidth.entries.associateWith { width ->
        width.label.replaceFirstChar { it.uppercaseChar() }
    }

/**
 * Lets the user choose a node's line style, width and color, each of which may be left to be inherited from the
 * node's parent.
 *
 * @param line The node's own line settings, whose null values are inherited.
 * @param colors The colors offered for the line.
 * @param onChange A callback receiving the line settings as changed.
 * @param modifier The modifier for the settings, one above the other with a line between each.
 */
@Composable
fun LineSettings(
    line: NodeLine
    , colors: List<Color>
    , onChange: (NodeLine) -> Unit
    , modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
        , modifier = modifier
    ) {
        ChoicePicker(
            label = "Type"
            , choices = LINE_STYLE_LABELS.keys.toList()
            , choiceLabel = { LINE_STYLE_LABELS.getValue(it) }
            , selectedChoice = line.style
            , onSelect = { onChange(line.copy(style = it)) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ChoicePicker(
            label = "Width"
            , choices = LINE_WIDTH_LABELS.keys.toList()
            , choiceLabel = { LINE_WIDTH_LABELS.getValue(it) }
            , selectedChoice = line.width
            , onSelect = { onChange(line.copy(width = it)) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LineColorPicker(
            colors = colors
            , selectedColor = line.color
            , onSelect = { onChange(line.copy(color = it)) }
        )
    }
}

// Like [ColorPicker], with an inherited choice first, which is why the color may be null.
@Composable
private fun LineColorPicker(
    colors: List<Color>
    , selectedColor: Color?
    , onSelect: (Color?) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
        , verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Color", modifier = Modifier.width(PICKER_LABEL_WIDTH))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
            , verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chipShape = RoundedCornerShape(8.dp)
            val isInherited = selectedColor == null
            Text(
                INHERITED
                , modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clip(chipShape)
                    .border(selectionBorderWidth(isInherited), selectionBorderColor(isInherited), chipShape)
                    .clickable { onSelect(null) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            colors.forEach { color ->
                val isSelected = color == selectedColor
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(selectionBorderWidth(isSelected), selectionBorderColor(isSelected), CircleShape)
                        .clickable { onSelect(color) }
                )
            }
        }
    }
}

private fun selectionBorderWidth(
    isSelected: Boolean
) = if (isSelected) 3.dp else 1.dp

@Composable
private fun selectionBorderColor(
    isSelected: Boolean
) = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
