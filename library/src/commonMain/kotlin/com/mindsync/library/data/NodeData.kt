package com.mindsync.library.data

import androidx.compose.ui.graphics.Color
import com.mindsync.library.util.Dp


sealed class NodeData<T>(
    open val id: String
    , open var alpha: Float
    , open val children: List<String>
    , open val color: Color
    , open val description: String
    , open var isAnimating: Boolean = false
    , open var isDrawingLine: Boolean = false
    , open val parentId: String?
    , open val path: NodePath
    , open var strokeWidth: Float = 1f
    , open val textColor: Color
) {
    abstract fun adjustPosition(horizontalSpacing: Dp, totalHeight: Dp): NodeData<T>
}

data class CircleNodeData(
    override val id: String
    , override var alpha: Float = 0f
    , override val children: List<String>
    , override val color: Color = NodeShape.CIRCLE.defaultColor
    , override val description: String
    , override var isAnimating: Boolean = false
    , override var isDrawingLine: Boolean = false
    , override val parentId: String?
    , override val path: CirclePath = CirclePath(Dp(0f), Dp(0f), Dp(0f))
    , override var strokeWidth: Float = 1f
    , override val textColor: Color = NodeShape.CIRCLE.defaultTextColor
) : NodeData<CircleNodeData>(
    id
    , alpha
    , children
    , color
    , description
    , isAnimating
    , isDrawingLine
    , parentId
    , path
    , strokeWidth
    , textColor
) {
    override fun adjustPosition(
        horizontalSpacing: Dp
        , totalHeight: Dp
    ): NodeData<CircleNodeData> {
        return this.copy(path = path.adjustPath(horizontalSpacing, totalHeight))
    }
}

data class RectangleNodeData(
    override val id: String
    , override var alpha: Float = 0f
    , override val children: List<String>
    , override val color: Color = NodeShape.RECTANGLE.defaultColor
    , override val description: String
    , override var isAnimating: Boolean = false
    , override var isDrawingLine: Boolean = false
    , override val parentId: String
    , override val path: RectanglePath = RectanglePath(Dp(0f), Dp(0f), Dp(0f), Dp(0f))
    , override var strokeWidth: Float = 1f
    , override val textColor: Color = NodeShape.RECTANGLE.defaultTextColor
): NodeData<RectangleNodeData>(
    id
    , alpha
    , children
    , color
    , description
    , isAnimating
    , isDrawingLine
    , parentId
    , path
    , strokeWidth
    , textColor
) {
    override fun adjustPosition(horizontalSpacing: Dp, totalHeight: Dp): NodeData<RectangleNodeData> {
        return this.copy(path = path.adjustPath(horizontalSpacing, totalHeight))
    }
}

data class StadiumNodeData(
    override val id: String
    , override var alpha: Float = 0f
    , override val children: List<String>
    , override val color: Color = NodeShape.STADIUM.defaultColor
    , override val description: String
    , override var isAnimating: Boolean = false
    , override var isDrawingLine: Boolean = false
    , override val parentId: String?
    , override val path: StadiumPath = StadiumPath(Dp(0f), Dp(0f), Dp(0f), Dp(0f))
    , override var strokeWidth: Float = 1f
    , override val textColor: Color = NodeShape.STADIUM.defaultTextColor
) : NodeData<StadiumNodeData>(
    id
    , alpha
    , children
    , color
    , description
    , isAnimating
    , isDrawingLine
    , parentId
    , path
    , strokeWidth
    , textColor
) {
    override fun adjustPosition(horizontalSpacing: Dp, totalHeight: Dp): NodeData<StadiumNodeData> {
        return this.copy(path = path.adjustPath(horizontalSpacing, totalHeight))
    }
}
