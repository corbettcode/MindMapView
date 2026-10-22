package com.mindsync.library.layout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeAttribute
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

    // The attributes are plain text on one line each, a little smaller than the text of the node.
    private val attributeTextStyle =
        textStyle.copy(fontSize = textStyle.fontSize * ATTRIBUTE_TEXT_SCALE, textAlign = TextAlign.Start)

    fun measure(description: String): TextLayoutResult {
        if (richTextCache.size >= RICH_TEXT_CACHE_SIZE && description !in richTextCache) richTextCache.clear()
        val richText = richTextCache.getOrPut(description) { RichText.parse(description) }
        return textMeasurer.measure(richText, textStyle)
    }

    /**
     * Measures what is drawn in a node: its text and, when [showAttributes] is set and it has any, the
     * table of its attributes below the text.
     */
    fun measureContent(
        tree: Tree<*>
        , node: NodeData<*>
        , showAttributes: Boolean
    ): NodeContent {
        val attributes = if (showAttributes) tree.attributesOf(node.id) else emptyList()
        return NodeContent(
            text = measure(node.description)
            , table = if (attributes.isEmpty()) null else measureAttributes(attributes)
            , gap = with(density) { ATTRIBUTE_TABLE_GAP.dp.toPx() }
        )
    }

    private fun measureAttributes(attributes: List<NodeAttribute>): AttributeTable {
        val paddingX = with(density) { ATTRIBUTE_CELL_PADDING_X.dp.toPx() }
        val paddingY = with(density) { ATTRIBUTE_CELL_PADDING_Y.dp.toPx() }
        val rows = attributes.map { attribute ->
            val name = textMeasurer.measure(attribute.name, attributeTextStyle, maxLines = 1)
            val value = textMeasurer.measure(attribute.value, attributeTextStyle, maxLines = 1)
            AttributeRow(name, value, maxOf(name.size.height, value.size.height) + paddingY * 2)
        }
        return AttributeTable(
            rows = rows
            , nameWidth = rows.maxOf { it.name.size.width } + paddingX * 2
            , valueWidth = rows.maxOf { it.value.size.width } + paddingX * 2
            , paddingX = paddingX
            , paddingY = paddingY
        )
    }

    fun traverseTextHead(
        tree: Tree<*>?
        , showAttributes: Boolean = true
    ) {
        tree?.let {
            it.doPreorderTraversal { node ->
                val content = measureContent(tree, node, showAttributes)
                val newNode = changeSize(node, content.width, content.height)
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
        // The root is larger than an oval node with its text would be.
        is OvalNodeData -> {
            val scale = if (nodeData.parentId == null) ROOT_OVAL_SCALE else 1f
            val newRadiusX = (Dp(Px(width).toDp(density)) / 2 * OVAL_TEXT_FIT_RATIO + drawInfo.padding / 2) * scale
            val newRadiusY = (Dp(Px(height).toDp(density)) / 2 * OVAL_TEXT_FIT_RATIO + drawInfo.padding / 2) * scale
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
        private const val ROOT_OVAL_SCALE = 1.21f
        private const val ATTRIBUTE_TEXT_SCALE = 0.85f
        private const val ATTRIBUTE_TABLE_GAP = 6f
        private const val ATTRIBUTE_CELL_PADDING_X = 5f
        private const val ATTRIBUTE_CELL_PADDING_Y = 2f
    }
}

/**
 * What is drawn in a node, in pixels: its [text] and, below it, the [table] of its attributes when it has
 * one. The two are centered on the node together.
 */
class NodeContent(
    val text: TextLayoutResult
    , val table: AttributeTable?
    , gap: Float
) {
    val width: Float = maxOf(text.size.width.toFloat(), table?.width ?: 0f)
    val height: Float = text.size.height + (table?.let { gap + it.height } ?: 0f)

    /** How far above the center of the node the center of the text is. */
    val textRise: Float = (height - text.size.height) / 2

    /** How far below the center of the node the top of the table is. */
    val tableTop: Float = height / 2 - (table?.height ?: 0f)
}

/**
 * The table of the attributes of a node, in pixels: a column of names and a column of values.
 */
class AttributeTable(
    val rows: List<AttributeRow>
    , val nameWidth: Float
    , val valueWidth: Float
    , val paddingX: Float
    , val paddingY: Float
) {
    val width: Float = nameWidth + valueWidth
    val height: Float = rows.sumOf { it.height.toDouble() }.toFloat()
}

class AttributeRow(
    val name: TextLayoutResult
    , val value: TextLayoutResult
    , val height: Float
)
