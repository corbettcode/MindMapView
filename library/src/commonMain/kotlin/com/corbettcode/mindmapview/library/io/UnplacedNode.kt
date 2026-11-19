package com.corbettcode.mindmapview.library.io

import androidx.compose.ui.graphics.Color
import com.corbettcode.mindmapview.library.data.CircleNodeData
import com.corbettcode.mindmapview.library.data.CirclePath
import com.corbettcode.mindmapview.library.data.NodeData
import com.corbettcode.mindmapview.library.data.NodeShape
import com.corbettcode.mindmapview.library.data.OvalNodeData
import com.corbettcode.mindmapview.library.data.OvalPath
import com.corbettcode.mindmapview.library.data.RectangleNodeData
import com.corbettcode.mindmapview.library.data.LineNodeData
import com.corbettcode.mindmapview.library.data.SquareNodeData
import com.corbettcode.mindmapview.library.data.RectanglePath
import com.corbettcode.mindmapview.library.data.StadiumNodeData
import com.corbettcode.mindmapview.library.data.StadiumPath
import com.corbettcode.mindmapview.library.util.Dp

// A node as it is read from a file: where it is and how large it is are worked out once the tree is arranged.
internal fun unplacedNode(
    shape: NodeShape
    , id: String
    , parentId: String
    , description: String
    , children: List<String>
    , color: Color
    , textColor: Color
    , note: String
): NodeData<*> = when (shape) {
    NodeShape.CIRCLE -> CircleNodeData(
        id = id
        , parentId = parentId
        , path = CirclePath(Dp(0f), Dp(0f), Dp(0f))
        , description = description
        , children = children
        , color = color
        , textColor = textColor
        , note = note
    )
    NodeShape.RECTANGLE -> RectangleNodeData(
        id = id
        , parentId = parentId
        , path = RectanglePath(Dp(0f), Dp(0f), Dp(0f), Dp(0f))
        , description = description
        , children = children
        , color = color
        , textColor = textColor
        , note = note
    )
    NodeShape.SQUARE -> SquareNodeData(
        id = id
        , parentId = parentId
        , path = RectanglePath(Dp(0f), Dp(0f), Dp(0f), Dp(0f))
        , description = description
        , children = children
        , color = color
        , textColor = textColor
        , note = note
    )
    NodeShape.LINE -> LineNodeData(
        id = id
        , parentId = parentId
        , path = RectanglePath(Dp(0f), Dp(0f), Dp(0f), Dp(0f))
        , description = description
        , children = children
        , color = color
        , textColor = textColor
        , note = note
    )
    NodeShape.STADIUM -> StadiumNodeData(
        id = id
        , parentId = parentId
        , path = StadiumPath(Dp(0f), Dp(0f), Dp(0f), Dp(0f))
        , description = description
        , children = children
        , color = color
        , textColor = textColor
        , note = note
    )
    NodeShape.OVAL -> OvalNodeData(
        id = id
        , parentId = parentId
        , path = OvalPath(Dp(0f), Dp(0f), Dp(0f), Dp(0f))
        , description = description
        , children = children
        , color = color
        , textColor = textColor
        , note = note
    )
}
