package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.data.withCenter
import com.mindsync.library.halfHeight
import com.mindsync.library.halfWidth
import com.mindsync.library.util.Dp

/**
 * How far a bone runs along the spine for each step it runs away from it: the cotangent of the 60° a bone slants back
 * from the spine at.
 */
internal const val FISHBONE_SLANT = 0.57735f

/** The space between the head and the first bone, and how far the spine runs past the last bone's joint. */
internal const val FISHBONE_HEAD_GAP = 40f

// The least space between the joints of two bones on the spine, one above and one below it.
private const val JOINT_GAP = 30f

// The space between a bone and the next one on its side of the spine.
private const val BONE_GAP = 30f

// How far from the spine the first rib of a bone is.
private const val SPINE_CLEARANCE = 24f

// The space between the ribs of a bone.
private const val RIB_GAP = 12f

// The least length of a rib, from its bone to its topic.
private const val RIB_LENGTH = 24f

// The space between a bone's last rib and its topic, at the far end of the bone.
private const val TOPIC_GAP = 16f

// The length of a bone without ribs.
private const val BONE_LENGTH = 48f

/**
 * Lays a branch out as a fishbone, an Ishikawa diagram: its topic is the head, with a spine running away from it to
 * the right when [isHeadLeft] and to the left otherwise. Its children are causes, each at the far end of a bone that
 * slants back from the spine at 60°, above and below the spine by turns. A cause's children are ribs along its bone,
 * the first nearest the cause, each joined to the bone by a horizontal line; the topics below a rib are laid out by
 * [tail], growing away from the head. A summary of the causes is below them all, laid out by [down].
 */
internal class FishboneLayout(
    private val isHeadLeft: Boolean
    , private val tail: MindMapLayoutManager
    , private val down: MindMapLayoutManager
) {
    // +1 when the spine runs right, -1 left. The layout works in how far along the spine from the head a point is.
    private val sign = if (isHeadLeft) 1f else -1f

    /** What lays out the branches of the causes with a structure of their own. */
    var ownedBranches: OwnedBranches? = null

    /** Places the children of [head] and the topics below them on bones off a spine from it. */
    fun arrange(
        head: NodeData<*>
        , tree: Tree<*>
    ) {
        // The root's floating topics are placed apart, and the children of a folded topic below it are hidden.
        val ids =
            if (head.parentId == null) head.children.filterNot { tree.isFloating(it) } else tree.visibleChildren(head)
        val spineY = head.path.centerY.dpVal
        val start = sign * head.path.centerX.dpVal + halfWidth(head).dpVal + FISHBONE_HEAD_GAP
        var jointCursor = start
        // Where the next bone above, and the next below, may start along the spine.
        val sideCursors = mutableMapOf(true to start, false to start)
        val spans = mutableMapOf<String, Pair<Float, Float>>()
        ids.filterNot { tree.isSummary(it) }.forEachIndexed { index, id ->
            val isAbove = index % 2 == 0
            // Each bone is laid out with its joint at the start of the spine, then moved along it.
            placeBone(tree.getNode(id), jointAlong = 0f, spineY = spineY, isAbove = isAbove, tree = tree)
            val (back, front) = alongSpan(id, tree)
            val joint = maxOf(jointCursor, sideCursors.getValue(isAbove) - minOf(back, 0f))
            shift(id, sign * joint, 0f, tree)
            spans[id] = joint + minOf(back, 0f) to joint + front
            jointCursor = joint + JOINT_GAP
            sideCursors[isAbove] = joint + front + BONE_GAP
        }
        placeSummaries(ids.filter { tree.isSummary(it) }, spans, tree)
    }

    /**
     * Places the cause [bone] with its center at ([centerX], [centerY]), and its ribs along its bone, above the spine
     * when [isAbove] and below it otherwise: while it is dragged, say.
     */
    fun arrangeBoneAt(
        bone: NodeData<*>
        , centerX: Float
        , centerY: Float
        , isAbove: Boolean
        , tree: Tree<*>
    ) {
        placeBone(bone, jointAlong = 0f, spineY = centerY, isAbove = isAbove, tree = tree)
        val placed = tree.getNode(bone.id)
        shift(bone.id, centerX - placed.path.centerX.dpVal, centerY - placed.path.centerY.dpVal, tree)
    }

    // Places [bone] at the far end of a bone from its joint at [jointAlong] on the spine at [spineY], with its ribs
    // along the bone, from the spine out, the first rib nearest the cause.
    private fun placeBone(
        bone: NodeData<*>
        , jointAlong: Float
        , spineY: Float
        , isAbove: Boolean
        , tree: Tree<*>
    ) {
        // +1 when away from the spine is down the screen.
        val away = if (isAbove) -1f else 1f
        val branches = ownedBranches
        // A cause with a structure of its own has its branch laid out in that, past the end of a short bone.
        if (branches != null && tree.branchStructureOf(bone.id) != null) {
            val box = branches.boxOf(bone, tree)
            val inward = (if (isAbove) box.bottom else box.top).dpVal
            val end = jointAlong + BONE_LENGTH * FISHBONE_SLANT
            val placed = bone.withCenter(Dp(sign * end), Dp(spineY + away * (BONE_LENGTH + inward)))
            tree.setNode(bone.id, placed)
            branches.arrangeBelow(placed, tree)
            return
        }
        val childIds = tree.visibleChildren(bone)
        val ribIds = childIds.filterNot { tree.isSummary(it) }
        var distance = SPINE_CLEARANCE
        ribIds.reversed().forEach { ribId ->
            val rib = tree.getNode(ribId)
            val (top, bottom) = tail.crossReach(rib, tree).let { (top, bottom) -> top.dpVal to bottom.dpVal }
            val inward = if (isAbove) bottom else top
            val outward = if (isAbove) top else bottom
            val ribDistance = distance + inward
            // The rib is long enough for the bone, slanting on past it, to be clear of all the rib's branch.
            val ribLength = maxOf(RIB_LENGTH, outward * FISHBONE_SLANT + RIB_GAP)
            val along = jointAlong + ribDistance * FISHBONE_SLANT + ribLength + tail.backReach(rib, tree).dpVal
            val placed = rib.withCenter(Dp(sign * along), Dp(spineY + away * ribDistance))
            tree.setNode(ribId, placed)
            tail.arrangeBelow(placed, tree)
            distance = ribDistance + outward + RIB_GAP
        }
        val length = if (ribIds.isEmpty()) BONE_LENGTH else distance - RIB_GAP + TOPIC_GAP
        val end = jointAlong + length * FISHBONE_SLANT
        val placed = bone.withCenter(Dp(sign * end), Dp(spineY + away * (length + halfHeight(bone).dpVal)))
        tree.setNode(bone.id, placed)
        placeRibSummaries(childIds.filter { tree.isSummary(it) }, tree)
    }

    // A summary of ribs is past the furthest of them along the spine, centered on them.
    private fun placeRibSummaries(
        summaryIds: List<String>
        , tree: Tree<*>
    ) {
        summaryIds.forEach { summaryId ->
            val rangeIds = tree.summaryChildIds(summaryId).filter { tree.isVisible(it) }
            if (rangeIds.isEmpty()) return@forEach
            var front = -Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            rangeIds.forEach { id ->
                tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
                    front = maxOf(front, sign * node.path.centerX.dpVal + halfWidth(node).dpVal)
                    top = minOf(top, node.path.centerY.dpVal - halfHeight(node).dpVal)
                    bottom = maxOf(bottom, node.path.centerY.dpVal + halfHeight(node).dpVal)
                }
            }
            val summary = tree.getNode(summaryId)
            val along = front + SUMMARY_OFFSET.value + tail.backReach(summary, tree).dpVal
            val placed = summary.withCenter(Dp(sign * along), Dp((top + bottom) / 2))
            tree.setNode(summaryId, placed)
            tail.arrangeBelow(placed, tree)
        }
    }

    // A summary of causes is below the whole fishbone, so clear of the bones below the spine, centered on its causes.
    private fun placeSummaries(
        summaryIds: List<String>
        , spans: Map<String, Pair<Float, Float>>
        , tree: Tree<*>
    ) {
        if (summaryIds.isEmpty()) return
        var bottom = -Float.MAX_VALUE
        spans.keys.forEach { id ->
            tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
                bottom = maxOf(bottom, node.path.centerY.dpVal + halfHeight(node).dpVal)
            }
        }
        summaryIds.forEach { summaryId ->
            val rangeIds = tree.summaryChildIds(summaryId).filter { it in spans }
            if (rangeIds.isEmpty()) return@forEach
            val back = rangeIds.minOf { spans.getValue(it).first }
            val front = rangeIds.maxOf { spans.getValue(it).second }
            val summary = tree.getNode(summaryId)
            val centerY = bottom + SUMMARY_OFFSET.value + halfHeight(summary).dpVal
            val placed = summary.withCenter(Dp(sign * (back + front) / 2), Dp(centerY))
            tree.setNode(summaryId, placed)
            down.arrangeBelow(placed, tree)
        }
    }

    // How far back towards the head, and on towards the tail, the bone [id] and the shown nodes below it reach, along
    // the spine from the head.
    private fun alongSpan(
        id: String
        , tree: Tree<*>
    ): Pair<Float, Float> {
        var back = Float.MAX_VALUE
        var front = -Float.MAX_VALUE
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
            val along = sign * node.path.centerX.dpVal
            back = minOf(back, along - halfWidth(node).dpVal)
            front = maxOf(front, along + halfWidth(node).dpVal)
        }
        return back to front
    }

    // Moves the node and the shown nodes below it by ([dx], [dy]).
    private fun shift(
        id: String
        , dx: Float
        , dy: Float
        , tree: Tree<*>
    ) {
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
            tree.setNode(node.id, node.withCenter(node.path.centerX + dx, node.path.centerY + dy))
        }
    }
}
