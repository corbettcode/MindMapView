package com.mindsync.mindmapview.desktop.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

/**
 * A framed section whose [title] is clicked to open and close it. While [isOpen], its [content] shows below.
 * The content slides open and closed, and the arrow by the title turns with it.
 */
@Composable
fun CollapsibleSection(
    title: String
    , isOpen: Boolean
    , onToggle: () -> Unit
    , modifier: Modifier = Modifier
    , content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val arrowRotation by animateFloatAsState(if (isOpen) 90f else 0f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
            , verticalAlignment = Alignment.CenterVertically
            , modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                "▸"
                , style = MaterialTheme.typography.titleSmall
                , modifier = Modifier.rotate(arrowRotation)
            )
        }
        AnimatedVisibility(
            visible = isOpen
            , enter = expandVertically(expandFrom = Alignment.Top) + fadeIn()
            , exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}
