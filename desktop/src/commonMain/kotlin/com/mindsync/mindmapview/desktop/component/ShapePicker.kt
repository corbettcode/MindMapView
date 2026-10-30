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
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeShape
import com.mindsync.mindmapview.desktop.label

@Composable
fun ShapePicker(
    selectedShape: NodeShape
    , onSelect: (NodeShape) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
        , verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Shape", maxLines = 1, softWrap = false, modifier = Modifier.width(PICKER_LABEL_WIDTH))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("${selectedShape.label} ▾")
            }
            DropdownMenu(
                expanded = expanded
                , onDismissRequest = { expanded = false }
            ) {
                NodeShape.entries.forEach { shape ->
                    DropdownMenuItem(
                        text = { Text(shape.label) }
                        , onClick = {
                            expanded = false
                            onSelect(shape)
                        }
                    )
                }
            }
        }
    }
}
