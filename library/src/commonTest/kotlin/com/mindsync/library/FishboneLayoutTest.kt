package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.layout.FISHBONE_SLANT
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FishboneLayoutTest {
    private val causes = listOf("people", "methods", "machines", "materials")

    // The effect, with four causes on it, each with two ribs, and a detail below the first rib of the first cause.
    private fun tree(structure: MapStructure = MapStructure.FISHBONE_LEFT): Tree<Any> =
        Tree<Any>(Dp(400f), Dp(400f)).apply {
            val rootId = getRootNode().id
            causes.forEach { cause ->
                addNode(cause, rootId, cause.replaceFirstChar { it.uppercase() })
                addNode("$cause-1", cause, "First $cause rib")
                addNode("$cause-2", cause, "Second rib")
            }
            addNode("detail", "people-1", "A detail")
            this.structure = structure
        }

    private fun laidOut(tree: Tree<Any>): Tree<Any> = tree.apply {
        MindMapManager().apply {
            setTree(tree)
            arrangeTree()
        }
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal

    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal

    private fun Tree<*>.left(id: String) = x(id) - halfWidth(getNode(id)).dpVal

    private fun Tree<*>.right(id: String) = x(id) + halfWidth(getNode(id)).dpVal

    private fun Tree<*>.top(id: String) = y(id) - halfHeight(getNode(id)).dpVal

    private fun Tree<*>.bottom(id: String) = y(id) + halfHeight(getNode(id)).dpVal

    // The left and right of the node and the nodes below it.
    private fun Tree<*>.span(id: String): Pair<Float, Float> {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        doVisiblePreorderTraversal(getNode(id)) { node ->
            left = minOf(left, node.path.centerX.dpVal - halfWidth(node).dpVal)
            right = maxOf(right, node.path.centerX.dpVal + halfWidth(node).dpVal)
        }
        return left to right
    }

    @Test
    fun causesAreAboveAndBelowTheSpineByTurnsInOrderAwayFromTheHead() {
        val tree = laidOut(tree())
        val spineY = tree.y(tree.getRootNode().id)

        assertTrue(tree.bottom("people") < spineY && tree.top("methods") > spineY)
        assertTrue(tree.bottom("machines") < spineY && tree.top("materials") > spineY)
        causes.zipWithNext().forEach { (one, next) -> assertTrue(tree.x(one) < tree.x(next), "$one before $next") }
        assertEquals(GrowDirection.DOWN, tree.directionOf("people"))
        assertEquals(GrowDirection.UP, tree.directionOf("methods"))
        assertEquals(GrowDirection.RIGHT, tree.directionOf("people-1"))
    }

    @Test
    fun eachCauseIsClearOfTheOneBeforeItOnItsSideAndOfTheHead() {
        val tree = laidOut(tree())

        assertTrue(tree.span("people").second < tree.span("machines").first)
        assertTrue(tree.span("methods").second < tree.span("materials").first)
        val headRight = tree.right(tree.getRootNode().id)
        causes.forEach { assertTrue(tree.span(it).first > headRight, "$it is clear of the head") }
    }

    @Test
    fun ribsAreAlongTheirBoneTheFirstNearestTheCause() {
        val tree = laidOut(tree())
        val spineY = tree.y(tree.getRootNode().id)

        // Above the spine, the first rib is higher, nearer the cause, and both are below the cause.
        assertTrue(tree.y("people-1") < tree.y("people-2"))
        assertTrue(tree.bottom("people") < tree.top("people-1") && tree.bottom("people-2") < spineY)
        // Below it, the first rib is lower, nearer the cause.
        assertTrue(tree.y("methods-1") > tree.y("methods-2") && tree.top("methods") > tree.bottom("methods-1"))
        // Each rib is to the tail's side of its bone where it meets it, the bone running from its joint at 60°.
        val joint = tree.x("people") - (spineY - tree.bottom("people")) * FISHBONE_SLANT
        listOf("people-1", "people-2").forEach { rib ->
            val boneX = joint + (spineY - tree.y(rib)) * FISHBONE_SLANT
            assertTrue(tree.left(rib) > boneX, "$rib is past its bone")
        }
        // The topics below a rib grow on towards the tail.
        assertTrue(tree.left("detail") > tree.right("people-1"))
        assertEquals(tree.y("people-1"), tree.y("detail"), 0.5f)
    }

    @Test
    fun headRightIsHeadLeftMirrored() {
        val left = laidOut(tree(MapStructure.FISHBONE_LEFT))
        val right = laidOut(tree(MapStructure.FISHBONE_RIGHT))
        val rootId = left.getRootNode().id

        left.nodes.keys.forEach { id ->
            assertEquals(left.x(id) - left.x(rootId), right.x(rootId) - right.x(id), 0.5f, id)
            assertEquals(left.y(id) - left.y(rootId), right.y(id) - right.y(rootId), 0.5f, id)
        }
    }

    @Test
    fun aFoldedCauseHasAShortBone() {
        val tree = tree().apply { setFolded("people", true) }
        laidOut(tree)
        val spineY = tree.y(tree.getRootNode().id)

        assertEquals(48f, spineY - tree.bottom("people"), 0.5f)
        assertTrue(tree.span("people").second < tree.span("machines").first)
    }

    @Test
    fun fishboneBranchIsClearOfItsSiblings() {
        val tree = Tree<Any>(Dp(400f), Dp(500f)).apply {
            val rootId = getRootNode().id
            addNode("above", rootId, "Above")
            addNode("effect", rootId, "Effect")
            addNode("below", rootId, "Below")
            causes.forEach { cause ->
                addNode(cause, "effect", cause)
                addNode("$cause-1", cause, "A rib")
            }
            setBranchStructure("effect", MapStructure.FISHBONE_LEFT)
        }
        laidOut(tree)

        val rootRight = tree.right(tree.getRootNode().id)
        (listOf("effect") + causes + causes.map { "$it-1" }).forEach { id ->
            assertTrue(tree.left(id) > rootRight, "$id is right of the root")
            assertTrue(tree.top(id) > tree.bottom("above") && tree.bottom(id) < tree.top("below"), "$id is between")
        }
        assertTrue(tree.left("people") > tree.right("effect"))
    }
}
