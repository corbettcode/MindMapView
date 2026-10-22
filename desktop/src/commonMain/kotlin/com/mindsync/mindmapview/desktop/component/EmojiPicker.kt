package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindsync.library.util.DescriptionEmoji
import kotlin.collections.chunked
import kotlin.collections.forEach

private const val EMOJI_PICKER_COLUMNS = 8

@Composable
fun EmojiPicker(
    selectedEmoji: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Emoji", maxLines = 1, softWrap = false, modifier = Modifier.width(PICKER_LABEL_WIDTH))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selectedEmoji ?: "None")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    },
                )
                HorizontalDivider()
                DescriptionEmoji.EMOJIS.chunked(EMOJI_PICKER_COLUMNS).forEach { emojis ->
                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                        emojis.forEach { emoji ->
                            val borderColor =
                                if (emoji == selectedEmoji) MaterialTheme.colorScheme.onSurface else Color.Transparent
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, borderColor, CircleShape)
                                    .clickable {
                                        expanded = false
                                        onSelect(emoji)
                                    },
                            ) {
                                Text(emoji)
                            }
                        }
                    }
                }
            }
        }
    }
}
