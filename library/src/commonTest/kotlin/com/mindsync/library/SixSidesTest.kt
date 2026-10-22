package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SixSidesTest {
    private fun Tree<Any>.rectangle(id: String) = getNode(id) as RectangleNodeData

    private fun degrees(
        dx: Float
        , dy: Float
    ): Float = atan2(dy, dx) * 180f / PI.toFloat()

    @Test
    fun sidesAreMirroredAtTheSameHeight() {
        assertEquals(LayoutSide.UPPER_RIGHT, LayoutSide.UPPER_LEFT.mirrored)
        assertEquals(LayoutSide.LOWER_LEFT, LayoutSide.LOWER_RIGHT.mirrored)
        assertEquals(LayoutSide.LEFT, LayoutSide.RIGHT.mirrored)
        assertEquals(LayoutSide.LEFT, LayoutSide.LOWER_LEFT.horizontal)
        assertEquals(LayoutSide.RIGHT, LayoutSide.UPPER_RIGHT.horizontal)
    }

    @Test
    fun theNextSideGoesRoundTheRootAsItsButtonsDo() {
        val clockwise = generateSequence(LayoutSide.RIGHT) { it.next(isClockwise = true) }.take(7).toList()
        assertEquals(
            listOf(
                LayoutSide.RIGHT, LayoutSide.LOWER_RIGHT, LayoutSide.LOWER_LEFT, LayoutSide.LEFT
                , LayoutSide.UPPER_LEFT, LayoutSide.UPPER_RIGHT, LayoutSide.RIGHT
            )
            , clockwise
        )
        LayoutSide.entries.forEach { assertEquals(it, it.next(isClockwise = true).next(isClockwise = false)) }
    }

    @Test
    fun branchIsMovedRoundTheRootClockwiseOrCounterclockwise() = runComposeUiTest {
        val tree = Tree<Any>(Dp(400f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "a")
        tree.addNode("a1", "a", "a1")
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        waitForIdle()

        // Moving the node below a child of the root moves the whole branch.
        state.goToNode("a1")
        state.moveNodeAround(isClockwise = true)
        waitForIdle()
        assertEquals(LayoutSide.LOWER_RIGHT, state.tree.sideOf("a"))
        state.moveNodeAround(isClockwise = false)
        state.moveNodeAround(isClockwise = false)
        waitForIdle()
        assertEquals(LayoutSide.UPPER_RIGHT, state.tree.sideOf("a1"))
        assertTrue(state.tree.getNode("a").path.centerY.dpVal < state.tree.getRootNode().path.centerY.dpVal)

        // The root is in no branch, so it is not moved.
        state.goToNode(rootId)
        state.moveNodeAround(isClockwise = true)
        assertEquals(LayoutSide.UPPER_RIGHT, state.tree.sideOf("a"))
    }

    @Test
    fun theNearestSideIsTheOneWhoseLineIsClosestToTheDirection() {
        assertEquals(LayoutSide.RIGHT, LayoutSide.nearest(100f, 50f))
        assertEquals(LayoutSide.UPPER_RIGHT, LayoutSide.nearest(100f, -170f))
        assertEquals(LayoutSide.LOWER_RIGHT, LayoutSide.nearest(20f, 100f))
        assertEquals(LayoutSide.LEFT, LayoutSide.nearest(-100f, 0f))
        assertEquals(LayoutSide.UPPER_LEFT, LayoutSide.nearest(-20f, -100f))
        assertEquals(LayoutSide.LOWER_LEFT, LayoutSide.nearest(-100f, 170f))
        // The lines of the sides are 60 degrees apart.
        val angles = LayoutSide.entries.map { (it.angleDegrees + 360f) % 360f }.sorted()
        assertEquals(listOf(0f, 60f, 120f, 180f, 240f, 300f), angles)
    }

    @Test
    fun slantedSidesAreSixtyDegreesOffTheHorizontalAndClearOfTheStraightOnes() {
        val tree = Tree<Any>(Dp(500f), Dp(500f))
        val rootId = tree.getRootNode().id
        tree.addNode("r1", rootId, "r1")
        tree.addNode("r2", rootId, "r2")
        tree.addNode("r3", rootId, "r3")
        tree.addNode("r4", rootId, "r4")
        tree.addNode("u1", rootId, "u1", side = LayoutSide.UPPER_RIGHT)
        tree.addNode("l1", rootId, "l1", side = LayoutSide.LOWER_LEFT)
        val manager = MindMapManager()
        manager.update(tree)

        manager.arrangeTree()

        val root = tree.getRootNode().path
        assertEquals(500f, root.centerX.dpVal, 0.01f)
        assertEquals(500f, root.centerY.dpVal, 0.01f)
        // The straight side is as it was: centered on the root, a spacing out of it.
        val r1 = tree.rectangle("r1").path
        val r4 = tree.rectangle("r4").path
        assertEquals((root.centerX + root.radiusX).dpVal + 50f, r1.leftX().dpVal, 0.01f)
        assertEquals(500f, (r1.centerY.dpVal + r4.centerY.dpVal) / 2, 0.01f)

        // Above the right side, a spacing clear of it, with its near corner on the line 60 degrees up.
        val u1 = tree.rectangle("u1").path
        val u1Bottom = u1.centerY + u1.height / 2
        assertEquals(r1.centerY.dpVal - 25f - 50f, u1Bottom.dpVal, 0.01f)
        assertEquals(60f, degrees((u1.leftX() - root.centerX).dpVal, (root.centerY - u1Bottom).dpVal), 0.01f)

        // Nothing is straight out to the left, so the lower left side is as near as its line allows.
        val l1 = tree.rectangle("l1").path
        val l1Top = l1.centerY - l1.height / 2
        assertEquals((root.centerX - root.radiusX).dpVal - 50f, l1.rightX().dpVal, 0.01f)
        assertEquals(60f, degrees((root.centerX - l1.rightX()).dpVal, (l1Top - root.centerY).dpVal), 0.01f)

        // The height of the tree is twice the farthest it reaches above or below the root: up to the top of u1.
        val above = root.centerY.dpVal - (u1.centerY - u1.height / 2).dpVal
        assertEquals(above * 2, manager.measureHeight(tree.getRootNode()).dpVal, 0.01f)
    }

    @Test
    fun rootIsLoweredUntilTheSidesAboveItFit() {
        val tree = Tree<Any>(Dp(500f), Dp(100f))
        val rootId = tree.getRootNode().id
        tree.addNode("u1", rootId, "u1", side = LayoutSide.UPPER_LEFT)
        val manager = MindMapManager()
        manager.update(tree)

        manager.arrangeTree()

        val u1 = tree.rectangle("u1").path
        assertEquals(50f, (u1.centerY - u1.height / 2).dpVal, 0.01f)
    }

    @Test
    fun rootButtonsAreSpacedEvenlyAlongItsEdge() {
        val tree = Tree<Any>(Dp(200f), Dp(100f))
        val root = tree.getRootNode()
        tree.setRootNode(root.copy(path = root.path.copy(radiusX = Dp(90f), radiusY = Dp(30f))))
        val oval = tree.getRootNode()
        val aroundTheEdge = listOf(
            LayoutSide.RIGHT, LayoutSide.UPPER_RIGHT, LayoutSide.UPPER_LEFT
            , LayoutSide.LEFT, LayoutSide.LOWER_LEFT, LayoutSide.LOWER_RIGHT
        )

        val points = with(Density(1f)) { aroundTheEdge.map { rootEdgePoint(oval, it, Dp(0f)) } }

        assertEquals(Offset(290f, 100f), points[0])
        assertEquals(110f, points[3].x, 0.01f)
        assertEquals(100f, points[3].y, 0.01f)
        // Each point is on the edge, and neighbors are as far apart as each other.
        points.forEach { assertEquals(1f, ((it.x - 200f) / 90f).pow(2) + ((it.y - 100f) / 30f).pow(2), 0.001f) }
        val gaps = (points + points.first()).zipWithNext { from, to -> (to - from).getDistance() }
        gaps.forEach { assertEquals(gaps.average().toFloat(), it, gaps.average().toFloat() * 0.05f) }
        assertTrue(points[1].y < 100f && points[1].x > 200f)
        assertTrue(points[4].y > 100f && points[4].x < 200f)
    }

    @Test
    fun rootIsLargerThanAnOvalWithTheSameText() = runComposeUiTest {
        val tree = Tree<Any>(Dp(400f), Dp(300f))
        val root = tree.getRootNode()
        tree.addNode("same", root.id, root.description, NodeShape.OVAL)
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        waitForIdle()

        val rootPath = state.tree.getRootNode().path
        val same = state.tree.getNode("same") as OvalNodeData
        assertEquals(same.path.radiusX.dpVal * 1.21f, rootPath.radiusX.dpVal, 0.01f)
        assertEquals(same.path.radiusY.dpVal * 1.21f, rootPath.radiusY.dpVal, 0.01f)
    }

    @Test
    fun rootHasAnAddButtonForEachOfItsSixSides() = runComposeUiTest {
        val sides = mutableListOf<LayoutSide>()
        val state = MindMapState()
        setContent {
            MindMap(
                state = state
                , modifier = Modifier.size(800.dp, 600.dp).testTag(TAG)
                , onAddChildClick = { _, side -> sides.add(side) }
            )
        }
        waitForIdle()

        LayoutSide.entries.forEach { side ->
            onNodeWithTag(TAG).performTouchInput { click(addButtonCenter(state.tree.getRootNode(), side)) }
            waitForIdle()
        }
        assertEquals(LayoutSide.entries.toList(), sides.toList())

        state.addNode("upper left", side = LayoutSide.UPPER_LEFT)
        waitForIdle()
        val root = state.tree.getRootNode()
        val id = root.children.single()
        val node = state.tree.getNode(id) as RectangleNodeData
        assertEquals(LayoutSide.UPPER_LEFT, state.tree.sideOf(id))
        assertTrue(node.path.rightX().dpVal < root.path.centerX.dpVal)
        assertTrue(node.path.centerY.dpVal < root.path.centerY.dpVal)

        // Moved to the other side, it stays above the root; FreeMind only keeps that it is on the right.
        state.goToNode(id)
        state.moveNodeToOtherSide()
        waitForIdle()
        assertEquals(LayoutSide.UPPER_RIGHT, state.tree.sideOf(id))
        assertTrue("POSITION=\"right\"" in FreeMind.write(state.tree))
    }

    private companion object {
        const val TAG = "six sides"
    }
}
