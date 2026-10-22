package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree

internal enum class FindDirection { NEXT, PREVIOUS }

/**
 * Returns the match to go to from the node with [currentId] in [direction], around to the first or the last
 * one at the ends, or null when there are no [matchIds]. The matches are in the order of the tree, as
 * `MindMapState.findNodes` returns them.
 *
 * The current node need not be a match: it may have been edited since it was found, and the step goes on from
 * where it is in the tree. A node that is gone, or none, starts from the first or the last match.
 */
internal fun nextMatch(
    tree: Tree<*>,
    matchIds: List<String>,
    currentId: String?,
    direction: FindDirection,
): String? {
    if (matchIds.isEmpty()) return null
    val order = mutableListOf<String>()
    tree.doPreorderTraversal { order.add(it.id) }
    val position = order.indexOf(currentId)
    if (position == -1) return if (direction == FindDirection.NEXT) matchIds.first() else matchIds.last()
    return when (direction) {
        FindDirection.NEXT -> matchIds.firstOrNull { order.indexOf(it) > position } ?: matchIds.first()
        FindDirection.PREVIOUS -> matchIds.lastOrNull { order.indexOf(it) < position } ?: matchIds.last()
    }
}

/** Returns which of the [matchIds] the node with [currentId] is, as "2 of 5", or how many there are. */
internal fun findCountText(matchIds: List<String>, currentId: String?): String {
    val number = matchIds.indexOf(currentId) + 1
    return when {
        matchIds.isEmpty() -> "No matches"
        number > 0 -> "$number of ${matchIds.size}"
        matchIds.size == 1 -> "1 match"
        else -> "${matchIds.size} matches"
    }
}
