package com.mindsync.library.io

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.CirclePath
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.OvalPath
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.RectanglePath
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.StadiumPath
import com.mindsync.library.util.Dp

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
