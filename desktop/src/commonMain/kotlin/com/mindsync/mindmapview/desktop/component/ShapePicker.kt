package com.mindsync.mindmapview.desktop.component

import androidx.compose.runtime.Composable
import com.corbettcode.mindmapview.library.data.NodeShape
import com.mindsync.mindmapview.desktop.label

@Composable
fun ShapePicker(
    selectedShape: NodeShape
    , onSelect: (NodeShape) -> Unit
) {
    DropdownPicker(
        label = "Shape"
        , choices = NodeShape.entries
        , choiceLabel = { it.label }
        , selectedChoice = selectedShape
        , onSelect = onSelect
    )
}
