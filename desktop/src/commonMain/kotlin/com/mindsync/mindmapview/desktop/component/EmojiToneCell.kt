package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.mindsync.library.emoji.Emoji

private val TONE_POPUP_PADDING = 4.dp
private val TONE_POPUP_GAP = 4.dp
private val TONE_MARK_SIZE = 6.dp
private val TONE_CELL_SIZE = EMOJI_CELL_SIZE

/**
 * A cell of the picker's grid. An emoji that comes in skin tones has a mark in its corner, and a long press or a
 * right-click on it opens a popup above it with its versions.
 */
@Composable
internal fun EmojiToneCell(
    emoji: Emoji
    , shown: String
    , isSelected: Boolean
    , onSelect: (String) -> Unit
    , onToneSelect: (String) -> Unit
) {
    if (emoji.tones.isEmpty()) {
        EmojiCell(
            emoji = shown
            , isSelected = isSelected
            , modifier = Modifier.clickable { onSelect(shown) }
        )
        return
    }
    val versions = remember(emoji) { listOf(emoji.value) + emoji.tones }
    var isToneOpen by remember { mutableStateOf(false) }
    // The version under the pointer while a long press is held, picked once it is let go.
    var heldVersion by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val position = remember(density) { TonePopupPosition(with(density) { TONE_POPUP_GAP.roundToPx() }) }
    val currentShown by rememberUpdatedState(shown)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnToneSelect by rememberUpdatedState(onToneSelect)
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant

    EmojiCell(
        emoji = shown
        , isSelected = isSelected
        , modifier = Modifier.pointerInput(emoji) {
            awaitEachGesture {
                // Not awaitFirstDown, which leaves out every mouse button but the primary one.
                var event: PointerEvent
                do {
                    event = awaitPointerEvent()
                } while (!event.changes.all { it.changedToDownIgnoreConsumed() })
                val down = event.changes.first()
                if (currentEvent.buttons.isSecondaryPressed) {
                    down.consume()
                    isToneOpen = true
                    return@awaitEachGesture
                }
                var isCancelled = false
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation().also { if (it == null) isCancelled = true }
                }
                when {
                    up != null -> {
                        up.consume()
                        currentOnSelect(currentShown)
                    }
                    !isCancelled -> {
                        isToneOpen = true
                        val cellSize = TONE_CELL_SIZE.toPx()
                        val padding = TONE_POPUP_PADDING.toPx()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            heldVersion = position.versionAt(change.position, cellSize, padding, versions.size)
                            if (!change.pressed) break
                        }
                        heldVersion?.let {
                            heldVersion = null
                            isToneOpen = false
                            currentOnToneSelect(versions[it])
                        }
                    }
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.size(EMOJI_CELL_SIZE)) {
            val mark = TONE_MARK_SIZE.toPx()
            val inset = size.width * 0.15f
            val corner = Offset(size.width - inset, size.height - inset)
            drawPath(
                Path().apply {
                    moveTo(corner.x, corner.y - mark)
                    lineTo(corner.x, corner.y)
                    lineTo(corner.x - mark, corner.y)
                    close()
                }
                , color = markColor
            )
        }
        if (isToneOpen) {
            Popup(
                popupPositionProvider = position
                , onDismissRequest = {
                    isToneOpen = false
                    heldVersion = null
                }
            ) {
                TonePopup(
                    versions = versions
                    , heldVersion = heldVersion
                    , onSelect = { variant ->
                        isToneOpen = false
                        currentOnToneSelect(variant)
                    }
                )
            }
        }
    }
}

@Composable
private fun TonePopup(
    versions: List<String>
    , heldVersion: Int?
    , onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp)
        , tonalElevation = 3.dp
        , shadowElevation = 3.dp
    ) {
        Row(modifier = Modifier.padding(TONE_POPUP_PADDING)) {
            versions.forEachIndexed { index, version ->
                val background =
                    if (index == heldVersion) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                Box(
                    contentAlignment = Alignment.Center
                    , modifier = Modifier
                        .size(TONE_CELL_SIZE)
                        .clip(CircleShape)
                        .background(background)
                        .clickable { onSelect(version) }
                ) {
                    Text(version, style = emojiTextStyle())
                }
            }
        }
    }
}

/**
 * Places the tone popup centered above its cell, and keeps where both went, so a pointer held down on the cell
 * can be matched to the version of the popup it is over.
 */
private class TonePopupPosition(private val gap: Int) : PopupPositionProvider {
    private var anchor = IntOffset.Zero
    private var popup = IntOffset.Zero
    private var popupSize = IntSize.Zero

    override fun calculatePosition(
        anchorBounds: IntRect
        , windowSize: IntSize
        , layoutDirection: LayoutDirection
        , popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        anchor = anchorBounds.topLeft
        popup = IntOffset(x, y)
        popupSize = popupContentSize
        return popup
    }

    /** The index of the version at [cellPosition], a position in the cell, or null when it is over none. */
    fun versionAt(
        cellPosition: Offset
        , cellSize: Float
        , padding: Float
        , count: Int
    ): Int? {
        if (popupSize == IntSize.Zero) return null
        val x = anchor.x + cellPosition.x - popup.x - padding
        val y = anchor.y + cellPosition.y - popup.y
        if (y !in 0f..popupSize.height.toFloat() || x < 0f) return null
        return (x / cellSize).toInt().takeIf { it < count }
    }
}
