package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp

/**
 * How far a branch, its topic and the shown nodes below it, reaches each way on the screen from the center of its
 * topic. A branch laid out in its parent's structure reaches as far before as after it across the way it grows, and
 * starts at its topic; one laid out in a structure of its own need not, as an org chart's row below a topic in a logic
 * chart reaches back as far as forward.
 */
internal data class BranchBox(
    val left: Dp
    , val right: Dp
    , val top: Dp
    , val bottom: Dp
)

/**
 * Lays out the branches of the topics with a structure of their own, for a layout that meets one among the nodes it
 * arranges: it places the topic itself, as its parent's structure has it, and leaves what is below it to this.
 */
internal interface OwnedBranches {
    /** Returns how far the branch of [owner] reaches from its center, laid out in its structure. */
    fun boxOf(
        owner: NodeData<*>
        , tree: Tree<*>
    ): BranchBox

    /** Lays out the nodes below [owner] in its structure, around it where it is. */
    fun arrangeBelow(
        owner: NodeData<*>
        , tree: Tree<*>
    )
}
