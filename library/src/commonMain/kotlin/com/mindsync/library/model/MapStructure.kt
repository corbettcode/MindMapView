package com.mindsync.library.model

import com.mindsync.library.data.Tree

/**
 * The way a branch grows out of its parent on the screen: its children are placed that way from it, and stacked
 * across it. A layout works along the [main][isHorizontal] axis, the way the branch grows, and the cross axis, the way
 * siblings are stacked; [sign] is +1 when the branch grows right or down, the ways x and y grow on the screen.
 */
enum class GrowDirection {
    RIGHT
    , LEFT
    , DOWN
    , UP;

    /** Whether the branch grows along x, so that siblings are stacked along y. */
    val isHorizontal: Boolean get() = this == RIGHT || this == LEFT

    /** +1 when the branch grows the way its axis grows on the screen, right or down, and -1 the other way. */
    val sign: Float get() = if (this == RIGHT || this == DOWN) 1f else -1f
}

/**
 * How a map is laid out, as XMind's structures. Only the drawing changes with it: the topics, their order and all
 * there is of them stay as they are, and the sides of the root a mind map has are kept for when it is one again.
 */
enum class MapStructure {
    /** The root in the middle with branches on its six sides, as the map has been laid out all along. */
    MIND_MAP
    , LOGIC_RIGHT
    , LOGIC_LEFT
    , ORG_DOWN
    , ORG_UP
    , TREE_RIGHT
    , TREE_LEFT
    , BRACE_RIGHT
    , TIMELINE;

    /**
     * Returns the way the branch the node [id] is in grows: the way its children are placed from it. In the mind map
     * it is by the side of the root the branch is on; in a timeline the branches below the root grow up and down by
     * turns. The root, and a floating topic's branch, grow the way the structure's root children are placed.
     */
    fun directionOf(
        tree: Tree<*>
        , id: String
    ): GrowDirection = when (this) {
        MIND_MAP -> if (tree.sideOf(id).isLeft) GrowDirection.LEFT else GrowDirection.RIGHT
        LOGIC_RIGHT, BRACE_RIGHT -> GrowDirection.RIGHT
        LOGIC_LEFT -> GrowDirection.LEFT
        ORG_DOWN, TREE_RIGHT, TREE_LEFT -> GrowDirection.DOWN
        ORG_UP -> GrowDirection.UP
        TIMELINE -> timelineDirectionOf(tree, id)
    }

    /** Whether the map is a tree chart, laid out in indented columns rather than by a direction. */
    val isTreeChart: Boolean get() = this == TREE_RIGHT || this == TREE_LEFT

    /**
     * Returns whether the node [id] and its siblings are stacked along x, side by side, rather than along y, one
     * above the other: across the way their branch grows, but for the rows of a tree chart's columns, which are one
     * above the other below the root's row.
     */
    fun isStackedAlongX(
        tree: Tree<*>
        , id: String
    ): Boolean {
        val isRootChild = tree.getNode(id).parentId == tree.getRootNode().id
        return if (isTreeChart) isRootChild else !directionOf(tree, id).isHorizontal
    }

    /**
     * Returns the way the summary [id] is past the siblings it is about: as its branch grows, but in a tree chart's
     * columns to their side, as the rows go down.
     */
    fun summaryDirectionOf(
        tree: Tree<*>
        , id: String
    ): GrowDirection {
        val isRootChild = tree.getNode(id).parentId == tree.getRootNode().id
        return when {
            !isTreeChart || isRootChild -> directionOf(tree, id)
            this == TREE_RIGHT -> GrowDirection.RIGHT
            else -> GrowDirection.LEFT
        }
    }

    // The root's children are along the axis, to its right; the branch of every other one goes up, and of the rest down.
    private fun timelineDirectionOf(
        tree: Tree<*>
        , id: String
    ): GrowDirection {
        val root = tree.getRootNode()
        if (id == root.id) return GrowDirection.RIGHT
        val branchId = tree.branchOf(id).id
        // Floating topics, and summaries of the root's children, are below the axis.
        if (tree.isFloating(branchId) || tree.isSummary(branchId)) return GrowDirection.DOWN
        val index = root.children.filterNot { tree.isFloating(it) || tree.isSummary(it) }.indexOf(branchId)
        return if (index % 2 == 0) GrowDirection.UP else GrowDirection.DOWN
    }
}
