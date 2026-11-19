package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.DpOffset
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FloatingStateTest {
    // The root with "a" and "b" below it, and "c" and "d" below "a", laid out.
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(400f), Dp(300f)).apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "A")
            addNode("b", rootId, "B")
            addNode("c", "a", "C")
            addNode("d", "a", "D")
        }
        return MindMapState(tree).apply {
            setTree(tree)
            mindMapManager.arrangeTree()
        }
    }

    private fun MindMapState.center(id: String) = tree.getNode(id).path.let { it.centerX.dpVal to it.centerY.dpVal }

    @Test
    fun floatingTopicIsAddedBelowEverythingAsOneChange() {
        val state = state()
        val lowest = state.tree.nodes.values.maxOf { it.path.centerY.dpVal }

        state.addFloatingNode("Idea")

        val id = assertNotNull(state.getAddNode()).id
        state.mindMapManager.arrangeTree()
        assertTrue(state.tree.isFloating(id))
        assertEquals(0f, state.tree.floatingOffsetOf(id)!!.x.dpVal)
        assertTrue(state.center(id).second > lowest)
        state.undo()
        assertFalse(id in state.tree.getRootNode().children)
        assertFalse(state.canUndo)
    }

    @Test
    fun floatingTopicIsAddedWhereItIsPutOnTheScreen() = runComposeUiTest {
        val state = state()
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        val root = state.tree.getRootNode()
        val rootOnScreen = with(density) {
            Offset(root.path.centerX.dpVal.dp.toPx(), root.path.centerY.dpVal.dp.toPx())
        } * state.scaleFactor + state.currentPoint
        val target = rootOnScreen + with(density) { Offset(100.dp.toPx(), 150.dp.toPx()) } * state.scaleFactor

        state.addFloatingNode("Here", at = target)

        val offset = assertNotNull(state.tree.floatingOffsetOf(state.getAddNode()!!.id))
        assertEquals(100f, offset.x.dpVal, 0.5f)
        assertEquals(150f, offset.y.dpVal, 0.5f)
    }

    @Test
    fun typingANewFloatingTopicAndCancellingRemovesIt() {
        val state = state()
        state.select(state.tree.getRootNode())

        state.addNodeForTextEdit { addFloatingNode("Idea") }
        val id = assertNotNull(state.textEditNodeId)
        assertTrue(state.tree.isFloating(id))
        state.cancelTextEdit()

        assertFalse(id in state.tree.getRootNode().children)
        assertFalse(state.canUndo)
    }

    @Test
    fun detachedTopicStaysWhereItIsWithItsBranch() {
        val state = state()
        val before = state.center("a")
        state.select(state.tree.getNode("a"))

        state.makeFloating()
        state.mindMapManager.arrangeTree()

        assertTrue(state.tree.isFloating("a"))
        assertEquals(before, state.center("a"))
        assertEquals(listOf("c", "d"), state.tree.getNode("a").children)
        state.undo()
        assertFalse(state.tree.isFloating("a"))
    }

    @Test
    fun topicBelowAnotherIsDetachedToTheRoot() {
        val state = state()
        val before = state.center("c")
        state.select(state.tree.getNode("c"))
        state.toggleSelected("d")

        state.makeFloating()
        state.mindMapManager.arrangeTree()

        assertEquals(state.tree.getRootNode().id, state.tree.getNode("c").parentId)
        assertTrue(state.tree.isFloating("c") && state.tree.isFloating("d"))
        assertEquals(before, state.center("c"))
        state.undo()
        assertEquals(listOf("c", "d"), state.tree.getNode("a").children)
    }

    @Test
    fun theRootIsNotDetached() {
        val state = state()
        state.select(state.tree.getRootNode())

        state.makeFloating()

        assertFalse(state.canUndo)
    }

    @Test
    fun floatingTopicIsAttachedToTheRoot() {
        val state = state()
        state.tree.setFloating("a", DpOffset(Dp(50f), Dp(400f)))
        state.select(state.tree.getNode("a"))

        state.attachToRoot()

        assertFalse(state.tree.isFloating("a"))
        assertEquals(listOf("b", "a"), state.tree.getRootNode().children)
    }

    @Test
    fun floatingTopicsAreNudged() {
        val state = state()
        state.tree.setFloating("a", DpOffset(Dp(50f), Dp(400f)))
        state.select(state.tree.getNode("a"))
        state.toggleSelected("b")

        state.moveFloating(DpOffset(Dp(10f), Dp(-10f)))

        assertEquals(DpOffset(Dp(60f), Dp(390f)), state.tree.floatingOffsetOf("a"))
        assertNull(state.tree.floatingOffsetOf("b"))
        state.undo()
        assertEquals(DpOffset(Dp(50f), Dp(400f)), state.tree.floatingOffsetOf("a"))
    }
}
