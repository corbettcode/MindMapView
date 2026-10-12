package com.mindsync.library.layout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.Dp
import com.mindsync.library.util.Px
import com.mindsync.library.util.RichText
import com.mindsync.library.util.toDp

class MeasureTextSize(
    private val textMeasurer: TextMeasurer,
    private val textStyle: TextStyle,
    private val density: Density,
    private val drawInfo: DrawInfo = DrawInfo(),
) {
    private val richTextCache = mutableMapOf<String, AnnotatedString>()

    fun measure(description: String): TextLayoutResult {
        if (richTextCache.size >= RICH_TEXT_CACHE_SIZE && description !in richTextCache) richTextCache.clear()
        val richText = richTextCache.getOrPut(description) { RichText.parse(description) }
        return textMeasurer.measure(richText, textStyle)
    }

    fun traverseTextHead(tree: Tree<*>?) {
        tree?.let {
            it.doPreorderTraversal { node ->
                val textSize = measure(node.description).size
                val newNode =
                    changeSize(node, textSize.width.toFloat(), textSize.height.toFloat())
                tree.setNode(node.id, newNode)
            }
        }
    }

    private fun changeSize(
        nodeData: NodeData<*>,
        width: Float,
        height: Float,
    ): NodeData<*> = when (nodeData) {
        is CircleNodeData -> {
            val newRadius =
                Dp(
                    maxOf(
                        (Dp(Px(width).toDp(density) / 2) + drawInfo.padding).dpVal,
                        ((Dp(Px(height).toDp(density)) + drawInfo.padding * 2) / 2).dpVal,
                    ),
                )
            nodeData.copy(
                id = nodeData.id,
                parentId = nodeData.parentId,
                path = nodeData.path.copy(radius = newRadius),
                description = nodeData.description,
                children = nodeData.children,
            )
        }

        is RectangleNodeData -> {
            var newWidth = Dp(Px(width).toDp(density)) + drawInfo.padding
            val newHeight = Dp(Px(height).toDp(density)) + drawInfo.padding
            nodeData.copy(
                id = nodeData.id,
                parentId = nodeData.parentId,
                path = nodeData.path.copy(width = newWidth, height = newHeight),
                description = nodeData.description,
                children = nodeData.children,
            )
        }

        // The text sits between the straight sides, so the caps add to the width on either end.
        is StadiumNodeData -> {
            val newLength = Dp(Px(width).toDp(density))
            val newRadius = (Dp(Px(height).toDp(density)) + drawInfo.padding) / 2
            nodeData.copy(
                id = nodeData.id,
                parentId = nodeData.parentId,
                path = nodeData.path.copy(length = newLength, radius = newRadius),
                description = nodeData.description,
                children = nodeData.children,
            )
        }

        // An ellipse holds a rectangle of the text's proportions when each radius is √2 times half that side.
        is OvalNodeData -> {
            val newRadiusX = Dp(Px(width).toDp(density)) / 2 * OVAL_TEXT_FIT_RATIO + drawInfo.padding / 2
            val newRadiusY = Dp(Px(height).toDp(density)) / 2 * OVAL_TEXT_FIT_RATIO + drawInfo.padding / 2
            nodeData.copy(
                id = nodeData.id,
                parentId = nodeData.parentId,
                path = nodeData.path.copy(radiusX = newRadiusX, radiusY = newRadiusY),
                description = nodeData.description,
                children = nodeData.children,
            )
        }
    }

    companion object {
        private const val RICH_TEXT_CACHE_SIZE = 256
        private const val OVAL_TEXT_FIT_RATIO = 1.4142135f
    }
}
