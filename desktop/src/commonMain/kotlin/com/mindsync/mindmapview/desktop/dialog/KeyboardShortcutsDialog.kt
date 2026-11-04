package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mindsync.mindmapview.desktop.AppDialog
import com.mindsync.mindmapview.desktop.AppDialogState
import com.mindsync.mindmapview.desktop.KEYBOARD_SHORTCUTS
import com.mindsync.mindmapview.desktop.KeyboardShortcut

private val SHORTCUTS_DIALOG_SIZE = DpSize(620.dp, 700.dp)
// Wide enough for the longest keys, two of them side by side, so the commands line up.
private val KEYS_COLUMN_WIDTH = 240.dp

/** A table of all of the keyboard shortcuts, in the groups the user manual has them in. */
@Composable
fun KeyboardShortcutsDialog(
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "Keyboard Shortcuts"
        , state = remember { AppDialogState(SHORTCUTS_DIALOG_SIZE) }
        , onCloseRequest = onDismiss
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                    , modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    KEYBOARD_SHORTCUTS.forEach { group ->
                        Column {
                            Text(
                                group.title
                                , style = MaterialTheme.typography.titleSmall
                                , modifier = Modifier.padding(bottom = 4.dp)
                            )
                            group.shortcuts.forEach { shortcut ->
                                HorizontalDivider()
                                ShortcutRow(shortcut)
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End
                    , modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    shortcut: KeyboardShortcut
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
        , modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
            , verticalArrangement = Arrangement.spacedBy(4.dp)
            , itemVerticalAlignment = Alignment.CenterVertically
            , modifier = Modifier.width(KEYS_COLUMN_WIDTH)
        ) {
            shortcut.keys.forEachIndexed { index, keys ->
                if (index > 0) {
                    Text(
                        if (shortcut.isPair) "/" else "or"
                        , style = MaterialTheme.typography.bodySmall
                        , color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                KeyCap(keys)
            }
        }
        Text(
            shortcut.command
            , style = MaterialTheme.typography.bodyMedium
            , modifier = Modifier.weight(1f)
        )
    }
}

// The keys drawn as on a keyboard, as the user manual draws them.
@Composable
private fun KeyCap(
    keys: String
) {
    Text(
        keys
        , style = MaterialTheme.typography.bodySmall
        , fontFamily = FontFamily.Monospace
        , modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
