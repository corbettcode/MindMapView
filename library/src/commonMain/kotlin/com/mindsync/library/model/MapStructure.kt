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
enum class MapStructure(
    /** The name the structure is kept under in a map file. */
    val fileName: String
    /** The structure class XMind names it by. */
    , private val xmindClass: String
) {
    /** The root in the middle with branches on its six sides, as the map has been laid out all along. */
    MIND_MAP("mind-map", "org.xmind.ui.map.unbalanced")
    , LOGIC_RIGHT("logic-right", "org.xmind.ui.logic.right")
    , LOGIC_LEFT("logic-left", "org.xmind.ui.logic.left")
    , ORG_DOWN("org-chart-down", "org.xmind.ui.org-chart.down")
    , ORG_UP("org-chart-up", "org.xmind.ui.org-chart.up")
    , TREE_RIGHT("tree-right", "org.xmind.ui.tree.right")
    , TREE_LEFT("tree-left", "org.xmind.ui.tree.left")
    , BRACE_RIGHT("brace-right", "org.xmind.ui.brace.right")
    , TIMELINE("timeline", "org.xmind.ui.timeline.horizontal");

    companion object {
        /** Returns the structure kept as [name] in a map file, or the mind map for one that is not known. */
        fun fromFileName(name: String?): MapStructure = entries.firstOrNull { it.fileName == name } ?: MIND_MAP

        /**
         * Returns the structure of XMind's [structureClass], or the mind map for one this library has no layout for,
         * as a fishbone, and for XMind's other mind maps, as the clockwise one.
         */
        fun fromXMindClass(structureClass: String?): MapStructure =
            entries.firstOrNull { it.xmindClass == structureClass } ?: MIND_MAP
    }

    /**
     * Returns the way the branch the node [id] is in grows when this structure is laid out from [owner]: the way the
     * children of [id] are placed from it. [owner] is the topic the structure is set on, the root for the map's own,
     * and [id] is it or a node below it. In the mind map it is by the side of the root the branch is on; in a
     * timeline the branches below the owner grow up and down by turns. The owner, and a floating topic's branch, grow
     * the way the structure's first children are placed.
     */
    fun directionOf(
        tree: Tree<*>
        , id: String
        , owner: String = tree.getRootNode().id
    ): GrowDirection = when (this) {
        MIND_MAP -> if (tree.sideOf(id).isLeft) GrowDirection.LEFT else GrowDirection.RIGHT
        LOGIC_RIGHT, BRACE_RIGHT -> GrowDirection.RIGHT
        LOGIC_LEFT -> GrowDirection.LEFT
        ORG_DOWN, TREE_RIGHT, TREE_LEFT -> GrowDirection.DOWN
        ORG_UP -> GrowDirection.UP
        TIMELINE -> timelineDirectionOf(tree, id, owner)
    }

    /** Whether the map is a tree chart, laid out in indented columns rather than by a direction. */
    val isTreeChart: Boolean get() = this == TREE_RIGHT || this == TREE_LEFT

    /**
     * Returns whether the node [id] and its siblings are stacked along x, side by side, rather than along y, one
     * above the other, when their parent is laid out in this structure from [owner]: across the way their branch
     * grows, but for the rows of a tree chart's columns, which are one above the other below the owner's row.
     */
    fun isStackedAlongX(
        tree: Tree<*>
        , id: String
        , owner: String = tree.getRootNode().id
    ): Boolean {
        val isOwnerChild = tree.getNode(id).parentId == owner
        return if (isTreeChart) isOwnerChild else !directionOf(tree, id, owner).isHorizontal
    }

    /**
     * Returns the way the summary [id] is past the siblings it is about, when their parent is laid out in this
     * structure from [owner]: as their branch grows, but in a tree chart's columns to their side, as the rows go
     * down.
     */
    fun summaryDirectionOf(
        tree: Tree<*>
        , id: String
        , owner: String = tree.getRootNode().id
    ): GrowDirection {
        val isOwnerChild = tree.getNode(id).parentId == owner
        return when {
            !isTreeChart || isOwnerChild -> directionOf(tree, id, owner)
            this == TREE_RIGHT -> GrowDirection.RIGHT
            else -> GrowDirection.LEFT
        }
    }

    // The owner's children are along the axis, to its right; the branch of every other one goes up, and of the rest
    // down.
    private fun timelineDirectionOf(
        tree: Tree<*>
        , id: String
        , owner: String
    ): GrowDirection {
        if (id == owner) return GrowDirection.RIGHT
        val branchId = generateSequence(id) { tree.getNode(it).parentId }.first { tree.getNode(it).parentId == owner }
        // Floating topics, and summaries of the owner's children, are below the axis.
        if (tree.isFloating(branchId) || tree.isSummary(branchId)) return GrowDirection.DOWN
        val axisIds = tree.getNode(owner).children.filterNot { tree.isFloating(it) || tree.isSummary(it) }
        return if (axisIds.indexOf(branchId) % 2 == 0) GrowDirection.UP else GrowDirection.DOWN
    }
}
