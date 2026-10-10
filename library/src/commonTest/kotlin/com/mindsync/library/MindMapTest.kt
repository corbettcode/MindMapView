package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MindMapTest {
    private val clicks = mutableListOf<NodeData<*>?>()
    private val moves = mutableListOf<Pair<String, String>>()

    private fun ComposeUiTest.setMindMap(state: MindMapState) {
        setContent {
            MindMap(
                state = state,
                modifier = Modifier.size(800.dp, 600.dp).testTag(TAG),
                onNodeClick = { clicks.add(it) },
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
