package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** A framed section whose [title] is clicked to open and close it. While [isOpen], its [content] shows below. */
@Composable
fun CollapsibleSection(
    title: String
    , isOpen: Boolean
    , onToggle: () -> Unit
    , modifier: Modifier = Modifier
    , content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
    ) {
        Text(
            if (isOpen) "$title ▾" else "$title ▸"
            , style = MaterialTheme.typography.titleSmall
            , modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(12.dp)
        )
        if (isOpen) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}
