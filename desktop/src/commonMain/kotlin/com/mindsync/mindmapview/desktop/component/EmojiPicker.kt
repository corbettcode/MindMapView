package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsync.library.emoji.Emoji
import com.mindsync.library.emoji.EmojiCatalog
import com.mindsync.mindmapview.desktop.LocalEmojiFontFamily
import com.mindsync.mindmapview.desktop.emojiVisualTransformation

private const val EMOJI_PICKER_COLUMNS = 8
private const val EMOJI_PICKER_ROWS = 6
internal val EMOJI_CELL_SIZE = 36.dp
internal val EMOJI_CELL_FONT_SIZE = 20.sp
private val EMOJI_GRID_WIDTH = EMOJI_CELL_SIZE * EMOJI_PICKER_COLUMNS
private val EMOJI_GRID_HEIGHT = EMOJI_CELL_SIZE * EMOJI_PICKER_ROWS

/**
 * A composable function that provides an emoji picker allowing the user to select emojis
 * with optional tone variations.
 *
 * @param selectedEmoji The currently selected emoji, or `null` if no emoji is selected.
 * @param mapEmojis A list of emojis attached to the current map for selection.
 * @param emojiTones A mapping of base emojis to their tone variants.
 * @param onToneChange A callback function triggered when a tone variant is selected.
 *                     Receives the base emoji and the selected tone variant as parameters.
 * @param onSelect A callback function invoked when an emoji is selected or deselected.
 *                 Receives the selected emoji or `null` if no emoji is selected.
 */
@Composable
fun EmojiPicker(
    selectedEmoji: String?
    , mapEmojis: List<String>
    , emojiTones: Map<String, String>
    , onToneChange: (base: String, variant: String) -> Unit
    , onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val emojiStyle = emojiTextStyle()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
        , verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Emoji", maxLines = 1, softWrap = false, modifier = Modifier.width(PICKER_LABEL_WIDTH))
        Box {
            TextButton(onClick = { expanded = true }) {
                if (selectedEmoji == null) Text("None") else Text(selectedEmoji, style = emojiStyle)
            }
            DropdownMenu(
                expanded = expanded
                , onDismissRequest = { expanded = false }
            ) {
                EmojiPickerContent(
                    selectedEmoji = selectedEmoji
                    , mapEmojis = mapEmojis
                    , emojiTones = emojiTones
                    , onToneChange = onToneChange
                    , onSelect = {
                        expanded = false
                        onSelect(it)
                    }
                )
            }
        }
    }
}

/**
 * Displays the content for an emoji picker, allowing users to search, select emojis,
 * and manage emoji tones.
 *
 * @param selectedEmoji The currently selected emoji, or null if no emoji is selected.
 * @param mapEmojis A list of emojis that are part of a specific map or context.
 * @param emojiTones A map where keys are base emojis and values are their respective tone variants.
 * @param onToneChange Callback invoked when the tone of an emoji changes, accepting
 *                     the base emoji and the selected variant.
 * @param onSelect Callback invoked when an emoji is selected or deselected,
 *                 receiving the selected emoji or null.
 */
@Composable
private fun EmojiPickerContent(
    selectedEmoji: String?
    , mapEmojis: List<String>
    , emojiTones: Map<String, String>
    , onToneChange: (base: String, variant: String) -> Unit
    , onSelect: (String?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(startGroup(selectedEmoji)) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    @Composable
    fun EmojiGrid(emojis: List<Emoji>) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(EMOJI_PICKER_COLUMNS)
            , modifier = Modifier
                .width(EMOJI_GRID_WIDTH)
                .height(EMOJI_GRID_HEIGHT)
        ) {
            items(emojis, key = { it.value }) { emoji ->
                EmojiToneCell(
                    emoji = emoji
                    , shown = emoji.shown(emojiTones)
                    , isSelected = selectedEmoji == emoji.value || selectedEmoji in emoji.tones
                    , onSelect = onSelect
                    , onToneSelect = { variant ->
                        onToneChange(emoji.value, variant)
                        onSelect(variant)
                    }
                )
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        OutlinedTextField(
            value = query
            , visualTransformation = emojiVisualTransformation()
            , onValueChange = { query = it }
            , placeholder = { Text("Search") }
            , singleLine = true
            , modifier = Modifier
                .width(EMOJI_GRID_WIDTH)
                .focusRequester(searchFocusRequester)
        )
    }
    DropdownMenuItem(
        text = { Text("None") }
        , onClick = { onSelect(null) }
    )
    HorizontalDivider()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
        , modifier = Modifier.padding(8.dp)
    ) {
        if (mapEmojis.isNotEmpty()) {
            Text(
                "In this map"
                , style = MaterialTheme.typography.labelMedium
            )
            mapEmojis.chunked(EMOJI_PICKER_COLUMNS).forEach { emojis ->
                Row {
                    emojis.forEach { emoji ->
                        EmojiCell(
                            emoji = emoji
                            , isSelected = emoji == selectedEmoji
                            , modifier = Modifier.clickable { onSelect(emoji) }
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.width(EMOJI_GRID_WIDTH))
        }
        if (query.isBlank()) {
            GroupTabs(
                selectedGroup = group.name
                , onSelect = { name -> EmojiCatalog.groups.firstOrNull { it.name == name }?.let { group = it } }
            )
            // Keyed by the group, so another tab starts at its top rather than where the last one was scrolled to.
            key(group.name) {
                EmojiGrid(group.emojis)
            }
        } else {
            val results = remember(query) { EmojiCatalog.search(query) }
            if (results.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center
                    , modifier = Modifier
                        .width(EMOJI_GRID_WIDTH)
                        .height(EMOJI_GRID_HEIGHT)
                ) {
                    Text(
                        "No emojis found"
                        , style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                key(query) {
                    EmojiGrid(results)
                }
            }
        }
    }
}

@Composable
private fun GroupTabs(
    selectedGroup: String
    , onSelect: (String) -> Unit
) {
    val emojiStyle = emojiTextStyle()
    Row(modifier = Modifier.width(EMOJI_GRID_WIDTH)) {
        GROUP_ICONS.forEach { (name, icon) ->
            val background =
                if (name == selectedGroup) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            Box(
                contentAlignment = Alignment.Center
                , modifier = Modifier
                    .weight(1f)
                    .height(EMOJI_CELL_SIZE)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .clickable { onSelect(name) }
            ) {
                Text(icon, style = emojiStyle)
            }
        }
    }
}

/** A cell of an emoji, outlined when it is the one picked. */
@Composable
internal fun EmojiCell(
    emoji: String
    , isSelected: Boolean
    , modifier: Modifier = Modifier
    , content: @Composable () -> Unit = {}
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        contentAlignment = Alignment.Center
        , modifier = Modifier
            .size(EMOJI_CELL_SIZE)
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .then(modifier)
    ) {
        Text(emoji, style = emojiTextStyle())
        content()
    }
}

@Composable
internal fun emojiTextStyle(): TextStyle = TextStyle(
    fontFamily = LocalEmojiFontFamily.current
    , fontSize = EMOJI_CELL_FONT_SIZE
)
