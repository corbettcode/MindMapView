package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.StadiumPath
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MindMapTest {
    private val clicks = mutableListOf<NodeData<*>?>()
    private val doubleClicks = mutableListOf<String>()
    private val moves = mutableListOf<Pair<String, String>>()

    private fun ComposeUiTest.setMindMap(state: MindMapState) {
        setContent {
            MindMap(
                state = state,
                modifier = Modifier.size(800.dp, 600.dp).testTag(TAG),
                onNodeClick = { clicks.add(it) },
                onNodeDoubleClick = { doubleClicks.add(it.id) },
                onNodeMove = { _, target, parent -> moves.add(target.id to parent.id) },
            )
        }
    }

    private fun ComposeUiTest.clickNode(node: NodeData<*>) {
        onNodeWithTag(TAG).performTouchInput { click(center(node)) }
        waitForIdle()
    }

    private fun androidx.compose.ui.unit.Density.center(node: NodeData<*>): Offset {
        return Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx())
    }

    @Test
    fun rootIsCenteredWhenNoPositionIsGiven() = runComposeUiTest {
        val state = MindMapState()
        setMindMap(state)
        waitForIdle()

        val root = state.tree.getRootNode()
        assertEquals(Dp(400f), root.path.centerX)
        assertEquals(Dp(300f), root.path.centerY)
    }

    @Test
    fun clickSelectsNodeAndEmptySpaceClearsSelection() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        assertIs<CircleNodeData>(state.selectedNode)
        assertIs<CircleNodeData>(clicks.last())

        onNodeWithTag(TAG).performTouchInput { click(Offset(700.dp.toPx(), 50.dp.toPx())) }
        waitForIdle()
        assertNull(state.selectedNode)
        assertNull(clicks.last())
    }

    @Test
    fun addEditAndRemoveNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child")
        waitForIdle()

        val root = state.tree.getRootNode()
        val child = assertIs<RectangleNodeData>(state.tree.getNode(root.children.single()))
        assertEquals("child", child.description)
        assertTrue(child.path.leftX().dpVal > (root.path.centerX + root.path.radius).dpVal)
        assertEquals(root.path.centerY.dpVal, child.path.centerY.dpVal, 0.01f)

        clickNode(child)
        assertEquals(child.id, state.selectedNode?.id)
        state.editNodeText("a much longer description")
        waitForIdle()
        val edited = assertIs<RectangleNodeData>(state.tree.getNode(child.id))
        assertEquals("a much longer description", edited.description)
        assertTrue(edited.path.width.dpVal > child.path.width.dpVal)

        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getRootNode().children.isEmpty())
        assertNull(state.selectedNode)
    }

    @Test
    fun addEditAndRemoveStadiumNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("child", NodeShape.STADIUM)
        waitForIdle()

        val root = state.tree.getRootNode()
        val child = assertIs<StadiumNodeData>(state.tree.getNode(root.children.single()))
        assertEquals("child", child.description)
        assertTrue(child.path.leftX().dpVal > (root.path.centerX + root.path.radius).dpVal)
        assertEquals(root.path.centerY.dpVal, child.path.centerY.dpVal, 0.01f)
        assertEquals(child.path.length.dpVal + child.path.radius.dpVal * 2, child.path.width().dpVal, 0.01f)
        assertEquals(child.path.radius.dpVal * 2, child.path.height().dpVal, 0.01f)

        clickNode(child)
        assertEquals(child.id, state.selectedNode?.id)
        state.editNodeText("a much longer description")
        waitForIdle()
        val edited = assertIs<StadiumNodeData>(state.tree.getNode(child.id))
        assertEquals("a much longer description", edited.description)
        assertTrue(edited.path.length.dpVal > child.path.length.dpVal)
        assertEquals(child.path.radius.dpVal, edited.path.radius.dpVal, 0.01f)

        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getRootNode().children.isEmpty())
        assertNull(state.selectedNode)
    }

    @Test
    fun addDragAndRemoveCircleNode() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.CIRCLE)
        waitForIdle()

        val root = state.tree.getRootNode()
        val (firstId, secondId) = root.children
        val first = state.tree.getNode(firstId)
        val second = assertIs<CircleNodeData>(state.tree.getNode(secondId))
        assertEquals(root.id, second.parentId)
        assertTrue((second.path.centerX - second.path.radius).dpVal > (root.path.centerX + root.path.radius).dpVal)

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()
        assertEquals(listOf(secondId to firstId), moves)
        assertEquals(firstId, state.tree.getNode(secondId).parentId)

        clickNode(state.tree.getNode(secondId))
        assertEquals(secondId, state.selectedNode?.id)
        state.removeNode()
        waitForIdle()
        assertTrue(state.tree.getNode(firstId).children.isEmpty())

        clickNode(state.tree.getRootNode())
        state.removeNode()
        waitForIdle()
        assertEquals(root.id, state.tree.getRootNode().id)
    }

    @Test
    fun nodeColorDefaultsToItsShapeAndCanBeChanged() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        val root = state.tree.getRootNode()
        assertEquals(NodeShape.CIRCLE.defaultColor, root.color)

        clickNode(root)
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.STADIUM)
        waitForIdle()
        state.addNode("third", NodeShape.RECTANGLE, Color.Red)
        waitForIdle()

        val (firstId, secondId, thirdId) = state.tree.getRootNode().children
        assertEquals(NodeShape.RECTANGLE.defaultColor, state.tree.getNode(firstId).color)
        assertEquals(NodeShape.STADIUM.defaultColor, state.tree.getNode(secondId).color)
        assertEquals(Color.Red, state.tree.getNode(thirdId).color)

        clickNode(state.tree.getNode(firstId))
        state.editNodeColor(Color.Blue)
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).color)
        assertEquals(Color.Blue, state.selectedNode?.color)
        state.editNodeText("renamed")
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).color)

        // Moving a node deeper into the tree keeps its color.
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)
        onNodeWithTag(TAG).performTouchInput { swipe(center(first), center(second), durationMillis = 200) }
        waitForIdle()
        assertEquals(secondId, state.tree.getNode(firstId).parentId)
        assertEquals(Color.Blue, state.tree.getNode(firstId).color)
        assertEquals(NodeShape.STADIUM.defaultColor, state.tree.getNode(secondId).color)
    }

    @Test
    fun nodeTextColorDefaultsToItsShapeAndCanBeChanged() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        val root = state.tree.getRootNode()
        assertEquals(NodeShape.CIRCLE.defaultTextColor, root.textColor)

        clickNode(root)
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.STADIUM, textColor = Color.Red)
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        assertEquals(NodeShape.RECTANGLE.defaultTextColor, state.tree.getNode(firstId).textColor)
        assertEquals(Color.Red, state.tree.getNode(secondId).textColor)
        assertEquals(NodeShape.STADIUM.defaultColor, state.tree.getNode(secondId).color)

        clickNode(state.tree.getNode(firstId))
        state.editNodeTextColor(Color.Blue)
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).textColor)
        assertEquals(Color.Blue, state.selectedNode?.textColor)
        assertEquals(NodeShape.RECTANGLE.defaultColor, state.tree.getNode(firstId).color)
        state.editNodeText("renamed")
        waitForIdle()
        assertEquals(Color.Blue, state.tree.getNode(firstId).textColor)
    }

    @Test
    fun doubleClickOnNodeIsReportedOnce() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        val root = state.tree.getRootNode()
        clickNode(root)
        state.addNode("child")
        waitForIdle()
        val child = state.tree.getNode(state.tree.getRootNode().children.single())

        // Single clicks, clicks on different nodes and slow repeat clicks are not double clicks.
        mainClock.advanceTimeBy(1_000)
        clickNode(root)
        clickNode(child)
        mainClock.advanceTimeBy(1_000)
        clickNode(child)
        assertTrue(doubleClicks.isEmpty())

        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG).performTouchInput { doubleClick(center(child)) }
        waitForIdle()
        assertEquals(listOf(child.id), doubleClicks)
        assertEquals(child.id, state.selectedNode?.id)

        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(TAG).performTouchInput { doubleClick(Offset(700.dp.toPx(), 50.dp.toPx())) }
        waitForIdle()
        assertEquals(listOf(child.id), doubleClicks)
    }

    @Test
    fun stadiumHitTestFollowsTheRoundedCaps() {
        val path = StadiumPath(Dp(100f), Dp(100f), length = Dp(60f), radius = Dp(20f))

        assertTrue(path.contains(Dp(100f), Dp(100f)))
        assertTrue(path.contains(Dp(149f), Dp(100f)))
        assertTrue(path.contains(Dp(130f), Dp(119f)))
        assertTrue(path.contains(Dp(51f), Dp(100f)))
        // Inside the bounding box, but outside the rounded cap.
        assertFalse(path.contains(Dp(148f), Dp(118f)))
        assertFalse(path.contains(Dp(52f), Dp(82f)))
        assertFalse(path.contains(Dp(151f), Dp(100f)))
        assertFalse(path.contains(Dp(100f), Dp(121f)))
    }

    @Test
    fun draggingStadiumOntoAnotherNodeReattachesIt() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second", NodeShape.STADIUM)
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()

        assertEquals(listOf(secondId to firstId), moves)
        assertEquals(listOf(secondId), state.tree.getNode(firstId).children)
        assertIs<StadiumNodeData>(state.tree.getNode(secondId))
        assertEquals(firstId, state.tree.getNode(secondId).parentId)
    }

    @Test
    fun draggingNodeOntoAnotherReattachesIt() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        clickNode(state.tree.getRootNode())
        state.addNode("first")
        waitForIdle()
        state.addNode("second")
        waitForIdle()

        val (firstId, secondId) = state.tree.getRootNode().children
        val first = state.tree.getNode(firstId)
        val second = state.tree.getNode(secondId)
        assertTrue(second.path.centerY.dpVal > first.path.centerY.dpVal)

        onNodeWithTag(TAG).performTouchInput { swipe(center(second), center(first), durationMillis = 200) }
        waitForIdle()

        assertEquals(listOf(secondId to firstId), moves)
        assertEquals(listOf(firstId), state.tree.getRootNode().children)
        assertEquals(listOf(secondId), state.tree.getNode(firstId).children)
        assertEquals(firstId, state.tree.getNode(secondId).parentId)
        assertTrue(state.tree.getNode(secondId).path.centerX.dpVal > state.tree.getNode(firstId).path.centerX.dpVal)
    }

    @Test
    fun dragOnEmptySpacePansAndFitScreenResets() = runComposeUiTest {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))
        setMindMap(state)

        onNodeWithTag(TAG).performTouchInput {
            swipe(Offset(700.dp.toPx(), 500.dp.toPx()), Offset(600.dp.toPx(), 450.dp.toPx()), durationMillis = 200)
        }
        waitForIdle()
        assertEquals(with(density) { Offset(-100.dp.toPx(), -50.dp.toPx()) }, state.currentPoint)

        state.fitScreen()
        waitForIdle()
        assertEquals(Offset.Zero, state.currentPoint)
        assertEquals(1f, state.scaleFactor)
    }

    private companion object {
        const val TAG = "mindMap"
    }
}
