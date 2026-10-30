package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Lays out a dialog with [settings] above a [note] that takes the room they leave, at least [noteMinHeight], and
 * [buttons] at the bottom. In a dialog too small for that, the note keeps its least height and the settings and note
 * scroll, while the buttons stay in view. Without a [note], as one that is closed, the settings take the room. The
 * height the content needs at the least is passed to [onContentMinHeightChange], for the dialog to be fitted to it.
 */
@Composable
fun DialogLayout(
    noteMinHeight: Dp
    , gap: Dp
    , contentPadding: PaddingValues
    , onContentMinHeightChange: (Dp) -> Unit
    , settings: @Composable ColumnScope.() -> Unit
    , note: (@Composable (Modifier) -> Unit)?
    , buttons: @Composable RowScope.() -> Unit
    , modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // The settings are laid out at their own height, which does not depend on the note's, so the note's height
    // follows from them in one pass.
    var settingsHeight by remember { mutableStateOf<Dp?>(null) }
    var buttonsHeight by remember { mutableStateOf<Dp?>(null) }

    val hasNote = note != null
    LaunchedEffect(settingsHeight, buttonsHeight, hasNote, noteMinHeight) {
        val settings = settingsHeight ?: return@LaunchedEffect
        val buttons = buttonsHeight ?: return@LaunchedEffect
        val noteRoom = if (hasNote) gap + noteMinHeight else 0.dp
        onContentMinHeightChange(
            contentPadding.calculateTopPadding() + settings + noteRoom + gap + buttons
                + contentPadding.calculateBottomPadding()
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection)
                , top = contentPadding.calculateTopPadding()
                , end = contentPadding.calculateEndPadding(layoutDirection)
                , bottom = contentPadding.calculateBottomPadding()
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val noteHeight = noteHeight(maxHeight, settingsHeight ?: 0.dp, noteMinHeight, gap)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(gap)
                    , modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { settingsHeight = with(density) { it.height.toDp() } }
                    , content = settings
                )
                if (note != null) {
                    Spacer(modifier = Modifier.height(gap))
                    note(
                        Modifier
                            .fillMaxWidth()
                            .height(noteHeight)
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            , verticalAlignment = Alignment.CenterVertically
            , modifier = Modifier
                .fillMaxWidth()
                .padding(top = gap)
                .onSizeChanged { buttonsHeight = with(density) { it.height.toDp() } }
            , content = buttons
        )
    }
}

/** The note's height in a scrolling area of [viewport]: the room [settings] and a [gap] leave, but at least [min]. */
internal fun noteHeight(
    viewport: Dp
    , settings: Dp
    , min: Dp
    , gap: Dp
): Dp = max(min, viewport - settings - gap)
