package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.Tree
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.Dp

internal data class ResolvedLine(
    val style: NodeLineStyle
    , val color: Color
    , val width: Dp
)

/** Calls [action] for every shown node with the line it is drawn with. */
internal fun forEachNodeWithLine(
    tree: Tree<*>
    , drawInfo: DrawInfo
    , rootColor: Color
    , action: (node: NodeData<*>, line: ResolvedLine) -> Unit
) {
    fun traverse(
        node: NodeData<*>
        , line: ResolvedLine
    ) {
        action(node, line)
        tree.visibleChildren(node).forEachIndexed { index, childId ->
            val own = tree.lineOf(childId)
            val inheritedColor = if (node.parentId == null) rootLineColor(drawInfo, index) else line.color
            val childColor = if (node.parentId == null) tree.lineOf(node.id).color ?: inheritedColor else inheritedColor
            traverse(
                tree.getNode(childId)
                , ResolvedLine(
                    style = own.style ?: line.style
                    , color = own.color ?: childColor
                    , width = own.width?.width ?: line.width
                )
            )
        }
    }
    val root = tree.getRootNode()
    val own = tree.lineOf(root.id)
    traverse(
        root
        , ResolvedLine(
            style = own.style ?: NodeLineStyle.BEZIER
            , color = own.color ?: rootColor
            , width = own.width?.width ?: drawInfo.lineWidth
        )
    )
}
