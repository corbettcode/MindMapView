package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeBorderStyle
import com.mindsync.library.model.LevelStyle
import com.mindsync.library.model.MapTheme
import com.mindsync.library.model.MapThemes
import com.mindsync.mindmapview.desktop.AppDialog
import com.mindsync.mindmapview.desktop.AppDialogState
import com.mindsync.mindmapview.desktop.NodeFont
import com.mindsync.mindmapview.desktop.loadNodeFontFamily

private val THEME_DIALOG_SIZE = DpSize(720.dp, 560.dp)
private val PREVIEW_SIZE = DpSize(200.dp, 120.dp)

// What a level of Classic shows in a preview, where a node keeps its own colors: those of a new node.
private val CLASSIC_PREVIEW = mapOf(
    0 to LevelStyle(Color(0xFF336770), Color.Black)
    , 1 to LevelStyle(Color(0xFF8E7CC3), Color.Black)
    , 2 to LevelStyle(Color(0xFF8E7CC3), Color.Black)
)

/**
 * Shows the themes a map can take, each in a small preview of a map. Choosing one gives the map that look at
 * once, as a change that can be undone; [current] is outlined.
 */
@Composable
fun ThemeDialog(
    current: MapTheme
    , onSelect: (MapTheme) -> Unit
    , onDismiss: () -> Unit
) {
    // Each theme's name is drawn in its font once it has loaded.
    var fontFamilies by remember { mutableStateOf<Map<String, FontFamily>>(emptyMap()) }
    LaunchedEffect(Unit) {
        MapThemes.all.mapNotNull { it.fontName }.distinct().forEach { name ->
            val font = NodeFont.entries.firstOrNull { it.label == name } ?: return@forEach
            loadNodeFontFamily(font)?.let { fontFamilies = fontFamilies + (name to it) }
        }
    }

    AppDialog(
        title = "Map Theme"
        , state = remember { AppDialogState(THEME_DIALOG_SIZE) }
        , onCloseRequest = onDismiss
        , isResizable = false
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                    , verticalArrangement = Arrangement.spacedBy(16.dp)
                    , modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                    MapThemes.all.forEach { theme ->
                        ThemeCard(
                            theme = theme
                            , isCurrent = theme == current
                            , fontFamily = theme.fontName?.let { fontFamilies[it] }
                            , onClick = { onSelect(theme) }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End
                    , modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Button(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: MapTheme
    , isCurrent: Boolean
    , fontFamily: FontFamily?
    , onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
        , modifier = Modifier
            .width(PREVIEW_SIZE.width + 16.dp)
            .clip(shape)
            .border(
                width = if (isCurrent) 3.dp else 1.dp
                , color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                , shape = shape
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))) {
            Canvas(modifier = Modifier.size(PREVIEW_SIZE)) { drawPreview(theme) }
        }
        Text(
            theme.name
            , fontFamily = fontFamily
            , style = MaterialTheme.typography.titleSmall
            , modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// A root in the middle, two main topics beside it and a subtopic beyond each, joined by the theme's lines.
private fun DrawScope.drawPreview(theme: MapTheme) {
    drawRect(theme.background)
    val root = Offset(size.width / 2, size.height / 2)
    val mains = listOf(Offset(size.width * 0.22f, size.height * 0.28f), Offset(size.width * 0.78f, size.height * 0.72f))
    val subs = listOf(Offset(size.width * 0.12f, size.height * 0.75f), Offset(size.width * 0.88f, size.height * 0.25f))
    val lineWidth = theme.lineWidth.dpVal.dp.toPx() / 2
    mains.forEachIndexed { index, main ->
        val color = theme.branchColors[index % theme.branchColors.size]
        drawCurve(root, main, color, lineWidth)
        drawCurve(main, subs[index], color, lineWidth / 2)
    }
    subs.forEach { drawNode(it, levelStyle(theme, 2), Size(44.dp.toPx(), 16.dp.toPx())) }
    mains.forEach { drawNode(it, levelStyle(theme, 1), Size(54.dp.toPx(), 20.dp.toPx())) }
    drawNode(root, levelStyle(theme, 0), Size(70.dp.toPx(), 26.dp.toPx()), isOval = true)
}

private fun levelStyle(
    theme: MapTheme
    , level: Int
): LevelStyle {
    val style = listOf(theme.root, theme.mainTopic, theme.subtopic)[level]
    val classic = CLASSIC_PREVIEW.getValue(level)
    return style.copy(fill = style.fill ?: classic.fill, text = style.text ?: classic.text)
}

private fun DrawScope.drawCurve(
    from: Offset
    , to: Offset
    , color: Color
    , width: Float
) {
    val middleX = (from.x + to.x) / 2
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(middleX, from.y, middleX, to.y, to.x, to.y)
    }
    drawPath(path, color, style = Stroke(width))
}

private fun DrawScope.drawNode(
    center: Offset
    , style: LevelStyle
    , size: Size
    , isOval: Boolean = false
) {
    val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
    val fill = style.fill ?: Color.Gray
    if (isOval) drawOval(fill, topLeft, size) else drawRoundRect(fill, topLeft, size, CornerRadius(4.dp.toPx()))
    if (style.border.style == NodeBorderStyle.LINE) {
        val stroke = Stroke(style.border.width.width.dpVal.dp.toPx())
        if (isOval) drawOval(style.border.color, topLeft, size, style = stroke)
        else drawRoundRect(style.border.color, topLeft, size, CornerRadius(4.dp.toPx()), style = stroke)
    }
    // A bar in the text color stands for the node's text, which is too small to read at this size.
    val text = style.text ?: Color.Black
    val bar = Size(size.width * 0.55f, 2.dp.toPx())
    drawRect(text, Offset(center.x - bar.width / 2, center.y - bar.height / 2), bar)
}
