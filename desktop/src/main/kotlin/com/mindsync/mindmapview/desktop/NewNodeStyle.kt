package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.data.shape
import com.mindsync.library.model.LayoutSide

/**
 * What a node that is added starts with, before any of it is changed in the dialog it is added in.
 */
internal data class NewNodeStyle(
    val shape: NodeShape,
    val color: Color,
    val textColor: Color,
)

/**
 * Returns the style of a node that is added to [parent]: that of the last child of [parent], for the nodes
 * of a parent to look alike, or a rectangle in its own colors when there is none. The root has a set of
 * children for each side, so it is the last one on [side] that counts for it.
 *
 * A [shape] that was chosen is kept, and only takes the colors of a last child of that shape.
 */
internal fun newNodeStyle(
    tree: Tree<*>,
    parent: NodeData<*>,
    side: LayoutSide,
    shape: NodeShape? = null,
): NewNodeStyle {
    val lastChild = parent.children
        .lastOrNull { parent.parentId != null || tree.sideOf(it) == side }
        ?.let { tree.getNode(it) }
    val newShape = shape ?: lastChild?.shape ?: NodeShape.RECTANGLE
    return lastChild
        ?.takeIf { it.shape == newShape }
        ?.let { NewNodeStyle(newShape, it.color, it.textColor) }
        ?: NewNodeStyle(newShape, newShape.defaultColor, newShape.defaultTextColor)
}
