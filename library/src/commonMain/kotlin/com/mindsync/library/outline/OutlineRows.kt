package com.mindsync.library.outline

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree

/**
 * A topic as a row of the outline: the topic [id], its [depth] below the root, which is 0, whether it
 * [hasChildren] and [isFolded], which hides them, whether it [isSummary] of some of its siblings, and whether it
 * [isFloating], placed apart from the root's branches.
 */
data class OutlineRow(
    val id: String
    , val depth: Int
    , val hasChildren: Boolean
    , val isFolded: Boolean
    , val isSummary: Boolean
    , val isFloating: Boolean = false
)

/**
 * Returns the rows of the outline of [tree]: its topics from the root down, each followed by those below it, but
 * none hidden in a folded topic. The children of a topic are in their order whatever side of the root they are on,
 * so a summary comes right after the siblings it is about, and the floating topics after the root's other children.
 */
fun outlineRows(tree: Tree<*>): List<OutlineRow> {
    val rows = mutableListOf<OutlineRow>()
    fun add(
        node: NodeData<*>
        , depth: Int
    ) {
        rows.add(
            OutlineRow(
                id = node.id
                , depth = depth
                , hasChildren = node.children.isNotEmpty()
                , isFolded = tree.isFolded(node.id)
                , isSummary = tree.isSummary(node.id)
                , isFloating = tree.isFloating(node.id)
            )
        )
        tree.visibleChildren(node).forEach { add(tree.getNode(it), depth + 1) }
    }
    add(tree.getRootNode(), 0)
    return rows
}

/** Returns the id of the row above the topic [id], or null when it is the first row or not a row. */
fun List<OutlineRow>.rowAbove(id: String): String? {
    val index = indexOfFirst { it.id == id }
    return if (index > 0) this[index - 1].id else null
}

/** Returns the id of the row below the topic [id], or null when it is the last row or not a row. */
fun List<OutlineRow>.rowBelow(id: String): String? {
    val index = indexOfFirst { it.id == id }
    return if (index >= 0) getOrNull(index + 1)?.id else null
}
